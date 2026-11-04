package com.luis.end_security.feature.account.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luis.end_security.core.common.ApiError
import com.luis.end_security.core.common.ApiResult
import com.luis.end_security.core.common.Countdown
import com.luis.end_security.core.designsystem.component.CodeFieldFilter
import com.luis.end_security.core.security.SecretClipboard
import com.luis.end_security.feature.account.domain.TwoFactorRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** Qué operación sensible pide la hoja «Confirma que eres tú». */
enum class ReauthPurpose { Regenerate, Disable }

/**
 * Estado de «Verificación en dos pasos está activa» (SCR de Figma 218:4304) y de su hoja de reautenticación
 * (218:4421). La contraseña maestra **no** está aquí: vive en un `TextFieldState` de la pantalla (regla 6). Los códigos
 * de respaldo nuevos tampoco se imprimen en `toString()`.
 */
data class TwoFactorManageUiState(
    /** La hoja de reautenticación abierta, o `null`. */
    val sheet: ReauthPurpose? = null,
    val code: String = "",
    val showPasswordRequired: Boolean = false,
    val showCodeIncomplete: Boolean = false,
    /** `reauth_required`: se marcan los dos campos con «Contraseña o código incorrectos.» */
    val credentialsWrong: Boolean = false,
    val genericError: Boolean = false,
    val lockoutSecondsLeft: Int = 0,
    /** Derivando la `auth_key` (Argon2) y llamando al servidor. */
    val isLoading: Boolean = false,
    val focusRequest: ReauthFocus? = null,
    /** Códigos de respaldo nuevos, para mostrarlos con el diseño del Paso 3. */
    val newCodes: List<String>? = null,
    val copiedSeconds: Int? = null,
) {
    val isLockedOut: Boolean get() = lockoutSecondsLeft > 0

    override fun toString(): String = "TwoFactorManageUiState(sheet=$sheet, digits=${code.length}, newCodes=${newCodes?.size}, ***)"
}

enum class ReauthField { Password, Code }

/** [id] cambia en cada intento, para que dos toques seguidos con el mismo error vuelvan a dar el foco. */
data class ReauthFocus(val field: ReauthField, val id: Int)

sealed interface ManageAction {
    data object RegenerateClicked : ManageAction
    data object DisableClicked : ManageAction
    data object SheetDismissed : ManageAction
    data class CodeChanged(val value: String) : ManageAction {
        override fun toString(): String = "CodeChanged(***)"
    }

    /** La pantalla avisa si el campo de contraseña quedó vacío o no; nunca manda su contenido. */
    data class PasswordEmptinessChanged(val isEmpty: Boolean) : ManageAction

    /** «Generar» o «Desactivar». [password] es el texto del campo; no se guarda. */
    class Submit(val password: CharSequence) : ManageAction {
        override fun toString(): String = "Submit(***)"
    }

    data object CopyCodes : ManageAction

    /** Cierra la vista de los códigos nuevos. */
    data object CodesClosed : ManageAction
}

sealed interface ManageEffect {
    /** 2FA desactivada: vuelve a Ajustes con «Verificación en dos pasos desactivada». */
    data object Disabled : ManageEffect
}

