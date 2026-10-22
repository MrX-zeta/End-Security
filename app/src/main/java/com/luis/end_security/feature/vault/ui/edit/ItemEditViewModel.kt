package com.luis.end_security.feature.vault.ui.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luis.end_security.core.common.DefaultDispatcher
import com.luis.end_security.feature.auth.domain.PasswordStrengthEstimator
import com.luis.end_security.feature.vault.domain.ItemDraft
import com.luis.end_security.feature.vault.domain.ReadableEntry
import com.luis.end_security.feature.vault.domain.SaveResult
import com.luis.end_security.feature.vault.domain.VaultItem
import com.luis.end_security.feature.vault.domain.VaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

/** Los valores con que arranca el formulario al editar. Lleva la contraseña: se aplica una vez y se suelta. */
class FormValues(
    val name: String,
    val username: String,
    val password: String,
    val url: String,
    val notes: String,
) {
    /** ¿Lo escrito en [draft] es distinto de estos valores? Lo usa la pantalla para «¿Descartar cambios?». */
    fun differsFrom(draft: ItemDraft): Boolean =
        draft.name != name || draft.username != username || draft.password != password ||
            (draft.url ?: "") != url || (draft.notes ?: "") != notes

    override fun toString(): String = "FormValues(***)"

    companion object {
        /** Lo que tiene un formulario nuevo al abrir. */
        val EMPTY = FormValues("", "", "", "", "")
    }
}

enum class EditStatus { Loading, Ready, Gone }

enum class SaveError { Generic, Conflict }

/**
 * Estado de Nueva / editar (SCR-14). La contraseña que se escribe vive en el `TextFieldState` de la
 * pantalla, no aquí; [prefill] solo existe desde que se carga un elemento hasta que la pantalla lo
 * copia a sus campos ([ItemEditViewModel.onPrefillApplied]).
 */
class ItemEditUiState(
    val isNew: Boolean,
    val status: EditStatus = EditStatus.Loading,
    val prefill: FormValues? = null,
    /** 0–4 del medidor, o `null` con la contraseña vacía. */
    val strengthScore: Int? = null,
    val nameError: Boolean = false,
    val isSaving: Boolean = false,
    val error: SaveError? = null,
    val showDiscardSheet: Boolean = false,
) {
    override fun toString(): String = "ItemEditUiState(isNew=$isNew, status=$status, ***)"
}

sealed interface ItemEditEffect {
    /** Guardado: volver. */
    data object Saved : ItemEditEffect

    /** Salir sin guardar (o el elemento ya no existe). */
    data object Close : ItemEditEffect
}

/**
 * Nueva contraseña y editar (OP-01, OP-02). El elemento completo se arma solo al tocar «Guardar»,
 * con lo que la pantalla le manda en [onSave]; antes, nada de lo escrito pasa por el estado.
 */
