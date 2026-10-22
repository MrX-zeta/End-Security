package com.luis.end_security.feature.vault.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luis.end_security.core.common.DeviceClock
import com.luis.end_security.core.designsystem.component.initialOf
import com.luis.end_security.core.security.SecretClipboard
import com.luis.end_security.core.session.VaultSession
import com.luis.end_security.core.session.VaultState
import com.luis.end_security.feature.vault.domain.DeletionUndo
import com.luis.end_security.feature.vault.domain.EditedAge
import com.luis.end_security.feature.vault.domain.PendingDeletion
import com.luis.end_security.feature.vault.domain.ReadableEntry
import com.luis.end_security.feature.vault.domain.SiteUrl
import com.luis.end_security.feature.vault.domain.VaultItem
import com.luis.end_security.feature.vault.domain.VaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/** Qué se copió, para el aviso «Copiada · se borra del portapapeles en N s» (SCR-13). */
enum class CopiedKind { Password, Username }

class CopiedNotice(val kind: CopiedKind, val seconds: Int)

enum class DetailStatus { Loading, Found, Gone }

/**
 * Estado del detalle (SCR-12). La contraseña solo está aquí, como `String`, mientras se ve
 * ([shownPassword]); oculta, la pantalla recibe solo su longitud (DEC-27, regla no negociable 6).
 */
class ItemDetailUiState(
    val status: DetailStatus = DetailStatus.Loading,
    val name: String = "",
    val initial: String = "?",
    val edited: EditedAge = EditedAge.Now,
    val username: String = "",
    /** El sitio tal como se escribió; `null` si no hay. */
    val site: String? = null,
    /** Se puede abrir en el navegador (solo http/https). */
    val siteOpenable: Boolean = false,
    val notes: String? = null,
    val passwordLength: Int = 0,
    val shownPassword: String? = null,
    val copied: CopiedNotice? = null,
    val showDeleteSheet: Boolean = false,
) {
    val passwordVisible: Boolean get() = shownPassword != null

    override fun toString(): String = "ItemDetailUiState(status=$status, ***)"
}

sealed interface ItemDetailEffect {
    /** El elemento ya no existe o se eliminó: volver a la lista. */
    data object Close : ItemDetailEffect

    /** No es `data class`: su `toString()` no debe imprimir el sitio. */
    class OpenSite(val url: String) : ItemDetailEffect {
        override fun equals(other: Any?): Boolean = other is OpenSite && url == other.url

        override fun hashCode(): Int = url.hashCode()

        override fun toString(): String = "OpenSite(***)"
    }
}

/**
 * Detalle de un elemento (OP-05, OP-06, OP-03). Sigue al elemento en Room, así que un cambio hecho en
 * Editar se ve al volver. Con la bóveda bloqueada se vacía solo (el repositorio deja de entregarlo).
 */
