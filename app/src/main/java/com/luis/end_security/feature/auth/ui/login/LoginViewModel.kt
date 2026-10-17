package com.luis.end_security.feature.auth.ui.login

import androidx.lifecycle.ViewModel
import com.luis.end_security.feature.auth.domain.EmailFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    private var passwordIsEmpty = true

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
        //  al tocar «Entrar», se derivará auth_key (DEC-05) y el arreglo se sobrescribirá.
        //  Falta PEND-01 (Argon2) para crear AuthRepository; por ahora no se envía nada.
    }

    private fun LoginUiState.withCanSubmit(): LoginUiState =
        copy(canSubmit = isEmailValid && !passwordIsEmpty && !isLoading)
}
