package com.luis.end_security.feature.auth.ui.twofactor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luis.end_security.core.common.Countdown
import com.luis.end_security.core.designsystem.component.CodeFieldFilter
import com.luis.end_security.feature.auth.domain.SecondFactorResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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

@HiltViewModel
class TwoFactorViewModel @Inject constructor(
    timeSource: TimeSource,
) : ViewModel() {

    private val _state = MutableStateFlow(TwoFactorUiState())
    val state: StateFlow<TwoFactorUiState> = _state.asStateFlow()

    private val lockoutCountdown = Countdown(timeSource)

    fun onAction(action: TwoFactorAction) {
        when (action) {
            // Al escribir otra vez se quita «Código incorrecto».
            is TwoFactorAction.CodeChanged -> _state.update {
                it.copy(code = CodeFieldFilter.filter(action.value), wrongCode = false).withCanSubmit()
            }
            TwoFactorAction.Submit -> submit()
        }
    }

    private fun submit() {
        if (!_state.value.canSubmit) return
        // TODO(OP-15): enviar el código TOTP y llamar a onResult con la respuesta.
        //  Si se acepta, el destino es «Bóveda · cargando» (SCR-39).
    }

    /** Aplica la respuesta del servidor (OP-15). */
    internal fun onResult(result: SecondFactorResult) {
        when (result) {
            SecondFactorResult.Accepted -> Unit // TODO(OP-15): ir a SCR-39.
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
