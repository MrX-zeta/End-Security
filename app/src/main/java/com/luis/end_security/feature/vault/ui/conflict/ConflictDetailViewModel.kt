package com.luis.end_security.feature.vault.ui.conflict

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luis.end_security.core.session.VaultSession
import com.luis.end_security.core.session.VaultState
import com.luis.end_security.feature.vault.domain.ConflictDetail
import com.luis.end_security.feature.vault.domain.ConflictLoad
import com.luis.end_security.feature.vault.domain.ConflictRepository
import com.luis.end_security.feature.vault.domain.KeepVersion
import com.luis.end_security.feature.vault.domain.ResolveResult
import com.luis.end_security.feature.vault.domain.VersionSide
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

enum class ConflictStatus { Loading, Ready, Offline, Failed, Gone }

/** Qué hay en cada lado: una versión con datos, una eliminación o algo que no se pudo abrir. */
enum class SideKind { Live, Deleted, Unreadable }

/** Un renglón de una de las dos versiones. [differs]: es distinto en la otra (se resalta en negritas). */
class VersionField(val kind: FieldKind, val value: String, val differs: Boolean) {
    override fun toString(): String = "VersionField(***)"
}

enum class FieldKind { Name, Username, Site, Notes }

/**
 * Una de las dos versiones de SCR-21. La contraseña solo está como `String` mientras se ve ([shownPassword]);
 * oculta, solo se sabe su longitud (DEC-27).
 */
class VersionCard(
    val kind: SideKind,
    val at: Instant,
    val fields: List<VersionField> = emptyList(),
    val passwordLength: Int = 0,
    val passwordDiffers: Boolean = false,
    val shownPassword: String? = null,
) {
    override fun toString(): String = "VersionCard(kind=$kind, ***)"
}

enum class ResolveError { Unavailable, Stale }

class ConflictDetailUiState(
    val status: ConflictStatus = ConflictStatus.Loading,
    /** El nombre del elemento para el título; `null` si ninguna versión se puede leer o se eliminó. */
    val name: String? = null,
    val local: VersionCard? = null,
    val remote: VersionCard? = null,
    val selected: KeepVersion = KeepVersion.Local,
    val isResolving: Boolean = false,
    val error: ResolveError? = null,
) {
    /** Una de las dos es una eliminación: se elige con «Conservar la mía» / «Eliminarla», no con el selector. */
    val hasDeletion: Boolean get() = local?.kind == SideKind.Deleted || remote?.kind == SideKind.Deleted

    override fun toString(): String = "ConflictDetailUiState(status=$status, ***)"
}

sealed interface ConflictDetailEffect {
    /** Resuelto o ya no hay conflicto: volver a donde se vino. */
    data object Close : ConflictDetailEffect
}

/**
 * «Elegir versión» (SCR-21, OP-46, DEC-57). Pide la versión del servidor, descifra las dos **solo en memoria** y,
 * al elegir, la resuelve. Se vacía al bloquear; los ViewModel de las pantallas sacadas de la pila se destruyen.
 */
