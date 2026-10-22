package com.luis.end_security.feature.vault.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luis.end_security.core.session.VaultSession
import com.luis.end_security.core.session.VaultState
import com.luis.end_security.feature.auth.domain.AuthRepository
import com.luis.end_security.feature.vault.domain.DamagedEntry
import com.luis.end_security.feature.vault.domain.DeletionUndo
import com.luis.end_security.feature.vault.domain.ItemSearch
import com.luis.end_security.feature.vault.domain.ReadableEntry
import com.luis.end_security.feature.vault.domain.UnsupportedEntry
import com.luis.end_security.feature.vault.domain.VaultEntry
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

    /** No se pudo descifrar: «Elemento dañado». */
    class Damaged(override val id: UUID) : VaultRow {
        override fun toString(): String = "VaultRow.Damaged(***)"
    }

    /** De una versión más nueva de la app: solo lectura. */
    class Newer(override val id: UUID) : VaultRow {
        override fun toString(): String = "VaultRow.Newer(***)"
    }
}

/** Estado de Bóveda · Lista y vacía (SCR-10, SCR-11). */
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
    /** Nombre del elemento que se acaba de eliminar y aún se puede deshacer. */
    val deletedName: String? = null,
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
}

/**
 * La lista de la bóveda (OP-04, OP-07). Los elementos se descifran en memoria (en el repositorio, fuera
 * del hilo principal) y la búsqueda es local: el texto buscado vive aquí, nunca en `SavedStateHandle`
 * ni en disco, y se borra al bloquear. Con la bóveda bloqueada el repositorio emite una lista vacía,
 * así que el estado se vacía solo (hallazgo 3 de la tarea 09).
 */
@HiltViewModel
class VaultListViewModel @Inject constructor(
    repository: VaultRepository,
    private val session: VaultSession,
    private val deletion: DeletionUndo,
    private val auth: AuthRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val recoveryCodesLeft = MutableStateFlow(auth.consumeRecoveryCodesNotice())
    private val accountInitial = MutableStateFlow("?")

    private val _effects = Channel<VaultListEffect>(Channel.BUFFERED)
    val effects: Flow<VaultListEffect> = _effects.receiveAsFlow()

    val state: StateFlow<VaultListUiState> = combine(
        repository.observeEntries(),
        query,
        deletion.pending,
        recoveryCodesLeft,
        accountInitial,
    ) { entries, query, pending, codesLeft, initial ->
        val visible = entries.filter { it.id != pending?.id }
        VaultListUiState(
            accountInitial = initial,
            isLoading = false,
            rows = ItemSearch.sorted(visible).filteredBy(query).map { it.toRow() },
            totalCount = visible.size,
            query = query,
            recoveryCodesLeft = codesLeft,
            deletedName = pending?.name,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), VaultListUiState())

    init {
        viewModelScope.launch {
            val name = auth.localAccountName()
            accountInitial.value = name?.let { com.luis.end_security.core.designsystem.component.initialOf(it) } ?: "?"
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
        // Un elemento dañado o de otra versión no tiene detalle: no se puede abrir ni editar.
        if (row is VaultRow.Item) viewModelScope.launch { _effects.send(VaultListEffect.OpenItem(row.id)) }
    }

    fun onAddClick() {
        viewModelScope.launch { _effects.send(VaultListEffect.OpenNew) }
    }

    private fun List<VaultEntry>.filteredBy(query: String): List<VaultEntry> =
        if (query.isBlank()) this else filter { it is ReadableEntry && ItemSearch.matches(it.item, query) }

    private fun VaultEntry.toRow(): VaultRow = when (this) {
        is ReadableEntry -> VaultRow.Item(
            id = id,
            name = item.name,
            username = item.username,
            initial = com.luis.end_security.core.designsystem.component.initialOf(item.name),
        )
        is DamagedEntry -> VaultRow.Damaged(id)
        is UnsupportedEntry -> VaultRow.Newer(id)
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
