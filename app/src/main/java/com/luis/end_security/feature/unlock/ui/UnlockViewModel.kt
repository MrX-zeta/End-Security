package com.luis.end_security.feature.unlock.ui

import androidx.lifecycle.ViewModel
import com.luis.end_security.core.designsystem.component.SignOutSheet
import com.luis.end_security.feature.unlock.domain.BiometricPreparation
import com.luis.end_security.feature.unlock.domain.BiometricResult
import com.luis.end_security.feature.unlock.domain.BiometricUnlock
import javax.crypto.Cipher
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
    /** «Escribe tu contraseña maestra.»: al tocar «Desbloquear» con el campo vacío. */
    val showPasswordRequired: Boolean = false,
    /** Cambia cada vez que la pantalla debe dar el foco al campo (con error al tocar el botón). */
    val focusRequest: Int = 0,
    /** Segundos de bloqueo por demasiados intentos (DEC-08); 0 sin bloqueo. */
    val lockoutSecondsLeft: Int = 0,
    /** Hoja de «Cerrar sesión» abierta: la normal o la de cambios sin subir (la bóveda está bloqueada, no se sincroniza). */
    val signOutSheet: SignOutSheet? = null,
    /** «Tu huella cambió. Entra con tu contraseña…»: la llave se invalidó y ya se desactivó todo (DEC-09). */
    val biometricInvalidated: Boolean = false,
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

    /** El `BiometricPrompt` aceptó la huella con este `Cipher` ya autenticado. */
    class BiometricAuthenticated(val cipher: Cipher) : UnlockAction {
        override fun toString(): String = "BiometricAuthenticated(***)"
    }

    /** El `BiometricPrompt` se canceló («Usar contraseña») o falló: se queda en Desbloquear. */
    data object BiometricCancelled : UnlockAction

    /** «Cerrar sesión» en «¿No eres Ana?». */
    data object SignOutClicked : UnlockAction

    /** «Cancelar» o cerrar la hoja. */
    data object SignOutDismissed : UnlockAction

    /** «Cerrar sesión» en la hoja. */
    data object SignOutConfirmed : UnlockAction

    /** Desbloquear pasó a `RESUMED` (a la vista y en primer plano). */
    data object ScreenResumed : UnlockAction

    /** Desbloquear dejó de estar a la vista. */
    data object ScreenPaused : UnlockAction
}

sealed interface UnlockEffect {
    /** Bóveda abierta: a la Bóveda, sin nada debajo. */
    data object NavigateToVault : UnlockEffect

    /** Sesión cerrada (o no hay cuenta): a Iniciar sesión, sin nada debajo. */
    data object NavigateToLogin : UnlockEffect

    /** La pantalla muestra el `BiometricPrompt` con este `Cipher` de descifrado (OP-20). */
    class ShowBiometricPrompt(val cipher: Cipher) : UnlockEffect
}

@HiltViewModel
class UnlockViewModel @Inject constructor(
    private val settings: UnlockSettingsRepository,
    attempts: UnlockAttemptRepository,
    private val auth: AuthRepository,
    private val biometric: BiometricUnlock,
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

    /** El diálogo de la huella sale **una sola vez por cada bloqueo** (cada bloqueo crea esta pantalla de nuevo). */
    private var autoPrompted = false

    /** Desbloquear está a la vista (`RESUMED`): con el autobloqueo en segundo plano, el diálogo espera a volver. */
    private var resumed = false

    private fun maybeAutoPrompt() {
        if (!resumed || autoPrompted || !_state.value.biometricAvailable) return
        autoPrompted = true
        startBiometric()
    }

    /** Hay un diálogo de huella abierto o descifrando: un toque más no abre otro. */
    private var biometricBusy = false

    init {
        viewModelScope.launch {
            settings.biometricEnabled.collect { enabled ->
                _state.update { it.copy(biometricAvailable = enabled) }
                maybeAutoPrompt()
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
                _state.update {
                    it.copy(
                        wrongPassword = it.wrongPassword && action.isEmpty,
                        // En cuanto escribe, el error de «falta la contraseña» se quita.
                        showPasswordRequired = it.showPasswordRequired && action.isEmpty,
                    ).withCanSubmit()
                }
            }
            is UnlockAction.Submit -> submit(action.password)
            UnlockAction.BiometricClicked -> startBiometric()
            is UnlockAction.BiometricAuthenticated -> completeBiometric(action.cipher)
            UnlockAction.BiometricCancelled -> biometricBusy = false
            UnlockAction.SignOutClicked -> openSignOutSheet()
            UnlockAction.SignOutDismissed -> _state.update { it.copy(signOutSheet = null) }
            UnlockAction.ScreenResumed -> {
                resumed = true
                maybeAutoPrompt()
            }
            UnlockAction.ScreenPaused -> resumed = false
            UnlockAction.SignOutConfirmed -> signOut()
        }
    }

    /** OP-20, paso 1: prepara el `Cipher` de descifrado y pide a la pantalla el diálogo de la huella. */
    private fun startBiometric() {
        if (biometricBusy || _state.value.isLoading) return
        biometricBusy = true
        viewModelScope.launch {
            val prepared = try {
                biometric.prepareUnlock()
            } catch (_: RuntimeException) {
                BiometricPreparation.Failed
            }
            when (prepared) {
                is BiometricPreparation.Ready -> _effects.send(UnlockEffect.ShowBiometricPrompt(prepared.cipher))
                BiometricPreparation.Invalidated -> {
                    biometricBusy = false
                    // La huella cambió: ya se desactivó todo; se pide la contraseña maestra.
                    _state.update { it.copy(biometricInvalidated = true) }
                }
                else -> biometricBusy = false
            }
        }
    }

    /** OP-20, paso 2: con la huella aceptada, abre la bóveda. No cuenta ni reinicia los intentos de la contraseña. */
    private fun completeBiometric(cipher: Cipher) {
        // Con un cierre de sesión o un desbloqueo con contraseña en curso, la huella no abre nada.
        if (_state.value.isLoading) {
            biometricBusy = false
            return
        }
        viewModelScope.launch {
            val result = try {
                biometric.completeUnlock(cipher)
            } catch (_: RuntimeException) {
                BiometricResult.Failed
            } finally {
                biometricBusy = false
            }
            when (result) {
                BiometricResult.Done -> _effects.send(UnlockEffect.NavigateToVault)
                BiometricResult.Invalidated -> _state.update { it.copy(biometricInvalidated = true) }
                BiometricResult.Failed -> Unit
            }
        }
    }

    private fun submit(password: CharSequence) {
        val current = _state.value
        if (current.isLoading || current.isLockedOut) return
        if (password.isEmpty()) {
            // Con el campo vacío no se intenta nada (ni se cuenta un intento): se marca el campo y se le da el foco.
            _state.update { it.copy(showPasswordRequired = true, wrongPassword = false, focusRequest = it.focusRequest + 1) }
            return
        }
        _state.update { it.copy(isLoading = true, wrongPassword = false, showPasswordRequired = false).withCanSubmit() }
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

    /** El conteo sale de la columna `sync_state`; sin intento de sincronizar (la bóveda está bloqueada). */
    private fun openSignOutSheet() {
        viewModelScope.launch {
            val count = settings.unsyncedCount()
            _state.update { it.copy(signOutSheet = if (count > 0) SignOutSheet.Unsynced(count) else SignOutSheet.Confirm) }
        }
    }

    private fun signOut() {
        _state.update { it.copy(signOutSheet = null, isLoading = true).withCanSubmit() }
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
