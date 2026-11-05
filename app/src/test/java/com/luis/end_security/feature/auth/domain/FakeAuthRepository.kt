package com.luis.end_security.feature.auth.domain

import com.luis.end_security.core.common.ApiResult
import kotlinx.coroutines.CompletableDeferred

/**
 * [AuthRepository] para las pruebas de los ViewModel: devuelve lo que la prueba fija y anota
 * las llamadas. Con [gate] la llamada espera hasta completarlo, para ver el estado «cargando».
 */
class FakeAuthRepository : AuthRepository {
    var registerResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var verifyEmailResult: ApiResult<AuthStep> = ApiResult.Success(AuthStep.VaultUnlocked)
    var resendResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var loginResult: ApiResult<AuthStep> = ApiResult.Success(AuthStep.VaultUnlocked)
    var twoFactorResult: ApiResult<AuthStep> = ApiResult.Success(AuthStep.VaultUnlocked)
    var recoveryResult: ApiResult<AuthStep> = ApiResult.Success(AuthStep.VaultUnlocked)
    var unlockOutcome: (password: String) -> UnlockOutcome = { UnlockOutcome.Unlocked }
    var accountName: String? = null
    var recoveryCodesNotice: Int? = null
    var gate: CompletableDeferred<Unit>? = null

    /** Cada llamada como «función:argumento»; las contraseñas no se anotan aquí. */
    val calls = mutableListOf<String>()

    /** Las contraseñas recibidas, para comprobar que llega el texto del campo. */
    val passwords = mutableListOf<String>()

    /** El `marketingOptIn` de cada `register` recibido. */
    val marketingOptIns = mutableListOf<Boolean>()
    var pendingLoginCancelled = false
    var signedOut = false

    /** Si no es `null`, `signOut()` lanza esto después de marcar `signedOut` (falla del borrado local). */
    var signOutError: RuntimeException? = null

    private suspend fun waitGate() {
        gate?.await()
    }

    override suspend fun register(email: String, password: CharSequence, marketingOptIn: Boolean): ApiResult<Unit> {
        marketingOptIns += marketingOptIn
        calls += "register:$email"
        passwords += password.toString()
        waitGate()
        return registerResult
    }

    override suspend fun verifyEmail(email: String, code: String): ApiResult<AuthStep> {
        calls += "verifyEmail:$email:$code"
        waitGate()
        return verifyEmailResult
    }

    override suspend fun resendVerification(email: String): ApiResult<Unit> {
        calls += "resend:$email"
        return resendResult
    }

    override suspend fun login(email: String, password: CharSequence): ApiResult<AuthStep> {
        calls += "login:$email"
        passwords += password.toString()
        waitGate()
        return loginResult
    }

    override suspend fun verifyTwoFactor(code: String): ApiResult<AuthStep> {
        calls += "twoFactor:$code"
        waitGate()
        return twoFactorResult
    }

    override suspend fun verifyRecoveryCode(code: String): ApiResult<AuthStep> {
        calls += "recovery:$code"
        waitGate()
        return recoveryResult
    }

    override fun cancelPendingLogin() {
        pendingLoginCancelled = true
    }

    override suspend fun unlock(password: CharSequence): UnlockOutcome {
        calls += "unlock"
        passwords += password.toString()
        waitGate()
        return unlockOutcome(password.toString())
    }

    override suspend fun signOut() {
        waitGate()
        signedOut = true
        signOutError?.let { throw it }
    }

    /** Lo que devuelve la limpieza tras eliminar la cuenta; `false` = parte no se pudo borrar. */
    var wipeResult: Boolean = true
    var wipeCalls = 0
    var pendingWipeCalls = 0

    /** Se invoca al empezar la limpieza (para mirar el estado de otras piezas en ese momento). */
    var onWipe: (() -> Unit)? = null

    /** Cada llamada a la limpieza, en orden con las demás de [calls]. */
    override suspend fun wipeAfterAccountDeletion(): Boolean {
        wipeCalls++
        onWipe?.invoke()
        calls += "wipeAfterAccountDeletion"
        waitGate()
        return wipeResult
    }

    override suspend fun completePendingWipe(): Boolean {
        pendingWipeCalls++
        calls += "completePendingWipe"
        return true
    }

    override suspend fun localAccountName(): String? = accountName

    override fun consumeRecoveryCodesNotice(): Int? = recoveryCodesNotice.also { recoveryCodesNotice = null }
}
