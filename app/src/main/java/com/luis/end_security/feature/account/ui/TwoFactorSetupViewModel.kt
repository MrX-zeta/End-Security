package com.luis.end_security.feature.account.ui

import androidx.lifecycle.ViewModel
import com.luis.end_security.R
import com.luis.end_security.core.common.AppMessage
import com.luis.end_security.core.common.AppMessages
import androidx.lifecycle.viewModelScope
import com.luis.end_security.core.common.ApiError
import com.luis.end_security.core.common.ApiResult
import com.luis.end_security.core.common.Countdown
import com.luis.end_security.core.common.DeviceClock
import com.luis.end_security.core.designsystem.component.CodeFieldFilter
import com.luis.end_security.core.security.SecretClipboard
import com.luis.end_security.feature.account.domain.OtpAuthUri
import com.luis.end_security.feature.account.domain.TwoFactorRepository
import com.luis.end_security.feature.account.domain.TwoFactorSetup
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** Los tres pasos de activar la verificación en dos pasos (SCR-30, SCR-31, SCR-32). */
enum class SetupStep { Key, Code, Codes }

/**
 * Estado del flujo de activación. La clave y los códigos de respaldo son secretos: `toString()` no los imprime y viven
 * solo aquí (nada en `SavedStateHandle`, logs ni argumentos de navegación).
 */
data class TwoFactorSetupUiState(
    val step: SetupStep = SetupStep.Key,
    /** Pidiendo la clave (EP-19): SCR-43. */
    val isLoadingKey: Boolean = false,
    /** La clave en grupos de 4, dos grupos por línea; `null` mientras carga o si falló. */
    val keyDisplay: String? = null,
    /** Hay una app de autenticación que abre `otpauth://` (lo dice la pantalla). */
    val canOpenAuthenticator: Boolean = false,
    /** No se pudo pedir la clave (sin red o error). */
    val keyError: Boolean = false,
    val code: String = "",
    val showIncomplete: Boolean = false,
    val wrongCode: Boolean = false,
    val genericError: Boolean = false,
    val lockoutSecondsLeft: Int = 0,
    val isConfirming: Boolean = false,
    /** Cambia cada vez que la pantalla debe dar el foco al campo del código. */
    val focusRequest: Int = 0,
    /** «El código cambia en N s». */
    val secondsLeft: Int = 0,
    val codes: List<String> = emptyList(),
    /** Segundos tras los que se borra lo copiado; `null` si no se copió nada. */
    val copiedSeconds: Int? = null,
) {
    val isLockedOut: Boolean get() = lockoutSecondsLeft > 0

    override fun toString(): String =
        "TwoFactorSetupUiState(step=$step, loading=$isLoadingKey, digits=${code.length}, codes=${codes.size}, ***)"
}

sealed interface SetupAction {
    data object Continue : SetupAction
    data object CopyKey : SetupAction
    data object OpenAuthenticator : SetupAction
    data class CodeChanged(val value: String) : SetupAction {
        override fun toString(): String = "CodeChanged(***)"
    }

    data object Confirm : SetupAction
    data object CopyCodes : SetupAction

    /** «Ya los guardé» o Atrás del sistema en el Paso 3: sale del flujo. */
    data object Finish : SetupAction

    /** Se salió del Paso 3 por una pestaña (tras confirmar): lo mismo que «Ya los guardé», sin el efecto de salir (la pestaña ya navega). */
    data object LeaveForTab : SetupAction

    /** Atrás en el Paso 2 vuelve al Paso 1; en el Paso 1 sale. */
    data object Back : SetupAction
    data object Retry : SetupAction
    data class AuthenticatorAvailable(val available: Boolean) : SetupAction
}

sealed interface SetupEffect {
    /** Sale del flujo completo y vuelve a Ajustes. */
    data object Exit : SetupEffect

    /** La pantalla abre este URI con `ACTION_VIEW`; contiene el secreto, no se imprime. */
    class OpenUri(val uri: String) : SetupEffect {
        override fun toString(): String = "OpenUri(***)"
    }
}

