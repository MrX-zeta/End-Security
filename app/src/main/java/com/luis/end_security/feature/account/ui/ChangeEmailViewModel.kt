package com.luis.end_security.feature.account.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luis.end_security.core.common.Countdown
import com.luis.end_security.core.common.EmailNormalizer
import com.luis.end_security.core.designsystem.component.CodeFieldFilter
import com.luis.end_security.feature.account.domain.ChangeEmail
import com.luis.end_security.feature.account.domain.ConfirmEmailResult
import com.luis.end_security.feature.account.domain.RequestEmailResult
import com.luis.end_security.feature.auth.domain.DebugVerificationCodes
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
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

enum class EmailStep { Enter, Code }

/**
 * «Podrás cambiar tu correo de nuevo en N h / N min / N s» (Figma 227:4726). Se redondea hacia arriba para no prometer menos de lo
 * que falta, y solo cambia cuando cambia lo que se muestra (cada hora, cada minuto o cada segundo).
 */
sealed interface EmailWait {
    data class Hours(val count: Int) : EmailWait

    data class Minutes(val count: Int) : EmailWait

    data class Seconds(val count: Int) : EmailWait

    companion object {
        fun of(seconds: Int): EmailWait? = when {
            seconds <= 0 -> null
            seconds >= 3_600 -> Hours((seconds + 3_599) / 3_600)
            seconds >= 60 -> Minutes((seconds + 59) / 60)
            else -> Seconds(seconds)
        }
    }
}

sealed interface EmailCodeError {
    /** «Código incorrecto. Te quedan N intentos.» */
    data class WrongCode(val attemptsLeft: Int) : EmailCodeError

    /** «Este código ya no sirve. Pide uno nuevo.» (vencido o sin intentos) */
    data object Expired : EmailCodeError

    data object Generic : EmailCodeError

    /** «Demasiados intentos. Vuelve a intentarlo en N min.» */
    data class RateLimited(val minutes: Int) : EmailCodeError
}

/**
 * Estado de Correo (SCR de Figma 227:4534 y 227:4802). Ninguna contraseña vive aquí (regla 6): la de la hoja de reautenticación está
 * en un `TextFieldState` de la hoja. Los correos no son secretos, pero tampoco se imprimen en `toString()`.
 */
data class ChangeEmailUiState(
    val step: EmailStep = EmailStep.Enter,
    val currentEmail: String = "",
    val newEmail: String = "",
    /** `null` mientras no se sabe (sin red): el código de la app se pide solo con la 2FA activa. */
    val twoFactorRequired: Boolean? = null,
    val showFormatError: Boolean = false,
    val showSameAsCurrent: Boolean = false,
    /** Hasta cuándo no se puede pedir otro cambio (`429`); `null` si se puede. */
    val wait: EmailWait? = null,
    val genericError: Boolean = false,
    // --- Hoja de reautenticación ---
    val sheetOpen: Boolean = false,
    val sheetCode: String = "",
    val showPasswordRequired: Boolean = false,
    val showCodeIncomplete: Boolean = false,
    val credentialsWrong: Boolean = false,
    val sheetConflict: Boolean = false,
    val sheetGenericError: Boolean = false,
    val isRequesting: Boolean = false,
    val focusRequest: ReauthFocus? = null,
    // --- Código ---
    val code: String = "",
    val showCodeIncompleteInline: Boolean = false,
    val codeError: EmailCodeError? = null,
    val isConfirming: Boolean = false,
    val codeFocusRequest: Int = 0,
    /** Solo con el backend falso y en depuración: «Modo demostración · Código: N». */
    val debugCode: String? = null,
) {
    val canSubmitCode: Boolean
        get() = code.length == CodeFieldFilter.LENGTH && codeError != EmailCodeError.Expired && !isConfirming

    val isBusy: Boolean get() = isRequesting || isConfirming

    override fun toString(): String = "ChangeEmailUiState(step=$step, sheet=$sheetOpen, digits=${code.length}, wait=$wait, ***)"
}