@HiltViewModel
class ConflictDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val conflicts: ConflictRepository,
    private val session: VaultSession,
) : ViewModel() {

    private val itemId: UUID? = savedStateHandle.get<String>(ID_ARG)?.let { runCatching { UUID.fromString(it) }.getOrNull() }

    private val _state = MutableStateFlow(ConflictDetailUiState())
    val state: StateFlow<ConflictDetailUiState> = _state.asStateFlow()

    private val _effects = Channel<ConflictDetailEffect>(Channel.BUFFERED)
    val effects: Flow<ConflictDetailEffect> = _effects.receiveAsFlow()

    // Las dos versiones descifradas, solo en memoria y solo con la bóveda abierta.
    private var detail: ConflictDetail? = null
    private var closing = false

    init {
        load()
        viewModelScope.launch {
            session.state.collect { state ->
                if (state is VaultState.Locked) {
                    detail = null
                    _state.value = ConflictDetailUiState(status = ConflictStatus.Gone)
                }
            }
        }
    }

    fun load() = loadWith(error = null)

    /** [error] se conserva al terminar de cargar: «Cambió mientras tanto» se ve sobre lo recién cargado. */
    private fun loadWith(error: ResolveError?) {
        val id = itemId ?: run {
            _state.value = ConflictDetailUiState(status = ConflictStatus.Gone)
            return
        }
        _state.update { ConflictDetailUiState(status = ConflictStatus.Loading) }
        viewModelScope.launch {
            when (val result = conflicts.load(id)) {
                is ConflictLoad.Ready -> if (session.state.value !is VaultState.Unlocked) {
                    // Se bloqueó mientras se descifraba: no se deja nada en claro en el estado.
                    _state.value = ConflictDetailUiState(status = ConflictStatus.Gone)
                } else {
                    detail = result.detail
                    _state.value = result.detail.toState().copyWith(error = error)
                }
                ConflictLoad.Gone -> {
                    _state.value = ConflictDetailUiState(status = ConflictStatus.Gone)
                    close()
                }
                ConflictLoad.Offline -> _state.value = ConflictDetailUiState(status = ConflictStatus.Offline)
                ConflictLoad.Failed -> _state.value = ConflictDetailUiState(status = ConflictStatus.Failed)
                // Con la bóveda bloqueada navega LockGuard.
                ConflictLoad.Locked -> _state.value = ConflictDetailUiState(status = ConflictStatus.Gone)
            }
        }
    }

    fun onSelect(keep: KeepVersion) {
        _state.update { it.copyWith(selected = keep, error = null) }
    }

    fun onToggleLocalPassword() = togglePassword(KeepVersion.Local)

    fun onToggleRemotePassword() = togglePassword(KeepVersion.Remote)

    /** La app salió de primer plano: las contraseñas vuelven a ocultarse. */
    fun onHidePasswords() {
        _state.update { it.copyWith(local = it.local?.withShown(null), remote = it.remote?.withShown(null)) }
    }

    /** «Conservar esta versión» (o «Conservar la mía» / «Eliminarla» con una eliminación). */
    fun onKeep(keep: KeepVersion = _state.value.selected) {
        val current = detail ?: return
        if (_state.value.isResolving || closing) return
        _state.update { it.copyWith(isResolving = true, error = null) }
        viewModelScope.launch {
            when (conflicts.resolve(current, keep)) {
                is ResolveResult.Resolved -> {
                    detail = null
                    close()
                }
                // Cambió algo mientras tanto: se vuelve a cargar con lo nuevo.
                ResolveResult.Stale -> {
                    loadWith(error = ResolveError.Stale)
                }
                ResolveResult.Unavailable -> _state.update { it.copyWith(isResolving = false, error = ResolveError.Unavailable) }
                ResolveResult.Locked -> _state.update { it.copyWith(isResolving = false) }
            }
        }
    }

    private fun close() {
        if (closing) return
        closing = true
        viewModelScope.launch { _effects.send(ConflictDetailEffect.Close) }
    }

    private fun togglePassword(side: KeepVersion) {
        val current = detail ?: return
        _state.update { state ->
            fun toggle(card: VersionCard?, side: VersionSide): VersionCard? {
                card ?: return null
                val live = side as? VersionSide.Live ?: return card
                return card.withShown(if (card.shownPassword == null) live.item.password else null)
            }
            when (side) {
                KeepVersion.Local -> state.copyWith(local = toggle(state.local, current.local))
                KeepVersion.Remote -> state.copyWith(remote = toggle(state.remote, current.remote))
            }
        }
    }

    // --- Armar lo que ve la pantalla ---

    private fun ConflictDetail.toState(): ConflictDetailUiState {
        val l = local as? VersionSide.Live
        val r = remote as? VersionSide.Live
        val passwordDiffers = l != null && r != null && l.item.password != r.item.password
        return ConflictDetailUiState(
            status = ConflictStatus.Ready,
            name = (l ?: r)?.item?.name,
            local = local.toCard(r?.item, passwordDiffers),
            remote = remote.toCard(l?.item, passwordDiffers),
            // Si la de este teléfono es una eliminación, lo más natural es quedarse con la otra; si no, la de aquí.
            // Una versión que no se puede leer no se puede elegir: se propone la otra.
            selected = if (local is VersionSide.Deleted || local is VersionSide.Unreadable) KeepVersion.Remote else KeepVersion.Local,
        )
    }

    private fun VersionSide.toCard(other: com.luis.end_security.feature.vault.domain.VaultItem?, passwordDiffers: Boolean): VersionCard =
        when (this) {
            is VersionSide.Deleted -> VersionCard(SideKind.Deleted, at)
            is VersionSide.Unreadable -> VersionCard(SideKind.Unreadable, at)
            is VersionSide.Live -> VersionCard(
                kind = SideKind.Live,
                at = at,
                fields = buildList {
                    add(VersionField(FieldKind.Name, item.name, other != null && item.name != other.name))
                    add(VersionField(FieldKind.Username, item.username, other != null && item.username != other.username))
                    add(VersionField(FieldKind.Site, item.url.orEmpty(), other != null && item.url != other.url))
                    if (item.notes != null || other?.notes != null) {
                        add(VersionField(FieldKind.Notes, item.notes.orEmpty(), other != null && item.notes != other.notes))
                    }
                },
                passwordLength = item.password.length,
                passwordDiffers = passwordDiffers,
            )
        }

    private fun VersionCard.withShown(password: String?) =
        VersionCard(kind, at, fields, passwordLength, passwordDiffers, password)

    private fun ConflictDetailUiState.copyWith(
        local: VersionCard? = this.local,
        remote: VersionCard? = this.remote,
        selected: KeepVersion = this.selected,
        isResolving: Boolean = this.isResolving,
        error: ResolveError? = this.error,
    ) = ConflictDetailUiState(status, name, local, remote, selected, isResolving, error)

    override fun onCleared() {
        detail = null
        _state.value = ConflictDetailUiState(status = ConflictStatus.Gone)
    }

    companion object {
        const val ID_ARG = "id"
    }
}