@HiltViewModel
class TwoFactorSetupViewModel @Inject constructor(
    private val repository: TwoFactorRepository,
    private val clipboard: SecretClipboard,
    private val clock: DeviceClock,
    timeSource: TimeSource,
    private val appMessages: AppMessages,
) : ViewModel() {

    private val _state = MutableStateFlow(TwoFactorSetupUiState(isLoadingKey = true))
    val state: StateFlow<TwoFactorSetupUiState> = _state.asStateFlow()

    private val _effects = Channel<SetupEffect>(Channel.BUFFERED)
    val effects: Flow<SetupEffect> = _effects.receiveAsFlow()

    private var setup: TwoFactorSetup? = null
    private var recoveryCodes: List<String> = emptyList()
    private var ticker: Job? = null
    private var copiedJob: Job? = null
    private val lockoutCountdown = Countdown(timeSource)

    init {
        // Cada entrada pide una clave nueva: si se salió antes de confirmar, la anterior ya no sirve.
        loadKey()
    }

    fun onAction(action: SetupAction) {
        when (action) {
            SetupAction.Continue -> goToCode()
            SetupAction.CopyKey -> copy(setup?.secret)
            SetupAction.OpenAuthenticator -> setup?.let { _effects.trySend(SetupEffect.OpenUri(OtpAuthUri.build(it))) }
            is SetupAction.CodeChanged -> _state.update {
                val code = CodeFieldFilter.filter(action.value)
                it.copy(
                    code = code,
                    wrongCode = false,
                    genericError = false,
                    showIncomplete = it.showIncomplete && code.length < CodeFieldFilter.LENGTH,
                )
            }
            SetupAction.Confirm -> confirm()
            SetupAction.CopyCodes -> copy(recoveryCodes.joinToString("\n"))
            SetupAction.Finish -> {
                announceEnabledIfDone()
                clearSecrets()
                _effects.trySend(SetupEffect.Exit)
            }
            SetupAction.LeaveForTab -> {
                announceEnabledIfDone()
                clearSecrets()
            }
            SetupAction.Back -> when (_state.value.step) {
                SetupStep.Key -> onAction(SetupAction.Finish)
                SetupStep.Code -> {
                    ticker?.cancel()
                    _state.update { it.copy(step = SetupStep.Key, code = "", showIncomplete = false, wrongCode = false, genericError = false) }
                }
                // Atrás en el Paso 3 hace lo mismo que «Ya los guardé»: nunca regresa al Paso 2.
                SetupStep.Codes -> onAction(SetupAction.Finish)
            }
            SetupAction.Retry -> loadKey()
            is SetupAction.AuthenticatorAvailable -> _state.update { it.copy(canOpenAuthenticator = action.available) }
        }
    }

    /** OP-22: pide la clave pendiente (EP-19). */
    private fun loadKey() {
        _state.update { it.copy(isLoadingKey = true, keyError = false, keyDisplay = null) }
        viewModelScope.launch {
            when (val result = repository.startSetup()) {
                is ApiResult.Success -> {
                    setup = result.value
                    _state.update { it.copy(isLoadingKey = false, keyDisplay = groupKey(result.value.secret)) }
                }
                is ApiResult.Failure -> _state.update { it.copy(isLoadingKey = false, keyError = true) }
            }
        }
    }

    private fun goToCode() {
        val current = setup ?: return
        _state.update { it.copy(step = SetupStep.Code, secondsLeft = secondsLeft(current.periodSeconds)) }
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (isActive) {
                delay(1_000)
                _state.update { it.copy(secondsLeft = secondsLeft(current.periodSeconds)) }
            }
        }
    }

    /** Lo que falta para que la app de autenticación cambie de código, con el reloj del teléfono. */
    private fun secondsLeft(period: Int): Int = period - ((clock.wallMillis() / 1_000) % period).toInt()

    /** OP-23: confirma con el primer código (EP-20) y recibe los códigos de respaldo. */
    private fun confirm() {
        val current = _state.value
        if (current.isConfirming || current.isLockedOut) return
        if (current.code.length != CodeFieldFilter.LENGTH) {
            // Incompleto: no se manda nada; se marca el campo y se le da el foco.
            _state.update { it.copy(showIncomplete = true, focusRequest = it.focusRequest + 1) }
            return
        }
        _state.update { it.copy(isConfirming = true, genericError = false) }
        viewModelScope.launch {
            when (val result = repository.confirm(current.code)) {
                is ApiResult.Success -> {
                    recoveryCodes = result.value
                    ticker?.cancel()
                    // La clave ya no hace falta: se suelta.
                    setup = null
                    _state.update {
                        it.copy(isConfirming = false, step = SetupStep.Codes, codes = result.value, code = "", keyDisplay = null)
                    }
                }
                is ApiResult.Failure -> {
                    _state.update { it.copy(isConfirming = false, code = "") }
                    onConfirmError(result.error)
                }
            }
        }
    }

    private fun onConfirmError(error: ApiError) {
        when (error) {
            is ApiError.InvalidCode -> _state.update { it.copy(wrongCode = true) }
            is ApiError.RateLimited -> lockoutCountdown.start(viewModelScope, error.retryAfterSeconds.seconds) { left ->
                _state.update { it.copy(lockoutSecondsLeft = left) }
            }
            else -> _state.update { it.copy(genericError = true) }
        }
    }

    private fun copy(text: String?) {
        if (text.isNullOrEmpty()) return
        copiedJob?.cancel()
        copiedJob = viewModelScope.launch {
            val seconds = clipboard.copy(text)
            _state.update { it.copy(copiedSeconds = seconds) }
            delay(3_000)
            _state.update { it.copy(copiedSeconds = null) }
        }
    }

    private fun groupKey(secret: String): String =
        secret.chunked(4).chunked(2).joinToString("\n") { it.joinToString(" ") }

    /**
     * Llegar a los códigos de respaldo significa que el código se confirmó y la verificación ya está activa: se avisa al volver a
     * Ajustes (antes regresaba sin decir nada). Salir en el Paso 1 o 2 no activó nada y no avisa.
     */
    private fun announceEnabledIfDone() {
        if (_state.value.step == SetupStep.Codes) {
            appMessages.post(AppMessage(R.string.msg_two_factor_enabled, icon = R.drawable.ic_check_16))
        }
    }

    private fun clearSecrets() {
        setup = null
        recoveryCodes = emptyList()
        _state.update { it.copy(keyDisplay = null, codes = emptyList(), code = "") }
    }

    /** Al salir del flujo (o bloquear la bóveda: la ruta sale de composición) se sueltan la clave y los códigos. */
    override fun onCleared() {
        ticker?.cancel()
        lockoutCountdown.cancel()
        clearSecrets()
    }
}
