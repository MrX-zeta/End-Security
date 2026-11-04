package com.luis.end_security.feature.auth.ui.signup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luis.end_security.core.common.DefaultDispatcher
import com.luis.end_security.core.common.ApiResult
import com.luis.end_security.feature.auth.domain.AuthRepository
import com.luis.end_security.feature.auth.domain.EmailFormat
import com.luis.end_security.feature.auth.domain.MasterPasswordNormalizer
import com.luis.end_security.feature.auth.domain.MasterPasswordPolicy
import com.luis.end_security.feature.auth.domain.PasswordStrengthEstimator
import com.luis.end_security.feature.auth.domain.UnicodeCharacterData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SignUpViewModel @Inject constructor(
    private val estimator: PasswordStrengthEstimator,
    private val unicode: UnicodeCharacterData,
    private val auth: AuthRepository,
    @param:DefaultDispatcher private val scoringDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _state = MutableStateFlow(SignUpUiState())
    val state: StateFlow<SignUpUiState> = _state.asStateFlow()

    private val _effects = Channel<SignUpEffect>(Channel.BUFFERED)
    val effects: Flow<SignUpEffect> = _effects.receiveAsFlow()

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
                val valid = EmailFormat.isValid(action.value)
                // En cuanto queda bien, el error se quita; si no, no aparece mientras se escribe.
                it.copy(email = action.value, isEmailValid = valid, showEmailError = it.showEmailError && !valid).withCanSubmit()
            }
            SignUpAction.EmailFocusLost -> _state.update {
                if (it.email.isNotBlank() && !it.isEmailValid) it.copy(showEmailError = true) else it
            }
            is SignUpAction.PasswordsChanged -> onPasswordsChanged(action.password, action.confirmation)
            SignUpAction.PasswordsCleared -> clearPasswordDerivedState()
            SignUpAction.ConfirmationFocusLost -> revealMismatchIfAny()
            is SignUpAction.TermsAcceptedChanged -> _state.update {
                it.copy(termsAccepted = action.accepted, showTermsError = it.showTermsError && !action.accepted).withCanSubmit()
            }
            is SignUpAction.Submit -> submit(action.password, action.confirmation)
        }
    }

    private fun onPasswordsChanged(password: CharSequence, confirmation: CharSequence) {
        if (password.isEmpty() && confirmation.isEmpty()) {
            clearPasswordDerivedState()
            return
        }
        // Todo se calcula sobre el texto normalizado (NFKC); las copias viven solo en esta llamada
        // y en el cálculo del puntaje (DEC-27).
        val normalizedPassword = MasterPasswordNormalizer.normalize(password)
        val normalizedConfirmation = MasterPasswordNormalizer.normalize(confirmation)
        passwordLength = MasterPasswordNormalizer.codePointLength(normalizedPassword)
        confirmationIsEmpty = confirmation.isEmpty()
        val matches = matches(normalizedPassword, normalizedConfirmation)
        // Pasado el máximo no se mide: acota el costo de zxcvbn (y después el de Argon2).
        val exceedsMax = MasterPasswordPolicy.exceedsMaxLength(passwordLength)
        // Con caracteres invisibles tampoco: no se puede usar, así que no hace falta su puntaje.
        val forbidden = MasterPasswordPolicy.hasForbiddenCharacters(password, normalizedPassword, unicode)
        val needsScore = password.isNotEmpty() && !exceedsMax && !forbidden
        scoringJob?.cancel()
        scorePending = needsScore
        _state.update {
            it.copy(
                strengthScore = if (needsScore) it.strengthScore else null,
                passwordTyped = password.isNotEmpty(),
                passwordLength = passwordLength,
                confirmationLength = MasterPasswordNormalizer.codePointLength(normalizedConfirmation),
                showPasswordRequired = it.showPasswordRequired && password.isEmpty(),
                meetsLength = MasterPasswordPolicy.meetsLength(passwordLength),
                hasUppercase = MasterPasswordPolicy.hasUppercase(normalizedPassword),
                hasLowercase = MasterPasswordPolicy.hasLowercase(normalizedPassword),
                hasDigit = MasterPasswordPolicy.hasDigit(normalizedPassword),
                // Al cambiar la contraseña, el aviso de «difícil de adivinar» se vuelve a ganar con el botón.
                showWeakPassword = false,
                exceedsMaxLength = exceedsMax,
                hasForbiddenCharacters = forbidden,
                confirmationMatches = matches,
                // Si ya coinciden se quita el error; si no, no se muestra mientras se escribe.
                showMismatchError = it.showMismatchError && !matches,
            ).withCanSubmit()
        }
        if (!needsScore) return
        scoringJob = viewModelScope.launch {
            val score = scoreOrNull(normalizedPassword)
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
                passwordTyped = false,
                passwordLength = 0,
                confirmationLength = 0,
                meetsLength = false,
                hasUppercase = false,
                hasLowercase = false,
                hasDigit = false,
                showWeakPassword = false,
                exceedsMaxLength = false,
                hasForbiddenCharacters = false,
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

    private var focusSequence = 0

    private fun focusOn(field: SignUpField) = SignUpFocusRequest(field, ++focusSequence)

    private fun submit(password: CharSequence, confirmation: CharSequence) {
        if (_state.value.isLoading || submitJob?.isActive == true) return
        // Lo derivado se recalcula con el texto que se va a registrar (no con el último cálculo, que va atrasado por el
        // debounce). La contraseña y la confirmación solo viven en esta llamada.
        onPasswordsChanged(password, confirmation)
        val normalizedPassword = MasterPasswordNormalizer.normalize(password)
        val normalizedConfirmation = MasterPasswordNormalizer.normalize(confirmation)
        val codePoints = MasterPasswordNormalizer.codePointLength(normalizedPassword)
        val current = _state.value

        // Errores que se ven sin esperar a nada; «al tocar el botón» se muestran todos a la vez.
        val emailBad = !current.isEmailValid
        val passwordEmpty = password.isEmpty()
        val passwordBad = passwordEmpty || !MasterPasswordPolicy.meetsLength(codePoints) ||
            MasterPasswordPolicy.exceedsMaxLength(codePoints) || current.hasForbiddenCharacters ||
            !MasterPasswordPolicy.meetsCharacterClasses(normalizedPassword)
        val mismatch = !passwordEmpty && !matches(normalizedPassword, normalizedConfirmation)
        val termsBad = !current.termsAccepted
        _state.update {
            it.copy(
                showEmailError = emailBad,
                showPasswordRequired = passwordEmpty,
                showMismatchError = mismatch,
                showTermsError = termsBad,
                submitError = null,
            ).withCanSubmit()
        }
        val firstBad = when {
            emailBad -> SignUpField.Email
            passwordBad -> SignUpField.Password
            mismatch -> SignUpField.Confirmation
            termsBad -> SignUpField.Terms
            else -> null
        }
        if (firstBad != null) {
            // Con errores no se manda nada: ni al servidor ni a la derivación de llaves.
            _state.update { it.copy(focusRequest = focusOn(firstBad)) }
            return
        }
        _state.update { it.copy(isLoading = true).withCanSubmit() }
        submitJob = viewModelScope.launch {
            val accepted = passesNormalizedPolicy(password, normalizedPassword, normalizedConfirmation)
            if (!accepted) {
                // Lo único que falta es la fuerza: se marca la contraseña y el foco va a ella.
                _state.update {
                    it.copy(isLoading = false, showWeakPassword = true, focusRequest = focusOn(SignUpField.Password))
                        .withCanSubmit()
                }
                return@launch
            }
            // OP-11: el repositorio genera la cuenta, deriva las llaves (Argon2 fuera del hilo
            // principal) y registra. La contraseña no se guarda en ningún lado.
            val email = _state.value.email.trim()
            val result = auth.register(email, password)
            _state.update {
                it.copy(
                    isLoading = false,
                    submitError = (result as? ApiResult.Failure)?.error?.let { error -> SignUpSubmitError.from(error) },
                ).withCanSubmit()
            }
            if (result is ApiResult.Success) _effects.send(SignUpEffect.NavigateToVerifyEmail(email))
        }
    }

    /**
     * La política de DEC-31 sobre el texto exacto que se va a registrar, no sobre el último
     * cálculo del medidor (que puede ir atrasado por el debounce).
     */
    internal suspend fun passesPolicy(password: CharSequence, confirmation: CharSequence): Boolean =
        passesNormalizedPolicy(
            password,
            MasterPasswordNormalizer.normalize(password),
            MasterPasswordNormalizer.normalize(confirmation),
        )

    private suspend fun passesNormalizedPolicy(
        password: CharSequence,
        normalizedPassword: String,
        normalizedConfirmation: String,
    ): Boolean {
        val codePoints = MasterPasswordNormalizer.codePointLength(normalizedPassword)
        // Largo y coincidencia antes de puntuar: pasado el máximo, zxcvbn ni siquiera corre.
        if (!MasterPasswordPolicy.meetsLength(codePoints) || MasterPasswordPolicy.exceedsMaxLength(codePoints)) {
            return false
        }
        if (MasterPasswordPolicy.hasForbiddenCharacters(password, normalizedPassword, unicode)) return false
        if (!MasterPasswordPolicy.meetsCharacterClasses(normalizedPassword)) return false
        if (!matches(normalizedPassword, normalizedConfirmation)) return false
        val score = scoreOrNull(normalizedPassword) ?: return false
        return MasterPasswordPolicy.isAcceptable(codePoints, score)
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

    // Recibe textos ya normalizados. contentEquals compara carácter por carácter.
    private fun matches(password: CharSequence, confirmation: CharSequence): Boolean =
        password.isNotEmpty() && password.contentEquals(confirmation)

    private fun SignUpUiState.withCanSubmit(): SignUpUiState = copy(
        canSubmit = isEmailValid &&
            !scorePending &&
            !hasForbiddenCharacters &&
            MasterPasswordPolicy.isAcceptable(passwordLength, strengthScore) &&
            hasUppercase && hasLowercase && hasDigit &&
            confirmationMatches &&
            termsAccepted &&
            !isLoading,
    )
}
