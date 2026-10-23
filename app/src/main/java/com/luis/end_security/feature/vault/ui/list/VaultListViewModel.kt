package com.luis.end_security.feature.vault.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luis.end_security.core.designsystem.component.initialOf
import com.luis.end_security.core.session.VaultSession
import com.luis.end_security.core.session.VaultState
import com.luis.end_security.feature.auth.domain.AuthRepository
import com.luis.end_security.core.sync.SyncStatus
import com.luis.end_security.feature.vault.domain.DeletionUndo
import com.luis.end_security.feature.vault.domain.ItemSearch
import com.luis.end_security.feature.vault.domain.VaultSummary
import com.luis.end_security.feature.vault.domain.VaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/** Una fila de la lista (SCR-10). Ninguna imprime su contenido. */
sealed interface VaultRow {
    val id: UUID

    class Item(override val id: UUID, val name: String, val username: String, val initial: String) : VaultRow {
        override fun toString(): String = "VaultRow.Item(***)"
    }

    /** No se pudo descifrar: «Elemento dañado». Abre un detalle mínimo con «Eliminar». */
    class Damaged(override val id: UUID) : VaultRow {
        override fun toString(): String = "VaultRow.Damaged(***)"
    }

    /** De una versión más nueva de la app: «Actualiza la app para ver esta contraseña». Con «Eliminar». */
    class Newer(override val id: UUID) : VaultRow {
        override fun toString(): String = "VaultRow.Newer(***)"
    }
}

/** El aviso «{nombre} se eliminó · Deshacer»; [name] es `null` si el elemento estaba dañado o era de otra versión. */
class DeletedNotice(val name: String?) {
    override fun toString(): String = "DeletedNotice(***)"
}

/** Estado de Bóveda · Lista y vacía (SCR-10, SCR-11) y sus avisos (SCR-18, SCR-44). */
class VaultListUiState(
    /** Inicial del avatar de la cuenta. */
    val accountInitial: String = "?",
    val isLoading: Boolean = true,
    /** Ya filtradas por la búsqueda y ordenadas A–Z. */
    val rows: List<VaultRow> = emptyList(),
    /** Todos los elementos, sin filtrar y sin el que espera su «Deshacer». */
    val totalCount: Int = 0,
    val query: String = "",
    /** «Te quedan N códigos de respaldo» tras entrar con uno, si quedan 3 o menos. */
    val recoveryCodesLeft: Int? = null,
    val deleted: DeletedNotice? = null,
    /** Contraseñas que cambiaron en dos dispositivos y esperan que se elija una versión (SCR-18). */
    val conflictCount: Int = 0,
    /** La última sincronización falló por falta de red (SCR-44). */
    val offline: Boolean = false,
) {
    /** SCR-11: la bóveda no tiene ningún elemento. */
    val isEmpty: Boolean get() = !isLoading && totalCount == 0

    /** Hay elementos pero la búsqueda no encontró ninguno. */
    val noResults: Boolean get() = !isLoading && totalCount > 0 && rows.isEmpty()

    override fun toString(): String = "VaultListUiState(count=$totalCount, loading=$isLoading, ***)"
}

sealed interface VaultListEffect {
    data class OpenItem(val id: UUID) : VaultListEffect

    data object OpenNew : VaultListEffect

    /** Un solo conflicto: directo a «Elegir versión». */
    data class OpenConflict(val id: UUID) : VaultListEffect

    /** Varios: a «Por resolver». */
    data object OpenConflicts : VaultListEffect
}

/**
 * La lista de la bóveda (OP-04, OP-07). Los elementos se descifran en memoria (en el repositorio, fuera
 * del hilo principal) y **solo con nombre, usuario y sitio**: la lista no ve contraseñas ni notas. La
 * búsqueda es local: el texto buscado vive aquí, nunca en `SavedStateHandle` ni en disco, y se borra al
 * bloquear. Con la bóveda bloqueada el repositorio emite una lista vacía, así que el estado se vacía solo
 * (hallazgo 3 de la tarea 09).
 */
@HiltViewModel
class VaultListViewModel @Inject constructor(
    repository: VaultRepository,
    private val session: VaultSession,
    private val deletion: DeletionUndo,
    private val auth: AuthRepository,
    syncStatus: SyncStatus,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val recoveryCodesLeft = MutableStateFlow(auth.consumeRecoveryCodesNotice())
    private val accountInitial = MutableStateFlow("?")
    private var conflictIds: List<UUID> = emptyList()

    private val _effects = Channel<VaultListEffect>(Channel.BUFFERED)
    val effects: Flow<VaultListEffect> = _effects.receiveAsFlow()

    private class Extras(val codesLeft: Int?, val initial: String, val conflicts: List<UUID>, val offline: Boolean)

    private val extras = combine(
        recoveryCodesLeft, accountInitial, repository.observeConflictIds(), syncStatus.offline,
    ) { codes, initial, conflicts, offline -> Extras(codes, initial, conflicts, offline) }

    val state: StateFlow<VaultListUiState> = combine(
        repository.observeSummaries(),
        query,
        deletion.pending,
        extras,
    ) { entries, query, pending, extras ->
        conflictIds = extras.conflicts
        val visible = entries.filter { it.id != pending?.id }
        VaultListUiState(
            accountInitial = extras.initial,
            isLoading = false,
            rows = ItemSearch.sorted(visible).filteredBy(query).map { it.toRow() },
            totalCount = visible.size,
            query = query,
            recoveryCodesLeft = extras.codesLeft,
            deleted = pending?.let { DeletedNotice(it.name) },
            conflictCount = extras.conflicts.size,
            offline = extras.offline,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), VaultListUiState())

    init {
        viewModelScope.launch {
            val name = auth.localAccountName()
            accountInitial.value = name?.let { initialOf(it) } ?: "?"
        }
        viewModelScope.launch {
            session.state.collect { state ->
                if (state is VaultState.Locked) {
                    query.value = ""
                    recoveryCodesLeft.value = null
                    accountInitial.value = "?"
                }
            }
        }
    }

    fun onQueryChange(text: String) {
        query.value = text
    }

    /** «Bloquear ahora» (DEC-52). */
    fun onLockNow() = session.lock()

    fun onUndoDelete() = deletion.undo()

    fun onItemClick(row: VaultRow) {
        // Todas las filas abren un detalle: el normal, o uno mínimo con «Eliminar» si está dañado o es de otra versión.
        viewModelScope.launch { _effects.send(VaultListEffect.OpenItem(row.id)) }
    }

    fun onAddClick() {
        viewModelScope.launch { _effects.send(VaultListEffect.OpenNew) }
    }

    /** «Resolver»: con un solo conflicto, directo a «Elegir versión»; con varios, a «Por resolver». */
    fun onResolveClick() {
        val ids = conflictIds
        viewModelScope.launch {
            when (ids.size) {
                0 -> Unit
                1 -> _effects.send(VaultListEffect.OpenConflict(ids.single()))
                else -> _effects.send(VaultListEffect.OpenConflicts)
            }
        }
    }

    private fun List<VaultSummary>.filteredBy(query: String): List<VaultSummary> =
        if (query.isBlank()) this else filter { it is VaultSummary.Readable && ItemSearch.matches(it, query) }

    private fun VaultSummary.toRow(): VaultRow = when (this) {
        is VaultSummary.Readable -> VaultRow.Item(id = id, name = name, username = username, initial = initialOf(name))
        is VaultSummary.Damaged -> VaultRow.Damaged(id)
        is VaultSummary.Unsupported -> VaultRow.Newer(id)
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
