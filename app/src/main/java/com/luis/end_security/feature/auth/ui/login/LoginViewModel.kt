package com.luis.end_security.feature.auth.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luis.end_security.core.common.Countdown
import com.luis.end_security.feature.auth.domain.EmailFormat
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

@HiltViewModel
class LoginViewModel @Inject constructor(
    timeSource: TimeSource,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    private val _effects = Channel<LoginEffect>(Channel.BUFFERED)
    val effects: Flow<LoginEffect> = _effects.receiveAsFlow()

    private var passwordIsEmpty = true
    private val lockoutCountdown = Countdown(timeSource)

    fun onAction(action: LoginAction) {
        when (action) {
            is LoginAction.EmailChanged -> _state.update {
                it.copy(email = action.value, isEmailValid = EmailFormat.isValid(action.value), error = null)
                    .withCanSubmit()
            }
            is LoginAction.PasswordEmptinessChanged -> {
                passwordIsEmpty = action.isEmpty
                _state.update { it.copy(error = null).withCanSubmit() }
            }
            LoginAction.Submit -> submit()
        }
    }

    private fun submit() {
        if (!_state.value.canSubmit) return
        // TODO(OP-13, OP-14): prelogin + login. La pantalla copiará la contraseña a un CharArray
        //  (normalizada con MasterPasswordNormalizer, NFKC + UTF-8, DEC-31)
        //  al tocar «Entrar», se derivará auth_key (DEC-05) y el arreglo se sobrescribirá.
        //  Falta PEND-01 (Argon2) para crear AuthRepository; por ahora no se envía nada.
        // TODO(OP-14): solo si la cuenta tiene 2FA; si no, a la bóveda.
        viewModelScope.launch { _effects.send(LoginEffect.NavigateToTwoFactor) }
    }

    /** Demasiados intentos (DEC-32): el servidor dice cuántos minutos esperar. */
    internal fun onLockedOut(minutes: Int) {
        lockoutCountdown.start(viewModelScope, minutes.minutes) { secondsLeft ->
            _state.update { it.copy(lockoutSecondsLeft = secondsLeft).withCanSubmit() }
        }
    }

    private fun LoginUiState.withCanSubmit(): LoginUiState =
        copy(canSubmit = isEmailValid && !passwordIsEmpty && !isLoading && !isLockedOut)
}