@HiltViewModel
class ItemEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: VaultRepository,
    private val estimator: PasswordStrengthEstimator,
    @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val itemId: UUID? = savedStateHandle.get<String>(ID_ARG)?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    private val isNew = savedStateHandle.get<String>(ID_ARG) == null

    private val _state = MutableStateFlow(ItemEditUiState(isNew = isNew, status = if (isNew) EditStatus.Ready else EditStatus.Loading))
    val state: StateFlow<ItemEditUiState> = _state.asStateFlow()

    private val _effects = Channel<ItemEditEffect>(Channel.BUFFERED)
    val effects: Flow<ItemEditEffect> = _effects.receiveAsFlow()

    private var expectedLocalSeq: Long = 0
    private var strengthJob: Job? = null
    private var closing = false

    /**
     * La pantalla pide los valores cada vez que arma sus campos (al abrir y al recrearse la actividad,
     * p. ej. al girar): los `TextFieldState` no sobreviven, así que no se puede suponer que ya los tiene.
     * Se leen de nuevo del repositorio; el ViewModel no los retiene.
     */
    fun requestPrefill() {
        if (isNew) return
        viewModelScope.launch {
            val loaded = itemId?.let { repository.entry(it) } as? ReadableEntry
            if (loaded == null) {
                _state.update { it.withStatus(EditStatus.Gone) }
                close()
            } else {
                expectedLocalSeq = loaded.localSeq
                val values = loaded.item.toFormValues()
                _state.update { it.withStatus(EditStatus.Ready, prefill = values) }
                onPasswordChanged(values.password)
            }
        }
    }

    /** La pantalla ya copió el [FormValues] a sus campos: se suelta el de este estado. */
    fun onPrefillApplied() {
        _state.update { it.withStatus(it.status, prefill = null) }
    }

    /** La contraseña cambió: el medidor solo informa la fuerza, sin la política de la contraseña maestra. */
    fun onPasswordChanged(password: CharSequence) {
        strengthJob?.cancel()
        if (password.isEmpty()) {
            _state.update { it.withStrength(null) }
            return
        }
        // Se copia a una String corta de vida para no leer el campo desde otro hilo mientras se escribe.
        val snapshot = password.toString()
        strengthJob = viewModelScope.launch {
            delay(STRENGTH_DEBOUNCE_MILLIS)
            val score = withContext(defaultDispatcher) { estimator.score(snapshot) }
            _state.update { it.withStrength(score) }
        }
    }

    fun onNameChanged() {
        _state.update { if (it.nameError || it.error != null) it.withErrors(nameError = false, error = null) else it }
    }

    fun onSave(draft: ItemDraft) {
        val current = _state.value
        if (current.isSaving || current.status != EditStatus.Ready) return
        if (draft.name.isBlank()) {
            _state.update { it.withErrors(nameError = true, error = null) }
            return
        }
        _state.update { it.withSaving(true) }
        viewModelScope.launch {
            val result = if (isNew) repository.create(draft) else repository.update(requireNotNull(itemId), expectedLocalSeq, draft)
            when (result) {
                // `isSaving` se queda en `true`: un segundo toque no debe guardar un duplicado.
                is SaveResult.Saved -> if (!closing) {
                    closing = true
                    _effects.send(ItemEditEffect.Saved)
                }
                SaveResult.Conflict -> _state.update { it.withSaving(false).withErrors(nameError = false, error = SaveError.Conflict) }
                SaveResult.NotFound -> _state.update { it.withSaving(false).withErrors(nameError = false, error = SaveError.Generic) }
                // La bóveda se bloqueó: LockGuard ya navega a Desbloquear.
                SaveResult.Locked -> _state.update { it.withSaving(false) }
            }
        }
    }

    /**
     * Atrás o el botón del sistema: con cambios sin guardar pide confirmación (SCR-14). Quién sabe si
     * hay cambios es la pantalla, que tiene los campos y lo que había al abrir.
     */
    fun onBack(hasChanges: Boolean) {
        if (hasChanges) _state.update { it.withDiscardSheet(true) } else close()
    }

    fun onDiscardConfirm() {
        _state.update { it.withDiscardSheet(false) }
        close()
    }

    fun onDiscardDismiss() {
        _state.update { it.withDiscardSheet(false) }
    }

    /** Una sola salida: dos toques seguidos no deben sacar también la pantalla de abajo. */
    private fun close() {
        if (closing) return
        closing = true
        viewModelScope.launch { _effects.send(ItemEditEffect.Close) }
    }

    private fun VaultItem.toFormValues() = FormValues(
        name = name,
        username = username,
        password = password,
        url = url.orEmpty(),
        notes = notes.orEmpty(),
    )

    // El estado no es `data class` (su `toString` no debe imprimir nada): copias explícitas.
    private fun ItemEditUiState.withStatus(status: EditStatus, prefill: FormValues? = this.prefill) = ItemEditUiState(
        isNew, status, prefill, strengthScore, nameError, isSaving, error, showDiscardSheet,
    )

    private fun ItemEditUiState.withStrength(score: Int?) = ItemEditUiState(
        isNew, status, prefill, score, nameError, isSaving, error, showDiscardSheet,
    )

    private fun ItemEditUiState.withErrors(nameError: Boolean, error: SaveError?) = ItemEditUiState(
        isNew, status, prefill, strengthScore, nameError, isSaving, error, showDiscardSheet,
    )

    private fun ItemEditUiState.withSaving(saving: Boolean) = ItemEditUiState(
        isNew, status, prefill, strengthScore, nameError, saving, if (saving) null else error, showDiscardSheet,
    )

    private fun ItemEditUiState.withDiscardSheet(show: Boolean) = ItemEditUiState(
        isNew, status, prefill, strengthScore, nameError, isSaving, error, show,
    )

    companion object {
        const val ID_ARG = "id"
        const val STRENGTH_DEBOUNCE_MILLIS = 150L
    }
}
