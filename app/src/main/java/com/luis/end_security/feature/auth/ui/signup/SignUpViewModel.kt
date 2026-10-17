package com.luis.end_security.feature.auth.ui.signup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luis.end_security.core.common.DefaultDispatcher
import com.luis.end_security.feature.auth.domain.EmailFormat
import com.luis.end_security.feature.auth.domain.MasterPasswordPolicy
import com.luis.end_security.feature.auth.domain.PasswordStrengthEstimator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SignUpViewModel @Inject constructor(
    private val estimator: PasswordStrengthEstimator,
    @param:DefaultDispatcher private val scoringDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _state = MutableStateFlow(SignUpUiState())
    val state: StateFlow<SignUpUiState> = _state.asStateFlow()

    private var passwordLength = 0
    private var confirmationIsEmpty = true

    // Mientras se calcula el puntaje de la contraseña nueva, el anterior no habilita el botón.
    private var scorePending = false
    private var scoringJob: Job? = null

    fun onAction(action: SignUpAction) {
        when (action) {
            is SignUpAction.EmailChanged -> _state.update {
                it.copy(email = action.value, isEmailValid = EmailFormat.isValid(action.value)).withCanSubmit()
            }
            is SignUpAction.PasswordsChanged -> onPasswordsChanged(action.password, action.confirmation)
            SignUpAction.ConfirmationFocusLost -> revealMismatchIfAny()
            is SignUpAction.TermsAcceptedChanged -> _state.update {
                it.copy(termsAccepted = action.accepted).withCanSubmit()
            }
            SignUpAction.Submit -> submit()
        }
    }

    private fun onPasswordsChanged(password: CharSequence, confirmation: CharSequence) {
        passwordLength = password.length
        confirmationIsEmpty = confirmation.isEmpty()
        // contentEquals compara carácter por carácter, sin crear un String.
        val matches = password.isNotEmpty() && password.contentEquals(confirmation)
        _state.update {
            it.copy(
                meetsLength = MasterPasswordPolicy.meetsLength(password.length),
                confirmationMatches = matches,
                // Si ya coinciden se quita el error; si no, no se muestra mientras se escribe.
                showMismatchError = it.showMismatchError && !matches,
            )
        }

        scoringJob?.cancel()
        if (password.isEmpty()) {
            scorePending = false
            _state.update { it.copy(strengthScore = null).withCanSubmit() }
            return
        }
        scorePending = true
        _state.update { it.withCanSubmit() }
        scoringJob = viewModelScope.launch {
            val score = withContext(scoringDispatcher) { estimator.score(password) }
            scorePending = false
            _state.update { it.copy(strengthScore = score).withCanSubmit() }
        }
    }

    private fun revealMismatchIfAny() {
        if (!confirmationIsEmpty && !_state.value.confirmationMatches) {
            _state.update { it.copy(showMismatchError = true) }
        }
    }

    private fun submit() {
        revealMismatchIfAny()
        if (!_state.value.canSubmit) return
        // TODO(OP-11): registro. La pantalla copiará la contraseña a un CharArray, se derivarán
        //  enc_key y auth_key (DEC-05) y el arreglo se sobrescribirá. Falta PEND-01 (Argon2)
        //  para crear AuthRepository; por ahora no se envía nada.
    }

    private fun SignUpUiState.withCanSubmit(): SignUpUiState = copy(
        canSubmit = isEmailValid &&
            !scorePending &&
            MasterPasswordPolicy.isAcceptable(passwordLength, strengthScore) &&
            confirmationMatches &&
            termsAccepted &&
            !isLoading,
    )
}