@HiltViewModel
class ItemDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: VaultRepository,
    private val clipboard: SecretClipboard,
    private val deletion: DeletionUndo,
    private val session: VaultSession,
    private val clock: DeviceClock,
) : ViewModel() {

    private val itemId: UUID? = savedStateHandle.get<String>(ID_ARG)?.let { runCatching { UUID.fromString(it) }.getOrNull() }

    private val _state = MutableStateFlow(ItemDetailUiState())
    val state: StateFlow<ItemDetailUiState> = _state.asStateFlow()

    private val _effects = Channel<ItemDetailEffect>(Channel.BUFFERED)
    val effects: Flow<ItemDetailEffect> = _effects.receiveAsFlow()

    // El elemento completo, solo en memoria y solo mientras la bóveda está abierta.
    private var entry: ReadableEntry? = null
    private var copiedJob: Job? = null
    private var closing = false

    init {
        viewModelScope.launch {
            // Solo este elemento: no se descifra toda la bóveda en cada cambio de Room.
            val changes = itemId?.let { repository.observeEntry(it) } ?: flowOf(null)
            changes.collectLatest { entry ->
                val current = entry as? ReadableEntry
                if (current == null || deletion.pending.value?.id == itemId) onGone() else onFound(current)
            }
        }
    }

    private fun onFound(found: ReadableEntry) {
        entry = found
        val item = found.item
        _state.update { old ->
            ItemDetailUiState(
                status = DetailStatus.Found,
                name = item.name,
                initial = initialOf(item.name),
                edited = EditedAge.of(item.passwordChangedAt, Instant.ofEpochMilli(clock.wallMillis())),
                username = item.username,
                site = item.url,
                siteOpenable = SiteUrl.toOpenable(item.url) != null,
                notes = item.notes,
                passwordLength = item.password.length,
                // Si estaba a la vista y la contraseña cambió, se ve la nueva.
                shownPassword = if (old.passwordVisible) item.password else null,
                copied = old.copied,
                showDeleteSheet = old.showDeleteSheet,
            )
        }
    }

    /** Ya no está (se eliminó, no se puede leer o la bóveda se bloqueó): se olvida todo lo descifrado. */
    private fun onGone() {
        entry = null
        copiedJob?.cancel()
        _state.value = ItemDetailUiState(status = DetailStatus.Gone)
        // Con la bóveda bloqueada navega LockGuard; aquí solo se sale si el elemento desapareció.
        if (session.state.value is VaultState.Unlocked) close()
    }

    /** Una sola salida: dos toques seguidos no deben sacar también la pantalla de abajo. */
    private fun close() {
        if (closing) return
        closing = true
        viewModelScope.launch { _effects.send(ItemDetailEffect.Close) }
    }

    fun onTogglePassword() {
        val item = entry?.item ?: return
        _state.update { it.withShownPassword(if (it.passwordVisible) null else item.password) }
    }

    /** La app salió de primer plano: la contraseña vuelve a ocultarse. */
    fun onHidePassword() {
        _state.update { it.withShownPassword(null) }
    }

    fun onCopyPassword() = copyToClipboard(CopiedKind.Password) { it.password }

    fun onCopyUsername() = copyToClipboard(CopiedKind.Username) { it.username }

    private fun copyToClipboard(kind: CopiedKind, text: (VaultItem) -> String) {
        val item = entry?.item ?: return
        copiedJob?.cancel()
        copiedJob = viewModelScope.launch {
            val seconds = clipboard.copy(text(item))
            _state.update { it.copy(copied = CopiedNotice(kind, seconds)) }
            delay(COPIED_NOTICE_MILLIS)
            _state.update { it.copy(copied = null) }
        }
    }

    fun onOpenSite() {
        val url = SiteUrl.toOpenable(entry?.item?.url) ?: return
        viewModelScope.launch { _effects.send(ItemDetailEffect.OpenSite(url)) }
    }

    fun onDeleteClick() = _state.update { it.copy(showDeleteSheet = true) }

    fun onDeleteDismiss() = _state.update { it.copy(showDeleteSheet = false) }

    /** «Eliminar» (OP-03): el borrado espera 5 s a su «Deshacer» en la lista (SCR-17). */
    fun onDeleteConfirm() {
        val current = entry ?: return
        if (closing) return
        deletion.schedule(PendingDeletion(current.id, current.item.name, current.localSeq))
        _state.update { it.copy(showDeleteSheet = false) }
        // A la lista, que ya oculta el elemento y muestra «Se eliminó · Deshacer».
        close()
    }

    override fun onCleared() {
        entry = null
    }

    private fun ItemDetailUiState.copy(
        copied: CopiedNotice? = this.copied,
        showDeleteSheet: Boolean = this.showDeleteSheet,
    ) = ItemDetailUiState(
        status, name, initial, edited, username, site, siteOpenable, notes, passwordLength, shownPassword, copied, showDeleteSheet,
    )

    private fun ItemDetailUiState.withShownPassword(password: String?) = ItemDetailUiState(
        status, name, initial, edited, username, site, siteOpenable, notes, passwordLength, password, copied, showDeleteSheet,
    )

    companion object {
        const val ID_ARG = "id"

        /** SCR-13: el estado «copiada» dura 2,5 s. */
        const val COPIED_NOTICE_MILLIS = 2_500L
    }
}