sealed interface ChangeEmailAction {
    data class NewEmailChanged(val value: String) : ChangeEmailAction {
        override fun toString(): String = "NewEmailChanged(***)"
    }

    data object NewEmailFocusLost : ChangeEmailAction

    /** «Continuar»: valida y abre la hoja de reautenticación (aún no llama al servidor). */
    data object Continue : ChangeEmailAction

    data class SheetCodeChanged(val value: String) : ChangeEmailAction {
        override fun toString(): String = "SheetCodeChanged(***)"
    }

    /** La hoja avisa si el campo de contraseña quedó vacío o no; nunca manda su contenido. */
    data class PasswordEmptinessChanged(val isEmpty: Boolean) : ChangeEmailAction

    /** «Enviar código»: reautentica y llama a EP-15. [password] es el texto del campo; no se guarda. */
    class SendCode(val password: CharSequence) : ChangeEmailAction {
        override fun toString(): String = "SendCode(***)"
    }

    data object SheetDismissed : ChangeEmailAction

    data class CodeChanged(val value: String) : ChangeEmailAction {
        override fun toString(): String = "CodeChanged(***)"
    }

    /** «Confirmar correo». */
    data object ConfirmCode : ChangeEmailAction

    /** «Pide otro código»: vuelve al correo nuevo, ya escrito; pedir otro es una solicitud nueva (reautenticación y límite). */
    data object RequestAnotherCode : ChangeEmailAction

    /** Atrás: del código vuelve al correo nuevo (no cambia nada); del correo nuevo sale. */
    data object Back : ChangeEmailAction
}

sealed interface ChangeEmailEffect {
    /** Sale de la pantalla (Atrás desde el correo nuevo). */
    data object Leave : ChangeEmailEffect

    /** El correo cambió: vuelve a Ajustes con «Tu correo ahora es {correo}». */
    data class Changed(val email: String) : ChangeEmailEffect
}

