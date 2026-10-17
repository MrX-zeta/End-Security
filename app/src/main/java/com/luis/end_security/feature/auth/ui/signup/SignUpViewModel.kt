package com.luis.end_security.feature.auth.ui.signup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luis.end_security.core.common.DefaultDispatcher
import com.luis.end_security.feature.auth.domain.EmailFormat
import com.luis.end_security.feature.auth.domain.MasterPasswordPolicy
import com.luis.end_security.feature.auth.domain.PasswordStrengthEstimator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private var submitJob: Job? = null

    // Un cálculo a la vez. Si se cancela mientras espera su turno, ya no se ejecuta.
    private val scoringMutex = Mutex()

    fun onAction(action: SignUpAction) {
        when (action) {
            is SignUpAction.EmailChanged -> _state.update {
                it.copy(email = action.value, isEmailValid = EmailFormat.isValid(action.value)).withCanSubmit()
            }
            is SignUpAction.PasswordsChanged -> onPasswordsChanged(action.password, action.confirmation)
            SignUpAction.PasswordsCleared -> clearPasswordDerivedState()
            SignUpAction.ConfirmationFocusLost -> revealMismatchIfAny()
            is SignUpAction.TermsAcceptedChanged -> _state.update {
                it.copy(termsAccepted = action.accepted).withCanSubmit()
            }
            is SignUpAction.Submit -> submit(action.password, action.confirmation)
        }
    }

    private fun onPasswordsChanged(password: CharSequence, confirmation: CharSequence) {
        if (password.isEmpty() && confirmation.isEmpty()) {
            clearPasswordDerivedState()
            return
        }
        passwordLength = password.length
        confirmationIsEmpty = confirmation.isEmpty()
        val matches = matches(password, confirmation)
        scoringJob?.cancel()
        scorePending = password.isNotEmpty()
        _state.update {
            it.copy(
                strengthScore = if (password.isEmpty()) null else it.strengthScore,
                meetsLength = MasterPasswordPolicy.meetsLength(password.length),
                confirmationMatches = matches,
                // Si ya coinciden se quita el error; si no, no se muestra mientras se escribe.
                showMismatchError = it.showMismatchError && !matches,
            ).withCanSubmit()
        }
        if (password.isEmpty()) return
        scoringJob = viewModelScope.launch {
            val score = scoreOrNull(password)
            scorePending = false
            _state.update { it.copy(strengthScore = score).withCanSubmit() }
        }
    }

    /** Los campos quedaron vacíos: se olvida todo lo calculado de ellos, sin esperar el debounce. */
    private fun clearPasswordDerivedState() {
        scoringJob?.cancel()
        scorePending = false
        passwordLength = 0
        confirmationIsEmpty = true
        _state.update {
            it.copy(
                strengthScore = null,
                meetsLength = false,
                confirmationMatches = false,
                showMismatchError = false,
            ).withCanSubmit()
        }
    }

    private fun revealMismatchIfAny() {
        if (!confirmationIsEmpty && !_state.value.confirmationMatches) {
            _state.update { it.copy(showMismatchError = true) }
        }
    }

    private fun submit(password: CharSequence, confirmation: CharSequence) {
        // «No coinciden» se evalúa con el texto que se intenta registrar.
        if (confirmation.isNotEmpty() && !matches(password, confirmation)) {
            _state.update { it.copy(confirmationMatches = false, showMismatchError = true).withCanSubmit() }
        }
        val current = _state.value
        if (!current.isEmailValid || !current.termsAccepted || current.isLoading) return
        if (submitJob?.isActive == true) return
        _state.update { it.copy(isLoading = true).withCanSubmit() }
        submitJob = viewModelScope.launch {
            val accepted = passesPolicy(password, confirmation)
            _state.update { it.copy(isLoading = false).withCanSubmit() }
            if (!accepted) return@launch
            // TODO(OP-11): registro. La pantalla copiará la contraseña a un CharArray, se derivarán
            //  enc_key y auth_key (DEC-05) y el arreglo se sobrescribirá. Falta PEND-01 (Argon2)
            //  para crear AuthRepository; por ahora no se envía nada.
        }
    }

    /**
     * La política de DEC-31 sobre el texto exacto que se va a registrar, no sobre el último
     * cálculo del medidor (que puede ir atrasado por el debounce).
     */
    internal suspend fun passesPolicy(password: CharSequence, confirmation: CharSequence): Boolean {
        if (!MasterPasswordPolicy.meetsLength(password.length) || !matches(password, confirmation)) return false
        val score = scoreOrNull(password) ?: return false
        return MasterPasswordPolicy.isAcceptable(password.length, score)
    }

    /** Puntaje en el dispatcher de CPU; `null` si el estimador falla (el botón queda deshabilitado). */
    private suspend fun scoreOrNull(password: CharSequence): Int? = try {
        scoringMutex.withLock {
            withContext(scoringDispatcher) {
                ensureActive()
                estimator.score(password)
            }
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        null
    }

    // contentEquals compara carácter por carácter, sin crear un String.
    private fun matches(password: CharSequence, confirmation: CharSequence): Boolean =
        password.isNotEmpty() && password.contentEquals(confirmation)

    private fun SignUpUiState.withCanSubmit(): SignUpUiState = copy(
        canSubmit = isEmailValid &&
            !scorePending &&
            MasterPasswordPolicy.isAcceptable(passwordLength, strengthScore) &&
            confirmationMatches &&
            termsAccepted &&
            !isLoading,
    )
}
