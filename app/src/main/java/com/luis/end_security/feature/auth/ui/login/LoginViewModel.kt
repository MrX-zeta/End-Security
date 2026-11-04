package com.luis.end_security.feature.auth.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luis.end_security.core.common.Countdown
import com.luis.end_security.core.common.ApiError
import com.luis.end_security.core.common.ApiResult
import com.luis.end_security.feature.auth.domain.AuthRepository
import com.luis.end_security.feature.auth.domain.AuthStep
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
    private val auth: AuthRepository,
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
                val valid = EmailFormat.isValid(action.value)
                // En cuanto queda bien, el error se quita; si no, no aparece mientras se escribe.
                it.copy(email = action.value, isEmailValid = valid, showEmailError = it.showEmailError && !valid, error = null)
                    .withCanSubmit()
            }
            LoginAction.EmailFocusLost -> _state.update {
                if (it.email.isNotBlank() && !it.isEmailValid) it.copy(showEmailError = true) else it
            }
            is LoginAction.PasswordEmptinessChanged -> {
                passwordIsEmpty = action.isEmpty
                _state.update { it.copy(showPasswordRequired = it.showPasswordRequired && action.isEmpty, error = null).withCanSubmit() }
            }
            is LoginAction.Submit -> submit(action.password)
        }
    }

    private var focusSequence = 0

    private fun submit(password: CharSequence) {
        val current = _state.value
        if (current.isLoading || current.isLockedOut) return
        // «Al tocar el botón» se marcan todos los campos con problema y el foco va al primero; no se manda nada.
        val emailBad = !current.isEmailValid
        val passwordEmpty = password.isEmpty()
        if (emailBad || passwordEmpty) {
            _state.update {
                it.copy(
                    showEmailError = emailBad,
                    showPasswordRequired = passwordEmpty,
                    error = null,
                    focusRequest = LoginFocusRequest(if (emailBad) LoginField.Email else LoginField.Password, ++focusSequence),
                ).withCanSubmit()
            }
            return
        }
        _state.update { it.copy(isLoading = true, error = null).withCanSubmit() }
        viewModelScope.launch {
            // OP-13/OP-14: prelogin, derivación (fuera del hilo principal) y login.
            val result = auth.login(current.email.trim(), password)
            _state.update { it.copy(isLoading = false).withCanSubmit() }
            when (result) {
                is ApiResult.Success -> _effects.send(
                    when (val step = result.value) {
                        AuthStep.VaultUnlocked -> LoginEffect.NavigateToVault
                        AuthStep.TwoFactorRequired -> LoginEffect.NavigateToTwoFactor
                        is AuthStep.EmailNotVerified -> LoginEffect.NavigateToVerifyEmail(step.email)
                        AuthStep.SignInAgain -> return@launch
                    },
                )
                is ApiResult.Failure -> when (val error = result.error) {
                    ApiError.InvalidCredentials -> _state.update { it.copy(error = LoginError.InvalidCredentials) }
                    is ApiError.RateLimited -> onLockedOut((error.retryAfterSeconds + 59) / 60)
                    else -> _state.update { it.copy(error = LoginError.Generic) }
                }
            }
        }
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
