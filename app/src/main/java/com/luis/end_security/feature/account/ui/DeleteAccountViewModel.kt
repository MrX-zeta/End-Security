package com.luis.end_security.feature.account.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luis.end_security.core.common.Countdown
import com.luis.end_security.core.designsystem.component.CodeFieldFilter
import com.luis.end_security.feature.account.domain.DeleteAccount
import com.luis.end_security.feature.account.domain.DeleteAccountResult
import com.luis.end_security.feature.account.domain.TwoFactorRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

enum class DeleteField { Password, Code }

/** [id] cambia en cada intento, para que dos toques seguidos con el mismo error vuelvan a dar el foco. */
data class DeleteFocus(val field: DeleteField, val id: Int)

/**
 * Estado de Eliminar cuenta (SCR-36) y de su hoja de confirmación (SCR-37). La contraseña maestra **no** está aquí: vive en un
 * `TextFieldState` de la pantalla (regla 6).
 */
data class DeleteAccountUiState(
    /** `null` mientras no se sabe (sin red): el código de la app se pide solo con la 2FA activa. */
    val twoFactorRequired: Boolean? = null,
    val code: String = "",
    val showPasswordRequired: Boolean = false,
    val showCodeIncomplete: Boolean = false,
    /** `reauth_required`: «Contraseña o código incorrectos.» */
    val credentialsWrong: Boolean = false,
    /** La contraseña cambió en otro dispositivo (la `key_version` del servidor es mayor). */
    val conflict: Boolean = false,
    val genericError: Boolean = false,
    val lockoutSecondsLeft: Int = 0,
    /** Elementos que se borrarán; no es `null` solo mientras la hoja de confirmación está abierta. */
    val confirmCount: Int? = null,
    /** Error genérico dentro de la hoja (sin red o 5xx): no se tocó nada local. */
    val sheetError: Boolean = false,
    /** Derivando la `auth_key`, llamando a EP-18 y limpiando: el botón carga, la hoja no se cierra y Atrás está deshabilitado. */
    val isLoading: Boolean = false,
    val focusRequest: DeleteFocus? = null,
) {
    val isLockedOut: Boolean get() = lockoutSecondsLeft > 0

    val sheetOpen: Boolean get() = confirmCount != null

    override fun toString(): String = "DeleteAccountUiState(loading=$isLoading, sheet=$sheetOpen, digits=${code.length}, ***)"
}

sealed interface DeleteAccountAction {
    /** La pantalla avisa si «Contraseña maestra» quedó vacía o no; nunca manda su contenido. */
    data class PasswordEmptinessChanged(val isEmpty: Boolean) : DeleteAccountAction

    data class CodeChanged(val value: String) : DeleteAccountAction {
        override fun toString(): String = "CodeChanged(***)"
    }

    /** «Eliminar mi cuenta»: valida y abre la hoja; **no** llama al servidor. */
    class Submit(val password: CharSequence) : DeleteAccountAction {
        override fun toString(): String = "Submit(***)"
    }

    /** «Sí, eliminar mi cuenta» en la hoja. [password] es el texto del campo; no se guarda. */
    class Confirm(val password: CharSequence) : DeleteAccountAction {
        override fun toString(): String = "Confirm(***)"
    }

    data object SheetDismissed : DeleteAccountAction
}

