package com.luis.end_security.feature.account.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luis.end_security.core.common.Countdown
import com.luis.end_security.core.common.DefaultDispatcher
import com.luis.end_security.core.designsystem.component.CodeFieldFilter
import com.luis.end_security.core.session.AuthNotices
import com.luis.end_security.feature.account.domain.ChangeMasterPassword
import com.luis.end_security.feature.account.domain.ChangePasswordResult
import com.luis.end_security.feature.account.domain.TwoFactorRepository
import com.luis.end_security.feature.auth.domain.MasterPasswordNormalizer
import com.luis.end_security.feature.auth.domain.MasterPasswordPolicy
import com.luis.end_security.feature.auth.domain.PasswordStrengthEstimator
import com.luis.end_security.feature.auth.domain.UnicodeCharacterData
import com.luis.end_security.feature.health.domain.BreachApi
import com.luis.end_security.feature.health.domain.BreachLookup
import com.luis.end_security.feature.unlock.domain.BiometricPreparation
import com.luis.end_security.feature.unlock.domain.BiometricResult
import com.luis.end_security.feature.unlock.domain.BiometricUnlock
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
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
import javax.crypto.Cipher
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** Qué se sabe de «No aparece en filtraciones»: se consulta al tocar el botón, no al escribir (DEC-21). */
enum class BreachCheck { Pending, Clean, Found, Offline }

enum class ChangeField { Current, Code, New, Confirmation }

/** [id] cambia en cada intento, para que dos toques seguidos con el mismo error vuelvan a dar el foco. */
data class ChangeFocus(val field: ChangeField, val id: Int)

/**
 * Estado de Contraseña maestra (SCR-35). Ninguna contraseña vive aquí (regla 6): solo lo que se calcula de ellas.
 */
data class ChangePasswordUiState(
    /** `null` mientras no se sabe (sin red): el código de la app se pide solo con la 2FA activa. */
    val twoFactorRequired: Boolean? = null,
    val code: String = "",
    val newTyped: Boolean = false,
    val passwordLength: Int = 0,
    val confirmationLength: Int = 0,
    val strengthScore: Int? = null,
    val meetsLength: Boolean = false,
    val hasUppercase: Boolean = false,
    val hasLowercase: Boolean = false,
    val hasDigit: Boolean = false,
    val hasForbiddenCharacters: Boolean = false,
    val exceedsMaxLength: Boolean = false,
    val confirmationMatches: Boolean = false,
    val breach: BreachCheck = BreachCheck.Pending,
    val showCurrentRequired: Boolean = false,
    val showCodeIncomplete: Boolean = false,
    val showNewRequired: Boolean = false,
    val showWeak: Boolean = false,
    val showSameAsCurrent: Boolean = false,
    val showMismatch: Boolean = false,
    /** `reauth_required`: «Contraseña o código incorrectos.» */
    val credentialsWrong: Boolean = false,
    /** 409: la contraseña cambió en otro dispositivo. */
    val conflict: Boolean = false,
    val genericError: Boolean = false,
    val lockoutSecondsLeft: Int = 0,
    /** Revisando, derivando llaves y llamando al servidor: el botón queda cargando y Atrás deshabilitado. */
    val isLoading: Boolean = false,
    /** Esperando la huella para volver a envolver la `vault_key` (paso 6). */
    val biometricPending: Boolean = false,
    val focusRequest: ChangeFocus? = null,
) {
    val isLockedOut: Boolean get() = lockoutSecondsLeft > 0

    /** Atrás se deshabilita mientras dura el cambio. */
    val isBusy: Boolean get() = isLoading || biometricPending

    override fun toString(): String =
        "ChangePasswordUiState(loading=$isLoading, breach=$breach, score=$strengthScore, digits=${code.length}, ***)"
}

sealed interface ChangePasswordAction {
    /** La pantalla avisa si «Contraseña actual» quedó vacía o no; nunca manda su contenido. */
    data class CurrentEmptinessChanged(val isEmpty: Boolean) : ChangePasswordAction

    data class CodeChanged(val value: String) : ChangePasswordAction {
        override fun toString(): String = "CodeChanged(***)"
    }

    /** La nueva y su confirmación cambiaron (con espera tras la última tecla). No se guardan. */
    class NewPasswordsChanged(val password: CharSequence, val confirmation: CharSequence) : ChangePasswordAction {
        override fun toString(): String = "NewPasswordsChanged(***)"
    }

    data object NewPasswordsCleared : ChangePasswordAction
    data object ConfirmationFocusLost : ChangePasswordAction