@HiltViewModel
class TwoFactorManageViewModel @Inject constructor(
    private val repository: TwoFactorRepository,
    private val clipboard: SecretClipboard,
    timeSource: TimeSource,
) : ViewModel() {

    private val _state = MutableStateFlow(TwoFactorManageUiState())
    val state: StateFlow<TwoFactorManageUiState> = _state.asStateFlow()

    private val _effects = Channel<ManageEffect>(Channel.BUFFERED)
    val effects: Flow<ManageEffect> = _effects.receiveAsFlow()

    private var passwordIsEmpty = true
    private var focusSequence = 0
    private var recoveryCodes: List<String> = emptyList()
    private var copiedJob: Job? = null
    private val lockoutCountdown = Countdown(timeSource)

    fun onAction(action: ManageAction) {
        when (action) {
            ManageAction.RegenerateClicked -> openSheet(ReauthPurpose.Regenerate)
            ManageAction.DisableClicked -> openSheet(ReauthPurpose.Disable)
            ManageAction.SheetDismissed -> if (!_state.value.isLoading) _state.update { closedSheet(it) }
            is ManageAction.CodeChanged -> _state.update {
                val code = CodeFieldFilter.filter(action.value)
                it.copy(
                    code = code,
                    credentialsWrong = false,
                    genericError = false,
                    showCodeIncomplete = it.showCodeIncomplete && code.length < CodeFieldFilter.LENGTH,
                )
            }
            is ManageAction.PasswordEmptinessChanged -> {
                passwordIsEmpty = action.isEmpty
                _state.update {
                    it.copy(
                        showPasswordRequired = it.showPasswordRequired && action.isEmpty,
                        credentialsWrong = false,
                        genericError = false,
                    )
                }
            }
            is ManageAction.Submit -> submit(action.password)
            ManageAction.CopyCodes -> copyCodes()
            ManageAction.CodesClosed -> {
                recoveryCodes = emptyList()
                _state.update { it.copy(newCodes = null, copiedSeconds = null) }
            }
        }
    }

    private fun openSheet(purpose: ReauthPurpose) {
        passwordIsEmpty = true
        _state.update { closedSheet(it).copy(sheet = purpose) }
    }

    private fun closedSheet(state: TwoFactorManageUiState) = state.copy(
        sheet = null,
        code = "",
        showPasswordRequired = false,
        showCodeIncomplete = false,
        credentialsWrong = false,
        genericError = false,
    )

    /** OP-24 y OP-25: reautentica (la `auth_key` se deriva en el teléfono) y llama a EP-21 o EP-22. */
    private fun submit(password: CharSequence) {
        val current = _state.value
        val purpose = current.sheet ?: return
        if (current.isLoading || current.isLockedOut) return
        val passwordEmpty = password.isEmpty()
        val codeBad = current.code.length != CodeFieldFilter.LENGTH
        if (passwordEmpty || codeBad) {
            // Vacíos o incompletos: se marcan y se da el foco al primero; no se deriva nada ni se llama al servidor.
            _state.update {
                it.copy(
                    showPasswordRequired = passwordEmpty,
                    showCodeIncomplete = codeBad,
                    credentialsWrong = false,
                    focusRequest = ReauthFocus(if (passwordEmpty) ReauthField.Password else ReauthField.Code, ++focusSequence),
                )
            }
            return
        }
        _state.update { it.copy(isLoading = true, credentialsWrong = false, genericError = false) }
        val code = current.code
        viewModelScope.launch {
            when (purpose) {
                ReauthPurpose.Disable -> handle(repository.disable(password, code)) {
                    _state.update { closedSheet(it).copy(isLoading = false) }
                    _effects.trySend(ManageEffect.Disabled)
                }
                ReauthPurpose.Regenerate -> handle(repository.regenerateRecoveryCodes(password, code)) { codes ->
                    recoveryCodes = codes
                    _state.update { closedSheet(it).copy(isLoading = false, newCodes = codes) }
                }
            }
        }
    }

    private inline fun <T> handle(result: ApiResult<T>, onSuccess: (T) -> Unit) {
        when (result) {
            is ApiResult.Success -> onSuccess(result.value)
            is ApiResult.Failure -> {
                val error = result.error
                // Fuera de `update`: el bloque del CAS puede repetirse y no debe tener efectos.
                if (error is ApiError.RateLimited) {
                    lockoutCountdown.start(viewModelScope, error.retryAfterSeconds.seconds) { left ->
                        _state.update { s -> s.copy(lockoutSecondsLeft = left) }
                    }
                }
                _state.update {
                    when {
                        error == ApiError.ReauthRequired || error is ApiError.InvalidCode || error == ApiError.InvalidCredentials ->
                            it.copy(isLoading = false, credentialsWrong = true, code = "")
                        error is ApiError.RateLimited -> it.copy(isLoading = false, code = "")
                        else -> it.copy(isLoading = false, genericError = true)
                    }
                }
            }
        }
    }

    private fun copyCodes() {
        val text = recoveryCodes.joinToString("\n")
        if (text.isEmpty()) return
        copiedJob?.cancel()
        copiedJob = viewModelScope.launch {
            val seconds = clipboard.copy(text)
            _state.update { it.copy(copiedSeconds = seconds) }
            delay(3_000)
            _state.update { it.copy(copiedSeconds = null) }
        }
    }

    override fun onCleared() {
        lockoutCountdown.cancel()
        recoveryCodes = emptyList()
        _state.update { it.copy(newCodes = null, code = "") }
    }
}
