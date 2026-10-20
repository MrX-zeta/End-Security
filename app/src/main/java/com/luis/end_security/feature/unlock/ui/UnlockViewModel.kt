package com.luis.end_security.feature.unlock.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luis.end_security.core.common.Countdown
import com.luis.end_security.feature.auth.domain.AuthRepository
import com.luis.end_security.feature.auth.domain.UnlockOutcome
import com.luis.end_security.feature.unlock.domain.UnlockAttemptRepository
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
import kotlin.time.Duration
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
    /** Segundos de bloqueo por demasiados intentos (DEC-08); 0 sin bloqueo. */
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

    /** «Desbloquear» o «Listo» en el teclado. [password] es el texto del campo; no se guarda. */
    class Submit(val password: CharSequence) : UnlockAction {
        override fun toString(): String = "Submit(***)"
    }

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
    /** Bóveda abierta: a la Bóveda, sin nada debajo. */
    data object NavigateToVault : UnlockEffect

    /** Sesión cerrada (o no hay cuenta): a Iniciar sesión, sin nada debajo. */
    data object NavigateToLogin : UnlockEffect
}

@HiltViewModel
class UnlockViewModel @Inject constructor(
    settings: UnlockSettingsRepository,
    attempts: UnlockAttemptRepository,
    private val auth: AuthRepository,
    timeSource: TimeSource,
) : ViewModel() {

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
        viewModelScope.launch {
            val name = auth.localAccountName()
            _state.update { it.copy(displayName = name) }
        }
        // Un bloqueo que empezó antes de cerrar la app sigue al volver (DEC-08).
        viewModelScope.launch {
            val remaining = attempts.remainingLockout()
            if (remaining > Duration.ZERO) startLockout(remaining)
        }
    }

    fun onAction(action: UnlockAction) {
        when (action) {
            is UnlockAction.PasswordEmptinessChanged -> {
                passwordIsEmpty = action.isEmpty
                // Al escribir otra vez se quita el error; vaciar el campo no lo quita.
                _state.update { it.copy(wrongPassword = it.wrongPassword && action.isEmpty).withCanSubmit() }
            }
            is UnlockAction.Submit -> submit(action.password)
            // TODO(OP-20): BiometricPrompt con CryptoObject (DEC-09): la llave del Keystore abre la
            //  vault_key envuelta (bio_wrapped_vault_key). Si la biometría cambió, pedir la contraseña.
            UnlockAction.BiometricClicked -> Unit
            UnlockAction.SignOutClicked -> _state.update { it.copy(showSignOutConfirmation = true) }
            UnlockAction.SignOutDismissed -> _state.update { it.copy(showSignOutConfirmation = false) }
            UnlockAction.SignOutConfirmed -> signOut()
        }
    }

    private fun submit(password: CharSequence) {
        if (!_state.value.canSubmit) return
        _state.update { it.copy(isLoading = true, wrongPassword = false).withCanSubmit() }
        viewModelScope.launch {
            // OP-19, sin red: el repositorio cuenta el intento antes de derivar (DEC-08).
            val outcome = auth.unlock(password)
            _state.update { it.copy(isLoading = false).withCanSubmit() }
            when (outcome) {
                UnlockOutcome.Unlocked -> _effects.send(UnlockEffect.NavigateToVault)
                is UnlockOutcome.WrongPassword -> onWrongPassword(outcome.lockout)
                is UnlockOutcome.LockedOut -> {
                    _clearRequests.update { it + 1 }
                    startLockout(outcome.remaining)
                }
                UnlockOutcome.NoAccount -> _effects.send(UnlockEffect.NavigateToLogin)
            }
        }
    }

    private fun signOut() {
        _state.update { it.copy(showSignOutConfirmation = false, isLoading = true).withCanSubmit() }
        viewModelScope.launch {
            // OP-18: revoca en el servidor (aunque falle la red), borra tokens y datos locales y bloquea.
            auth.signOut()
            _effects.send(UnlockEffect.NavigateToLogin)
        }
    }

    /**
     * La contraseña no abrió la bóveda (OP-19). El campo se vacía; desde el quinto fallo seguido
     * empieza el bloqueo (DEC-08), que reemplaza al error.
     */
    private fun onWrongPassword(lockout: Duration) {
        _clearRequests.update { it + 1 }
        if (lockout > Duration.ZERO) {
            startLockout(lockout)
        } else {
            _state.update { it.copy(wrongPassword = true).withCanSubmit() }
        }
    }

    private fun startLockout(duration: Duration) {
        _state.update { it.copy(wrongPassword = false) }
        lockoutCountdown.start(viewModelScope, duration) { secondsLeft ->
            _state.update { it.copy(lockoutSecondsLeft = secondsLeft).withCanSubmit() }
        }
    }

    private fun UnlockUiState.withCanSubmit(): UnlockUiState =
        copy(canSubmit = !passwordIsEmpty && !isLoading && !isLockedOut)
}
