package com.luis.end_security.feature.auth.ui.verify

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luis.end_security.core.designsystem.component.CodeFieldFilter
import com.luis.end_security.feature.auth.domain.EmailVerificationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.ceil
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

@HiltViewModel
class VerifyEmailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    // Inyectada para probar la cuenta regresiva con el reloj virtual de TestScope.
    private val timeSource: TimeSource,
) : ViewModel() {

    private val _state = MutableStateFlow(
        VerifyEmailUiState(email = savedStateHandle.get<String>(EMAIL_ARG).orEmpty()),
    )
    val state: StateFlow<VerifyEmailUiState> = _state.asStateFlow()

    private var countdownJob: Job? = null

    init {
        startResendCountdown()
    }

    fun onAction(action: VerifyEmailAction) {
        when (action) {
            is VerifyEmailAction.CodeChanged -> _state.update {
                val code = CodeFieldFilter.filter(action.value)
                // Al escribir otra vez se quita «Código incorrecto»; «ya no sirve» se queda hasta reenviar.
                val error = if (it.error is VerifyEmailError.WrongCode) null else it.error
                it.copy(code = code, error = error).withCanSubmit()
            }
            VerifyEmailAction.Submit -> submit()
            VerifyEmailAction.Resend -> resend()
        }
    }

    private fun submit() {
        if (!_state.value.canSubmit) return
        // TODO(OP-12): enviar el código y llamar a onVerificationResult con la respuesta.
    }

    private fun resend() {
        if (!_state.value.canResend) return
        _state.update { it.copy(code = "", error = null).withCanSubmit() }
        startResendCountdown()
        // TODO(OP-11 reenvío): pedir un código nuevo al servidor.
    }

    /** Aplica la respuesta del servidor a la verificación (OP-12). */
    internal fun onVerificationResult(result: EmailVerificationResult) {
        when (result) {
            EmailVerificationResult.Verified -> Unit // TODO(OP-12): continuar al siguiente paso.
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
        countdownJob?.cancel()
        val deadline = timeSource.markNow() + RESEND_WAIT
        countdownJob = viewModelScope.launch {
            while (true) {
                // Antes del plazo, elapsedNow() es negativo: lo que falta es su opuesto.
                val left = (-deadline.elapsedNow()).coerceAtLeast(Duration.ZERO)
                val secondsLeft = ceil(left / 1.seconds).toInt()
                _state.update { it.copy(resendSecondsLeft = secondsLeft) }
                if (secondsLeft == 0) break
                // Espera justo hasta que cambie el número que se muestra.
                delay(left - (secondsLeft - 1).seconds)
            }
        }
    }

    private fun VerifyEmailUiState.withCanSubmit(): VerifyEmailUiState = copy(
        canSubmit = code.length == CodeFieldFilter.LENGTH && error != VerifyEmailError.Expired && !isLoading,
    )

    companion object {
        const val EMAIL_ARG = "email"
        private val RESEND_WAIT = VerifyEmailUiState.RESEND_WAIT_SECONDS.seconds
    }
}
