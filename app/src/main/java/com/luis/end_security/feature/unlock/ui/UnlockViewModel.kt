package com.luis.end_security.feature.unlock.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luis.end_security.core.common.Countdown
import com.luis.end_security.feature.unlock.domain.UnlockSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource

/**
 * Estado de Desbloquear (SCR-09). La contraseña maestra NO está aquí (regla no negociable 6):
 * vive en un TextFieldState de la pantalla y el ViewModel solo sabe si está vacía.
 */
data class UnlockUiState(
    /** Nombre de la cuenta para «¿No eres Ana?»; `null` mientras no se conozca. */
    val displayName: String? = null,
    /** `biometric_enabled` (DEC-09): con `false` no se muestra el botón de huella. */
    val biometricAvailable: Boolean = false,
    val canSubmit: Boolean = false,
    val isLoading: Boolean = false,
    /** «Contraseña incorrecta.» */
    val wrongPassword: Boolean = false,
    /** Segundos de bloqueo por demasiados intentos (DEC-32); 0 sin bloqueo. */
    val lockoutSecondsLeft: Int = 0,
    /** Hoja «¿Cerrar sesión?» abierta. */
    val showSignOutConfirmation: Boolean = false,
) {
    val isLockedOut: Boolean get() = lockoutSecondsLeft > 0

    override fun toString(): String =
        "UnlockUiState(canSubmit=$canSubmit, wrongPassword=$wrongPassword, " +
            "lockoutSecondsLeft=$lockoutSecondsLeft, biometricAvailable=$biometricAvailable, ***)"
}

sealed interface UnlockAction {
    /** La pantalla avisa si el campo quedó vacío o no; nunca manda su contenido. */
    data class PasswordEmptinessChanged(val isEmpty: Boolean) : UnlockAction

    /** «Desbloquear» o «Listo» en el teclado. */
    data object Submit : UnlockAction

    /** Botón de huella. */
    data object BiometricClicked : UnlockAction

    /** «Cerrar sesión» en «¿No eres Ana?». */
    data object SignOutClicked : UnlockAction

    /** «Cancelar» o cerrar la hoja. */
    data object SignOutDismissed : UnlockAction

    /** «Cerrar sesión» en la hoja. */
    data object SignOutConfirmed : UnlockAction
}

sealed interface UnlockEffect {
    /** Sesión cerrada: a Iniciar sesión, sin nada debajo. */
    data object NavigateToLogin : UnlockEffect
}

@HiltViewModel
class UnlockViewModel @Inject constructor(
    settings: UnlockSettingsRepository,
    timeSource: TimeSource,
) : ViewModel() {

    // TODO(TBL-L-ACCOUNT): leer display_name de la cuenta local cuando exista Room.
    private val _state = MutableStateFlow(UnlockUiState())
    val state: StateFlow<UnlockUiState> = _state.asStateFlow()

    private val _effects = Channel<UnlockEffect>(Channel.BUFFERED)
    val effects: Flow<UnlockEffect> = _effects.receiveAsFlow()

    private val _clearRequests = MutableStateFlow(0)

    /** Cambia cada vez que la pantalla debe vaciar el campo. */
    val clearRequests: StateFlow<Int> = _clearRequests.asStateFlow()

    private var passwordIsEmpty = true
    private val lockoutCountdown = Countdown(timeSource)

    init {
        viewModelScope.launch {
            settings.biometricEnabled.collect { enabled ->
                _state.update { it.copy(biometricAvailable = enabled) }
            }
        }
    }

    fun onAction(action: UnlockAction) {
        when (action) {
            is UnlockAction.PasswordEmptinessChanged -> {
                passwordIsEmpty = action.isEmpty
                // Al escribir otra vez se quita el error; vaciar el campo no lo quita.
                _state.update { it.copy(wrongPassword = it.wrongPassword && action.isEmpty).withCanSubmit() }
            }
            UnlockAction.Submit -> submit()
            // TODO(OP-20): BiometricPrompt con CryptoObject (DEC-09): la llave del Keystore abre la
            //  vault_key envuelta (bio_wrapped_vault_key). Si la biometría cambió, pedir la contraseña.
            UnlockAction.BiometricClicked -> Unit
            UnlockAction.SignOutClicked -> _state.update { it.copy(showSignOutConfirmation = true) }
            UnlockAction.SignOutDismissed -> _state.update { it.copy(showSignOutConfirmation = false) }
            UnlockAction.SignOutConfirmed -> signOut()
        }
    }

    private fun submit() {
        if (!_state.value.canSubmit) return
        // TODO(OP-19): la pantalla copiará la contraseña a un CharArray normalizado con
        //  MasterPasswordNormalizer (DEC-31) y codificado en UTF-8 con CodingErrorAction.REPORT
        //  (si falla, es «Contraseña incorrecta.»); se derivará enc_key con Argon2 (PEND-01), se
        //  abrirá pvk para obtener vault_key en VaultSession y el arreglo se sobrescribirá. Sin red.
        //  Mientras dure, isLoading = true y no se aceptan más envíos.
    }

    private fun signOut() {
        _state.update { it.copy(showSignOutConfirmation = false) }
        // TODO(OP-18): revocar la sesión en el servidor, borrar tokens y datos locales y bloquear.
        viewModelScope.launch { _effects.send(UnlockEffect.NavigateToLogin) }
    }

    /** La contraseña no abrió la bóveda (OP-19). El campo se vacía. */
    internal fun onWrongPassword() {
        _state.update { it.copy(wrongPassword = true).withCanSubmit() }
        _clearRequests.update { it + 1 }
    }

    /** Demasiados intentos (DEC-32): minutos que hay que esperar. El campo se vacía. */
    internal fun onLockedOut(minutes: Int) {
        _state.update { it.copy(wrongPassword = false) }
        _clearRequests.update { it + 1 }
        lockoutCountdown.start(viewModelScope, minutes.minutes) { secondsLeft ->
            _state.update { it.copy(lockoutSecondsLeft = secondsLeft).withCanSubmit() }
        }
    }

    private fun UnlockUiState.withCanSubmit(): UnlockUiState =
        copy(canSubmit = !passwordIsEmpty && !isLoading && !isLockedOut)
}
