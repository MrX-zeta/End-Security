package com.luis.end_security.feature.auth.ui.backupcode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luis.end_security.core.common.Countdown
import com.luis.end_security.feature.auth.domain.BackupCodeFormat
import com.luis.end_security.feature.auth.domain.SecondFactorResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource

/**
 * Estado del código de respaldo (SCR-08). El código es un secreto de larga vida hasta que se usa:
 * no está aquí; vive en el TextFieldState de la pantalla y el ViewModel solo sabe su largo.
 */
data class BackupCodeUiState(
    val canSubmit: Boolean = false,
    /** «Ese código no es válido o ya se usó.» */
    val invalidCode: Boolean = false,
    /** Segundos de bloqueo por demasiados intentos (DEC-32); 0 sin bloqueo. */
    val lockoutSecondsLeft: Int = 0,
    val isLoading: Boolean = false,
) {
    val isLockedOut: Boolean get() = lockoutSecondsLeft > 0
}

sealed interface BackupCodeAction {
    /** Cuántos caracteres válidos hay en el campo; nunca su contenido. */
    data class CodeLengthChanged(val length: Int) : BackupCodeAction

    /**
     * «Entrar» o «Listo» en el teclado, con el código de paso para OP-16; el ViewModel no lo guarda.
     * No es `data class` para que su `toString()` no lo imprima.
     */
    class Submit(val code: CharSequence) : BackupCodeAction {
        override fun toString(): String = "Submit(***)"
    }
}

@HiltViewModel
class BackupCodeViewModel @Inject constructor(
    timeSource: TimeSource,
) : ViewModel() {

    private val _state = MutableStateFlow(BackupCodeUiState())
    val state: StateFlow<BackupCodeUiState> = _state.asStateFlow()

    private val _clearRequests = MutableStateFlow(0)

    /** Cambia cada vez que la pantalla debe vaciar el campo. */
    val clearRequests: StateFlow<Int> = _clearRequests.asStateFlow()

    private var codeLength = 0
    private val lockoutCountdown = Countdown(timeSource)

    fun onAction(action: BackupCodeAction) {
        when (action) {
            is BackupCodeAction.CodeLengthChanged -> {
                codeLength = action.length
                // Al escribir otra vez se quita el error; vaciar el campo no lo quita.
                _state.update { it.copy(invalidCode = it.invalidCode && action.length == 0).withCanSubmit() }
            }
            is BackupCodeAction.Submit -> submit(action.code)
        }
    }

    private fun submit(code: CharSequence) {
        // Se vuelve a validar aquí: un cambio programático del campo se saltaría el filtro.
        if (!_state.value.canSubmit || !BackupCodeFormat.isValid(code)) return
        // TODO(OP-16): poner isLoading = true antes de enviar e ignorar envíos mientras haya uno
        //  activo (un doble toque contaría como intento fallido hacia el bloqueo de DEC-32);
        //  enviar el código y llamar a onResult con la respuesta.
    }

    /** Aplica la respuesta del servidor (OP-16). */
    internal fun onResult(result: SecondFactorResult) {
        when (result) {
            SecondFactorResult.Accepted -> Unit // TODO(OP-16): ir a la bóveda (SCR-39).
            SecondFactorResult.Rejected -> {
                _state.update { it.copy(invalidCode = true).withCanSubmit() }
                _clearRequests.update { it + 1 }
            }
            is SecondFactorResult.LockedOut -> {
                _state.update { it.copy(invalidCode = false) }
                _clearRequests.update { it + 1 }
                lockoutCountdown.start(viewModelScope, result.minutes.minutes) { secondsLeft ->
                    _state.update { it.copy(lockoutSecondsLeft = secondsLeft).withCanSubmit() }
                }
            }
        }
    }

    private fun BackupCodeUiState.withCanSubmit(): BackupCodeUiState =
        copy(canSubmit = codeLength == BackupCodeFormat.LENGTH && !isLockedOut && !isLoading)
}
