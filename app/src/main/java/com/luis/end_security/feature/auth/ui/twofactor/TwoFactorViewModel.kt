package com.luis.end_security.feature.auth.ui.twofactor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luis.end_security.core.common.Countdown
import com.luis.end_security.core.designsystem.component.CodeFieldFilter
import com.luis.end_security.core.common.ApiError
import com.luis.end_security.core.common.ApiResult
import com.luis.end_security.feature.auth.domain.AuthRepository
import com.luis.end_security.feature.auth.domain.SecondFactorResult
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

/** Estado de la verificación en dos pasos al entrar (SCR-07). */
data class TwoFactorUiState(
    val code: String = "",
    val canSubmit: Boolean = false,
    /** «Código incorrecto. Revisa que la hora de tu teléfono sea automática.» */
    val wrongCode: Boolean = false,
    /** Segundos de bloqueo por demasiados intentos (DEC-32); 0 sin bloqueo. */
    val lockoutSecondsLeft: Int = 0,
    val isLoading: Boolean = false,
    /** «No pudimos conectar. Intenta de nuevo.» */
    val genericError: Boolean = false,
) {
    val isLockedOut: Boolean get() = lockoutSecondsLeft > 0

    // El código no va en logs ni en toString().
    override fun toString(): String =
        "TwoFactorUiState(digits=${code.length}, wrongCode=$wrongCode, lockoutSecondsLeft=$lockoutSecondsLeft)"
}

sealed interface TwoFactorAction {
    data class CodeChanged(val value: String) : TwoFactorAction {
        override fun toString(): String = "CodeChanged(***)"
    }

    /** «Continuar» o «Listo» en el teclado. */
    data object Submit : TwoFactorAction
}

sealed interface TwoFactorEffect {
    /** SCR-39: primera descarga de la bóveda. */
    data object NavigateToVault : TwoFactorEffect

    /** El reto venció o ya se usó: a Iniciar sesión. */
    data object NavigateToLogin : TwoFactorEffect
}

@HiltViewModel
class TwoFactorViewModel @Inject constructor(
    timeSource: TimeSource,
    private val auth: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(TwoFactorUiState())
    val state: StateFlow<TwoFactorUiState> = _state.asStateFlow()

    private val _effects = Channel<TwoFactorEffect>(Channel.BUFFERED)
    val effects: Flow<TwoFactorEffect> = _effects.receiveAsFlow()

    private val lockoutCountdown = Countdown(timeSource)

    fun onAction(action: TwoFactorAction) {
        when (action) {
            // Al escribir otra vez se quita «Código incorrecto».
            is TwoFactorAction.CodeChanged -> _state.update {
                it.copy(code = CodeFieldFilter.filter(action.value), wrongCode = false, genericError = false).withCanSubmit()
            }
            TwoFactorAction.Submit -> submit()
        }
    }

    private fun submit() {
        val current = _state.value
        if (!current.canSubmit) return
        _state.update { it.copy(isLoading = true, genericError = false).withCanSubmit() }
        viewModelScope.launch {
            // OP-15: el código TOTP con el reto del login; si pasa, se abre la bóveda.
            val result = auth.verifyTwoFactor(current.code)
            _state.update { it.copy(isLoading = false).withCanSubmit() }
            when (result) {
                is ApiResult.Success -> onResult(SecondFactorResult.Accepted)
                is ApiResult.Failure -> when (val error = result.error) {
                    is ApiError.InvalidCode -> onResult(SecondFactorResult.Rejected)
                    is ApiError.RateLimited -> onResult(SecondFactorResult.LockedOut((error.retryAfterSeconds + 59) / 60))
                    ApiError.ChallengeExpired -> _effects.send(TwoFactorEffect.NavigateToLogin)
                    else -> _state.update { it.copy(code = "", genericError = true).withCanSubmit() }
                }
            }
        }
    }

    /** Al salir del segundo paso (atrás o completado), la enc_key que esperaba se borra. */
    override fun onCleared() {
        auth.cancelPendingLogin()
    }

    /** Aplica la respuesta del servidor (OP-15). */
    internal fun onResult(result: SecondFactorResult) {
        when (result) {
            SecondFactorResult.Accepted -> viewModelScope.launch { _effects.send(TwoFactorEffect.NavigateToVault) }
            // Sin intentos restantes: DEC-32 bloquea al quinto. Las casillas se vacían.
            SecondFactorResult.Rejected -> _state.update { it.copy(code = "", wrongCode = true).withCanSubmit() }
            is SecondFactorResult.LockedOut -> {
                _state.update { it.copy(code = "", wrongCode = false) }
                lockoutCountdown.start(viewModelScope, result.minutes.minutes) { secondsLeft ->
                    _state.update { it.copy(lockoutSecondsLeft = secondsLeft).withCanSubmit() }
                }
            }
        }
    }

    private fun TwoFactorUiState.withCanSubmit(): TwoFactorUiState =
        copy(canSubmit = code.length == CodeFieldFilter.LENGTH && !isLockedOut && !isLoading)
}