    /** «Cambiar contraseña». No es `data class`: su `toString()` no imprime nada. */
    class Submit(val current: CharSequence, val password: CharSequence, val confirmation: CharSequence) : ChangePasswordAction {
        override fun toString(): String = "Submit(***)"
    }

    class BiometricAuthenticated(val cipher: Cipher) : ChangePasswordAction {
        override fun toString(): String = "BiometricAuthenticated(***)"
    }

    data object BiometricCancelled : ChangePasswordAction
}

sealed interface ChangePasswordEffect {
    /** Vuelve a Ajustes; [biometricOff]: la huella quedó desactivada y hay que avisarlo. */
    data class Finish(val biometricOff: Boolean) : ChangePasswordEffect

    /** La pantalla muestra el `BiometricPrompt` («Confirma tu huella para seguir usándola»). */
    class ShowBiometricPrompt(val cipher: Cipher) : ChangePasswordEffect

    /** Se cerró la sesión local: a Iniciar sesión con la pila limpia. */
    data object NavigateToLogin : ChangePasswordEffect
}

@HiltViewModel
class ChangePasswordViewModel @Inject constructor(
    private val change: ChangeMasterPassword,
    private val twoFactor: TwoFactorRepository,
    private val breaches: BreachApi,
    private val estimator: PasswordStrengthEstimator,
    private val unicode: UnicodeCharacterData,
    private val biometric: BiometricUnlock,
    private val notices: AuthNotices,
    @param:DefaultDispatcher private val scoringDispatcher: CoroutineDispatcher,
    timeSource: TimeSource,
) : ViewModel() {

    private val _state = MutableStateFlow(ChangePasswordUiState())
    val state: StateFlow<ChangePasswordUiState> = _state.asStateFlow()

    private val _effects = Channel<ChangePasswordEffect>(Channel.BUFFERED)
    val effects: Flow<ChangePasswordEffect> = _effects.receiveAsFlow()

    private var focusSequence = 0
    private var scoringJob: Job? = null
    private val scoringMutex = Mutex()
    private val lockoutCountdown = Countdown(timeSource)

    init {
        // El campo del código solo se muestra con la 2FA activa; sin red queda sin saber y se vuelve a preguntar al enviar.
        viewModelScope.launch {
            val enabled = twoFactor.isEnabled()
            _state.update { it.copy(twoFactorRequired = enabled) }
        }
    }

    fun onAction(action: ChangePasswordAction) {
        when (action) {
            is ChangePasswordAction.CurrentEmptinessChanged -> _state.update {
                it.copy(
                    showCurrentRequired = it.showCurrentRequired && action.isEmpty,
                    credentialsWrong = false,
                    conflict = false,
                    genericError = false,
                )
            }
            is ChangePasswordAction.CodeChanged -> _state.update {
                val code = CodeFieldFilter.filter(action.value)
                it.copy(
                    code = code,
                    credentialsWrong = false,
                    genericError = false,
                    showCodeIncomplete = it.showCodeIncomplete && code.length < CodeFieldFilter.LENGTH,
                )
            }
            is ChangePasswordAction.NewPasswordsChanged -> onNewPasswordsChanged(action.password, action.confirmation, score = true)
            ChangePasswordAction.NewPasswordsCleared -> clearDerived()
            ChangePasswordAction.ConfirmationFocusLost -> _state.update {
                if (it.confirmationLength > 0 && !it.confirmationMatches) it.copy(showMismatch = true) else it
            }
            is ChangePasswordAction.Submit -> submit(action.current, action.password, action.confirmation)
            is ChangePasswordAction.BiometricAuthenticated -> completeBiometric(action.cipher)
            ChangePasswordAction.BiometricCancelled -> finishWithBiometricOff()
        }
    }

    /** Todo se calcula sobre el texto normalizado (NFKC); las copias viven solo en esta llamada y en el puntaje (DEC-27). */
    private fun onNewPasswordsChanged(password: CharSequence, confirmation: CharSequence, score: Boolean) {
        if (password.isEmpty() && confirmation.isEmpty()) {
            clearDerived()
            return
        }
        val normalized = MasterPasswordNormalizer.normalize(password)
        val normalizedConfirmation = MasterPasswordNormalizer.normalize(confirmation)
        val length = MasterPasswordNormalizer.codePointLength(normalized)
        val exceeds = MasterPasswordPolicy.exceedsMaxLength(length)
        val forbidden = MasterPasswordPolicy.hasForbiddenCharacters(password, normalized, unicode)
        val matches = normalized.isNotEmpty() && normalized.contentEquals(normalizedConfirmation)
        scoringJob?.cancel()
        _state.update {
            it.copy(
                newTyped = password.isNotEmpty(),
                passwordLength = length,
                confirmationLength = MasterPasswordNormalizer.codePointLength(normalizedConfirmation),
                strengthScore = if (password.isNotEmpty() && !exceeds && !forbidden) it.strengthScore else null,
                meetsLength = MasterPasswordPolicy.meetsLength(length),
                hasUppercase = MasterPasswordPolicy.hasUppercase(normalized),
                hasLowercase = MasterPasswordPolicy.hasLowercase(normalized),
                hasDigit = MasterPasswordPolicy.hasDigit(normalized),
                hasForbiddenCharacters = forbidden,
                exceedsMaxLength = exceeds,
                confirmationMatches = matches,
                showMismatch = it.showMismatch && !matches,
                showNewRequired = it.showNewRequired && password.isEmpty(),
                showSameAsCurrent = false,
                showWeak = false,
                // Cambiar el texto invalida lo que ya se había revisado en filtraciones.
                breach = BreachCheck.Pending,
            )
        }
        if (score && password.isNotEmpty() && !exceeds && !forbidden) {
            scoringJob = viewModelScope.launch {
                val value = scoreOrNull(normalized)
                _state.update { it.copy(strengthScore = value) }
            }
        }
    }

    private fun clearDerived() {
        scoringJob?.cancel()
        _state.update {
            it.copy(
                newTyped = false,
                passwordLength = 0,
                confirmationLength = 0,
                strengthScore = null,
                meetsLength = false,
                hasUppercase = false,
                hasLowercase = false,
                hasDigit = false,
                hasForbiddenCharacters = false,
                exceedsMaxLength = false,
                confirmationMatches = false,
                showMismatch = false,
                showSameAsCurrent = false,
                showWeak = false,
                breach = BreachCheck.Pending,
            )
        }
    }

    /** Puntaje en el dispatcher de CPU; `null` si el estimador falla. */
    private suspend fun scoreOrNull(normalized: CharSequence): Int? = try {
        scoringMutex.withLock { withContext(scoringDispatcher) { estimator.score(normalized) } }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        null
    }

    private fun focusOn(field: ChangeField) = ChangeFocus(field, ++focusSequence)

    /**
     * «Cambiar contraseña» (OP-27). Primero se marcan todos los errores de la pantalla y el foco va al primero (sin red ni
     * Argon2); después se revisa «No aparece en filtraciones» (solo aquí) y por fin se llama al cambio.
     */
    private fun submit(current: CharSequence, password: CharSequence, confirmation: CharSequence) {
        val before = _state.value
        if (before.isBusy || before.isLockedOut) return
        // Lo derivado se recalcula con el texto que se va a enviar (el medidor va atrasado por el debounce).
        onNewPasswordsChanged(password, confirmation, score = false)
        val normalizedNew = MasterPasswordNormalizer.normalize(password)
        val normalizedCurrent = MasterPasswordNormalizer.normalize(current)
        val s = _state.value
        val currentEmpty = current.isEmpty()
        val codeBad = s.twoFactorRequired == true && s.code.length != CodeFieldFilter.LENGTH
        val newEmpty = password.isEmpty()
        val newInvalid = newEmpty || !s.meetsLength || s.exceedsMaxLength || s.hasForbiddenCharacters ||
            !(s.hasUppercase && s.hasLowercase && s.hasDigit)
        val same = !newEmpty && normalizedNew == normalizedCurrent
        val mismatch = !newEmpty && !s.confirmationMatches
        val first = when {
            currentEmpty -> ChangeField.Current
            codeBad -> ChangeField.Code
            newInvalid || same -> ChangeField.New
            mismatch -> ChangeField.Confirmation
            else -> null
        }
        _state.update {
            it.copy(
                showCurrentRequired = currentEmpty,
                showCodeIncomplete = codeBad,
                showNewRequired = newEmpty,
                showSameAsCurrent = same,
                showMismatch = mismatch,
                credentialsWrong = false,
                conflict = false,
                genericError = false,
                focusRequest = if (first != null) focusOn(first) else it.focusRequest,
            )
        }
        // Con errores no se llama a nada: ni a HIBP, ni a Argon2, ni al servidor.
        if (first != null) return
        _state.update { it.copy(isLoading = true) }
        viewModelScope.launch { run(current, password, normalizedNew) }
    }

    private suspend fun run(current: CharSequence, password: CharSequence, normalizedNew: String) {
        // ¿Hace falta el código de la app? Sin red se vuelve a preguntar aquí; si sigue sin saberse, no se puede cambiar.
        var required = _state.value.twoFactorRequired
        if (required == null) {
            required = twoFactor.isEnabled()
            if (required == null) {
                _state.update { it.copy(isLoading = false, genericError = true) }
                return
            }
            _state.update { it.copy(twoFactorRequired = required) }
            if (required) {
                _state.update { it.copy(isLoading = false, showCodeIncomplete = true, focusRequest = focusOn(ChangeField.Code)) }
                return
            }
        }
        // Fuerza (zxcvbn ≥ 3) sobre el texto exacto que se va a enviar.
        val score = scoreOrNull(normalizedNew)
        if (score == null || score < MasterPasswordPolicy.MIN_SCORE) {
            _state.update { it.copy(isLoading = false, strengthScore = score, showWeak = true, focusRequest = focusOn(ChangeField.New)) }
            return
        }
        // «No aparece en filtraciones»: solo el prefijo del SHA-1 sale del teléfono (DEC-21), solo al tocar el botón.
        when (breaches.lookup(normalizedNew)) {
            is BreachLookup.Found -> {
                _state.update {
                    it.copy(isLoading = false, breach = BreachCheck.Found, focusRequest = focusOn(ChangeField.New))
                }
                return
            }
            BreachLookup.Clean -> _state.update { it.copy(breach = BreachCheck.Clean) }
            // Sin red o sin respuesta: no bloquea (de todos modos el cambio necesita red).
            BreachLookup.Offline, BreachLookup.Unavailable -> _state.update { it.copy(breach = BreachCheck.Offline) }
        }
        val totp = if (required) _state.value.code else null
        handle(change.change(current, totp, password))
    }

    private suspend fun handle(result: ChangePasswordResult) {
        when (result) {
            is ChangePasswordResult.Success -> {
                _state.update { it.copy(isLoading = false) }
                if (result.biometricWasEnabled) rewrapBiometric() else _effects.send(ChangePasswordEffect.Finish(biometricOff = false))
            }
            ChangePasswordResult.WrongCredentials -> _state.update {
                it.copy(isLoading = false, credentialsWrong = true, code = "", focusRequest = focusOn(ChangeField.Current))
            }
            ChangePasswordResult.KeyVersionConflict -> _state.update { it.copy(isLoading = false, conflict = true) }
            is ChangePasswordResult.RateLimited -> {
                lockoutCountdown.start(viewModelScope, result.retryAfterSeconds.seconds) { left ->
                    _state.update { it.copy(lockoutSecondsLeft = left) }
                }
                _state.update { it.copy(isLoading = false, code = "") }
            }
            ChangePasswordResult.Generic -> _state.update { it.copy(isLoading = false, genericError = true) }
            ChangePasswordResult.SameAsCurrent -> _state.update {
                it.copy(isLoading = false, showSameAsCurrent = true, focusRequest = focusOn(ChangeField.New))
            }
            ChangePasswordResult.InvalidNewPassword -> _state.update {
                it.copy(isLoading = false, showWeak = true, focusRequest = focusOn(ChangeField.New))
            }
            ChangePasswordResult.LocalSaveFailedSignedOut -> {
                _state.update { it.copy(isLoading = false) }
                notices.postPasswordChanged()
                _effects.send(ChangePasswordEffect.NavigateToLogin)
            }
        }
    }

    /** Paso 6: con la huella activa, se vuelve a envolver la `vault_key` con la `key_version` nueva (o se desactiva). */
    private suspend fun rewrapBiometric() {
        _state.update { it.copy(biometricPending = true) }
        when (val prepared = biometric.prepareEnable()) {
            is BiometricPreparation.Ready -> _effects.send(ChangePasswordEffect.ShowBiometricPrompt(prepared.cipher))
            else -> finishWithBiometricOffNow()
        }
    }

    private fun completeBiometric(cipher: Cipher) {
        viewModelScope.launch {
            val result = try {
                biometric.completeEnable(cipher)
            } catch (_: RuntimeException) {
                BiometricResult.Failed
            }
            if (result == BiometricResult.Done) {
                _state.update { it.copy(biometricPending = false) }
                _effects.send(ChangePasswordEffect.Finish(biometricOff = false))
            } else {
                finishWithBiometricOffNow()
            }
        }
    }

    private fun finishWithBiometricOff() {
        viewModelScope.launch { finishWithBiometricOffNow() }
    }

    private suspend fun finishWithBiometricOffNow() {
        runCatching { biometric.disable() }
        _state.update { it.copy(biometricPending = false) }
        _effects.send(ChangePasswordEffect.Finish(biometricOff = true))
    }

    override fun onCleared() {
        lockoutCountdown.cancel()
    }
}
