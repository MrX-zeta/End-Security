package com.luis.end_security.feature.auth.ui.verify

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luis.end_security.core.common.Countdown
import com.luis.end_security.core.designsystem.component.CodeFieldFilter
import com.luis.end_security.core.common.ApiError
import com.luis.end_security.core.common.ApiResult
import com.luis.end_security.feature.auth.domain.AuthRepository
import com.luis.end_security.feature.auth.domain.AuthStep
import com.luis.end_security.feature.auth.domain.DebugVerificationCodes
import com.luis.end_security.feature.auth.domain.EmailVerificationResult
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
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

@HiltViewModel
class VerifyEmailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    // Inyectada para probar la cuenta regresiva con el reloj virtual de TestScope.
    private val timeSource: TimeSource,
    private val auth: AuthRepository,
    private val debugCodes: DebugVerificationCodes,
) : ViewModel() {

    private val _state = MutableStateFlow(
        VerifyEmailUiState(email = savedStateHandle.get<String>(EMAIL_ARG).orEmpty()),
    )
    val state: StateFlow<VerifyEmailUiState> = _state.asStateFlow()

    private val _effects = Channel<VerifyEmailEffect>(Channel.BUFFERED)
    val effects: Flow<VerifyEmailEffect> = _effects.receiveAsFlow()

    private val resendCountdown = Countdown(timeSource)

    init {
        startResendCountdown()
        refreshDebugCode()
    }

    fun onAction(action: VerifyEmailAction) {
        when (action) {
            is VerifyEmailAction.CodeChanged -> _state.update {
                val code = CodeFieldFilter.filter(action.value)
                // Al escribir otra vez se quita «Código incorrecto»; «ya no sirve» se queda hasta reenviar.
                val error = if (it.error == VerifyEmailError.Expired) it.error else null
                it.copy(code = code, error = error).withCanSubmit()
            }
            VerifyEmailAction.Submit -> submit()
            VerifyEmailAction.Resend -> resend()
        }
    }

    private fun submit() {
        val current = _state.value
        if (!current.canSubmit) return
        _state.update { it.copy(isLoading = true).withCanSubmit() }
        viewModelScope.launch {
            // OP-12: verifica, guarda la sesión y abre la bóveda con la llave que espera en memoria.
            val result = auth.verifyEmail(current.email, current.code)
            _state.update { it.copy(isLoading = false).withCanSubmit() }
            when (result) {
                is ApiResult.Success -> _effects.send(
                    if (result.value == AuthStep.VaultUnlocked) VerifyEmailEffect.NavigateToVault else VerifyEmailEffect.NavigateToLogin,
                )
                is ApiResult.Failure -> when (val error = result.error) {
                    is ApiError.InvalidCode ->
                        onVerificationResult(EmailVerificationResult.WrongCode(error.attemptsRemaining ?: 0))
                    ApiError.ChallengeExpired -> onVerificationResult(EmailVerificationResult.Expired)
                    else -> showError(error)
                }
            }
        }
    }

    private fun resend() {
        if (!_state.value.canResend) return
        _state.update { it.copy(code = "", error = null).withCanSubmit() }
        startResendCountdown()
        viewModelScope.launch {
            val result = auth.resendVerification(_state.value.email)
            if (result is ApiResult.Failure) showError(result.error)
            refreshDebugCode()
        }
    }

    private fun showError(error: ApiError) {
        val shown = if (error is ApiError.RateLimited) {
            VerifyEmailError.RateLimited((error.retryAfterSeconds + 59) / 60)
        } else {
            VerifyEmailError.Generic
        }
        _state.update { it.copy(error = shown).withCanSubmit() }
    }

    private fun refreshDebugCode() {
        _state.update { it.copy(debugCode = debugCodes.latestCode(it.email)) }
    }

    /** Aplica la respuesta del servidor a la verificación (OP-12). */
    internal fun onVerificationResult(result: EmailVerificationResult) {
        when (result) {
            EmailVerificationResult.Verified -> Unit
            is EmailVerificationResult.WrongCode ->
                if (result.attemptsLeft <= 0) {
                    markExpired()
                } else {
                    _state.update {
                        it.copy(code = "", error = VerifyEmailError.WrongCode(result.attemptsLeft)).withCanSubmit()
                    }
                }
            EmailVerificationResult.Expired -> markExpired()
        }
    }

    private fun markExpired() {
        _state.update { it.copy(code = "", error = VerifyEmailError.Expired).withCanSubmit() }
    }

    private fun startResendCountdown() {
        resendCountdown.start(viewModelScope, RESEND_WAIT) { secondsLeft ->
            _state.update { it.copy(resendSecondsLeft = secondsLeft) }
        }
    }

    private fun VerifyEmailUiState.withCanSubmit(): VerifyEmailUiState = copy(
        canSubmit = code.length == CodeFieldFilter.LENGTH && error != VerifyEmailError.Expired && !isLoading,
    )

    /** Al salir de Verificar correo (atrás o completado), la llave que esperaba se borra (DEC-27). */
    override fun onCleared() {
        auth.cancelPendingLogin()
    }

    companion object {
        const val EMAIL_ARG = "email"
        private val RESEND_WAIT = VerifyEmailUiState.RESEND_WAIT_SECONDS.seconds
    }
}