@HiltViewModel
class ChangeEmailViewModel @Inject constructor(
    private val changeEmail: ChangeEmail,
    private val debugCodes: DebugVerificationCodes,
    timeSource: TimeSource,
) : ViewModel() {

    private val _state = MutableStateFlow(ChangeEmailUiState())
    val state: StateFlow<ChangeEmailUiState> = _state.asStateFlow()

    private val _effects = Channel<ChangeEmailEffect>(Channel.BUFFERED)
    val effects: Flow<ChangeEmailEffect> = _effects.receiveAsFlow()

    private var focusSequence = 0
    private val waitCountdown = Countdown(timeSource)

    init {
        viewModelScope.launch {
            val current = changeEmail.currentEmail().orEmpty()
            _state.update { it.copy(currentEmail = current) }
        }
        viewModelScope.launch {
            val enabled = changeEmail.twoFactorEnabled()
            _state.update { it.copy(twoFactorRequired = enabled) }
        }
    }

    fun onAction(action: ChangeEmailAction) {
        when (action) {
            is ChangeEmailAction.NewEmailChanged -> _state.update {
                it.copy(newEmail = action.value, showFormatError = false, showSameAsCurrent = false, genericError = false)
            }
            ChangeEmailAction.NewEmailFocusLost -> _state.update { validated(it, onlyIfTyped = true) }
            ChangeEmailAction.Continue -> continueToSheet()
            is ChangeEmailAction.SheetCodeChanged -> _state.update {
                val code = CodeFieldFilter.filter(action.value)
                it.copy(
                    sheetCode = code,
                    credentialsWrong = false,
                    sheetGenericError = false,
                    showCodeIncomplete = it.showCodeIncomplete && code.length < CodeFieldFilter.LENGTH,
                )
            }
            is ChangeEmailAction.PasswordEmptinessChanged -> _state.update {
                it.copy(
                    showPasswordRequired = it.showPasswordRequired && action.isEmpty,
                    credentialsWrong = false,
                    sheetGenericError = false,
                )
            }
            is ChangeEmailAction.SendCode -> sendCode(action.password)
            ChangeEmailAction.SheetDismissed -> _state.update { if (it.isRequesting) it else closedSheet(it) }
            is ChangeEmailAction.CodeChanged -> _state.update {
                val code = CodeFieldFilter.filter(action.value)
                // Al escribir otra vez se quita «Código incorrecto»; «ya no sirve» se queda hasta pedir otro.
                val error = if (it.codeError == EmailCodeError.Expired) it.codeError else null
                it.copy(code = code, codeError = error, showCodeIncompleteInline = it.showCodeIncompleteInline && code.length < CodeFieldFilter.LENGTH)
            }
            ChangeEmailAction.ConfirmCode -> confirmCode()
            ChangeEmailAction.RequestAnotherCode -> _state.update { backToEnter(it) }
            ChangeEmailAction.Back -> back()
        }
    }

    // --- Correo nuevo ---

    /** Formato y «igual al actual» (tras normalizar). Con [onlyIfTyped] no marca un campo vacío (salir sin escribir no es un error). */
    private fun validated(state: ChangeEmailUiState, onlyIfTyped: Boolean): ChangeEmailUiState {
        val typed = state.newEmail
        if (onlyIfTyped && typed.isBlank()) return state
        val normalized = EmailNormalizer.normalize(typed)
        val badFormat = !EmailFormat.isValid(normalized)
        val same = !badFormat && normalized == EmailNormalizer.normalize(state.currentEmail)
        return state.copy(showFormatError = badFormat, showSameAsCurrent = same)
    }

    private fun continueToSheet() {
        val before = _state.value
        if (before.isBusy || before.sheetOpen) return
        val checked = validated(before, onlyIfTyped = false)
        if (checked.showFormatError || checked.showSameAsCurrent) {
            // Con errores no se pide nada: el foco vuelve al campo (que está marcado).
            _state.update { checked.copy(genericError = false, codeFocusRequest = it.codeFocusRequest + 1) }
            return
        }
        // Durante la espera de DEC-63 el botón no llama al servidor: el mensaje de espera ya está a la vista.
        if (before.wait != null) {
            _state.update { checked }
            return
        }
        viewModelScope.launch {
            var required = _state.value.twoFactorRequired
            if (required == null) {
                required = changeEmail.twoFactorEnabled()
                if (required == null) {
                    _state.update { it.copy(genericError = true) }
                    return@launch
                }
                _state.update { it.copy(twoFactorRequired = required) }
            }
            _state.update { checked.copy(genericError = false, sheetOpen = true, twoFactorRequired = required) }
        }
    }

    private fun closedSheet(state: ChangeEmailUiState) = state.copy(
        sheetOpen = false,
        sheetCode = "",
        showPasswordRequired = false,
        showCodeIncomplete = false,
        credentialsWrong = false,
        sheetConflict = false,
        sheetGenericError = false,
    )

    /** OP-26, paso 1: reautentica (la `auth_key` se deriva en el teléfono) y llama a EP-15 con el correo **normalizado**. */
    private fun sendCode(password: CharSequence) {
        val current = _state.value
        if (!current.sheetOpen || current.isRequesting) return
        val passwordEmpty = password.isEmpty()
        val codeBad = current.twoFactorRequired == true && current.sheetCode.length != CodeFieldFilter.LENGTH
        if (passwordEmpty || codeBad) {
            _state.update {
                it.copy(
                    showPasswordRequired = passwordEmpty,
                    showCodeIncomplete = codeBad,
                    credentialsWrong = false,
                    focusRequest = ReauthFocus(if (passwordEmpty) ReauthField.Password else ReauthField.Code, ++focusSequence),
                )
            }
            return
        }
        _state.update { it.copy(isRequesting = true, credentialsWrong = false, sheetConflict = false, sheetGenericError = false) }
        val newEmail = EmailNormalizer.normalize(current.newEmail)
        val totp = if (current.twoFactorRequired == true) current.sheetCode else null
        viewModelScope.launch {
            when (val result = changeEmail.request(newEmail, password, totp)) {
                RequestEmailResult.Accepted -> {
                    _state.update {
                        closedSheet(it).copy(isRequesting = false, step = EmailStep.Code, newEmail = newEmail, code = "", codeError = null,
                            showCodeIncompleteInline = false, debugCode = debugCodes.latestCode(newEmail))
                    }
                }
                RequestEmailResult.WrongCredentials ->
                    _state.update { it.copy(isRequesting = false, credentialsWrong = true, sheetCode = "") }
                RequestEmailResult.KeyVersionConflict -> _state.update { it.copy(isRequesting = false, sheetConflict = true) }
                is RequestEmailResult.RateLimited -> {
                    startWait(result.retryAfterSeconds)
                    _state.update { closedSheet(it).copy(isRequesting = false) }
                }
                RequestEmailResult.SameAsCurrent -> _state.update { closedSheet(it).copy(isRequesting = false, showSameAsCurrent = true) }
                RequestEmailResult.Generic -> _state.update { it.copy(isRequesting = false, sheetGenericError = true) }
            }
        }
    }

    /** La cuenta regresiva solo cambia el estado cuando cambia lo que se muestra (hora, minuto o segundo). */
    private fun startWait(seconds: Int) {
        waitCountdown.start(viewModelScope, seconds.seconds) { left ->
            val display = EmailWait.of(left)
            _state.update { if (it.wait == display) it else it.copy(wait = display) }
        }
    }

    // --- Código ---

    private fun confirmCode() {
        val current = _state.value
        if (current.isConfirming || current.codeError == EmailCodeError.Expired) return
        if (current.code.length != CodeFieldFilter.LENGTH) {
            _state.update { it.copy(showCodeIncompleteInline = true, codeFocusRequest = it.codeFocusRequest + 1) }
            return
        }
        _state.update { it.copy(isConfirming = true) }
        viewModelScope.launch {
            when (val result = changeEmail.confirm(current.code)) {
                is ConfirmEmailResult.Changed -> {
                    _state.update { it.copy(isConfirming = false) }
                    _effects.send(ChangeEmailEffect.Changed(result.email))
                }
                is ConfirmEmailResult.WrongCode ->
                    _state.update {
                        it.copy(
                            isConfirming = false,
                            code = "",
                            codeError = if (result.attemptsLeft <= 0) EmailCodeError.Expired else EmailCodeError.WrongCode(result.attemptsLeft),
                        )
                    }
                ConfirmEmailResult.Expired -> _state.update { it.copy(isConfirming = false, code = "", codeError = EmailCodeError.Expired) }
                is ConfirmEmailResult.RateLimited ->
                    _state.update { it.copy(isConfirming = false, code = "", codeError = EmailCodeError.RateLimited((result.retryAfterSeconds + 59) / 60)) }
                ConfirmEmailResult.Generic -> _state.update { it.copy(isConfirming = false, codeError = EmailCodeError.Generic) }
            }
        }
    }

    /** Vuelve al correo nuevo **con el correo ya escrito**; no se cambia nada del lado del servidor ni de Room. */
    private fun backToEnter(state: ChangeEmailUiState) = state.copy(
        step = EmailStep.Enter,
        code = "",
        codeError = null,
        showCodeIncompleteInline = false,
        isConfirming = false,
        debugCode = null,
    )

    private fun back() {
        val current = _state.value
        if (current.isBusy) return
        when {
            current.sheetOpen -> _state.update { closedSheet(it) }
            current.step == EmailStep.Code -> _state.update { backToEnter(it) }
            else -> _effects.trySend(ChangeEmailEffect.Leave)
        }
    }

    override fun onCleared() {
        waitCountdown.cancel()
    }
}