@HiltViewModel
class DeleteAccountViewModel @Inject constructor(
    private val deleteAccount: DeleteAccount,
    private val twoFactor: TwoFactorRepository,
    timeSource: TimeSource,
) : ViewModel() {

    private val _state = MutableStateFlow(DeleteAccountUiState())
    val state: StateFlow<DeleteAccountUiState> = _state.asStateFlow()

    private var focusSequence = 0
    private val lockoutCountdown = Countdown(timeSource)

    init {
        viewModelScope.launch {
            val enabled = twoFactor.isEnabled()
            _state.update { it.copy(twoFactorRequired = enabled) }
        }
    }

    fun onAction(action: DeleteAccountAction) {
        when (action) {
            is DeleteAccountAction.PasswordEmptinessChanged -> _state.update {
                it.copy(
                    showPasswordRequired = it.showPasswordRequired && action.isEmpty,
                    credentialsWrong = false,
                    conflict = false,
                    genericError = false,
                )
            }
            is DeleteAccountAction.CodeChanged -> _state.update {
                val code = CodeFieldFilter.filter(action.value)
                it.copy(
                    code = code,
                    credentialsWrong = false,
                    genericError = false,
                    showCodeIncomplete = it.showCodeIncomplete && code.length < CodeFieldFilter.LENGTH,
                )
            }
            is DeleteAccountAction.Submit -> submit(action.password)
            is DeleteAccountAction.Confirm -> confirm(action.password)
            DeleteAccountAction.SheetDismissed -> _state.update { if (it.isLoading) it else it.copy(confirmCount = null, sheetError = false) }
        }
    }

    private fun focusOn(field: DeleteField) = DeleteFocus(field, ++focusSequence)

    /**
     * «Eliminar mi cuenta»: marca los errores de la pantalla (contraseña vacía, código incompleto) con el foco en el primero. Con
     * todo bien **no** llama a EP-18: cuenta los elementos y abre la hoja de confirmación.
     */
    private fun submit(password: CharSequence) {
        val before = _state.value
        if (before.isLoading || before.isLockedOut || before.sheetOpen) return
        val passwordEmpty = password.isEmpty()
        val codeBad = before.twoFactorRequired == true && before.code.length != CodeFieldFilter.LENGTH
        val first = when {
            passwordEmpty -> DeleteField.Password
            codeBad -> DeleteField.Code
            else -> null
        }
        _state.update {
            it.copy(
                showPasswordRequired = passwordEmpty,
                showCodeIncomplete = codeBad,
                credentialsWrong = false,
                conflict = false,
                genericError = false,
                focusRequest = if (first != null) focusOn(first) else it.focusRequest,
            )
        }
        if (first != null) return
        viewModelScope.launch {
            // ¿Hace falta el código? Sin red se vuelve a preguntar aquí; si sigue sin saberse, no se puede eliminar.
            var required = _state.value.twoFactorRequired
            if (required == null) {
                required = twoFactor.isEnabled()
                if (required == null) {
                    _state.update { it.copy(genericError = true) }
                    return@launch
                }
                _state.update { it.copy(twoFactorRequired = required) }
                if (required) {
                    _state.update { it.copy(showCodeIncomplete = true, focusRequest = focusOn(DeleteField.Code)) }
                    return@launch
                }
            }
            val count = deleteAccount.itemCount()
            _state.update { it.copy(confirmCount = count, sheetError = false) }
        }
    }

    /**
     * «Sí, eliminar mi cuenta»: reautentica y llama a EP-18; con el 204 limpia el teléfono (todo en `NonCancellable`, dentro de
     * [DeleteAccount]). La navegación a «Cuenta eliminada» no la hace esta pantalla: sale de la composición cuando la limpieza
     * bloquea la bóveda, así que la avisa `AccountDeletionGate` a `AppNavHost`.
     */
    private fun confirm(password: CharSequence) {
        val before = _state.value
        if (before.isLoading || !before.sheetOpen) return
        val totp = if (before.twoFactorRequired == true) before.code else null
        _state.update { it.copy(isLoading = true, sheetError = false) }
        viewModelScope.launch {
            val result = try {
                deleteAccount.delete(password, totp)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                DeleteAccountResult.Generic
            }
            handle(result)
        }
    }

    private fun handle(result: DeleteAccountResult) {
        when (result) {
            // La cuenta no se eliminó: la hoja se cierra y la pantalla vuelve con los campos marcados.
            DeleteAccountResult.WrongCredentials -> _state.update {
                it.copy(
                    isLoading = false, confirmCount = null, credentialsWrong = true, code = "",
                    focusRequest = focusOn(DeleteField.Password),
                )
            }
            DeleteAccountResult.KeyVersionConflict -> _state.update { it.copy(isLoading = false, confirmCount = null, conflict = true) }
            is DeleteAccountResult.RateLimited -> {
                lockoutCountdown.start(viewModelScope, result.retryAfterSeconds.seconds) { left ->
                    _state.update { it.copy(lockoutSecondsLeft = left) }
                }
                _state.update { it.copy(isLoading = false, confirmCount = null, code = "") }
            }
            // Sin red o 5xx: el genérico dentro de la hoja; nada local se tocó.
            DeleteAccountResult.Generic -> _state.update { it.copy(isLoading = false, sheetError = true) }
            // La cuenta ya no existe: la navegación a «Cuenta eliminada» llega por `AccountDeletionGate`.
            is DeleteAccountResult.Deleted -> _state.update { it.copy(isLoading = false, confirmCount = null) }
        }
    }

    override fun onCleared() {
        lockoutCountdown.cancel()
    }
}
