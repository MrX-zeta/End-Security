package com.luis.end_security.feature.auth.domain

import com.luis.end_security.core.common.ApiResult
import com.luis.end_security.core.crypto.SecretReader
import com.luis.end_security.core.crypto.Sealed
import com.luis.end_security.feature.account.domain.Authenticated
import com.luis.end_security.feature.account.domain.ServerKdf
import com.luis.end_security.feature.account.domain.SessionTokens
import java.util.UUID

/** Datos de `POST /auth/register` (EP-02). La `authKey` y la pvk las prepara el teléfono (DEC-05). */
class RegisterRequest(
    val userId: UUID,
    val email: String,
    val displayName: String?,
    val authKey: SecretReader,
    val kdf: ServerKdf,
    val protectedVaultKey: Sealed,
) {
    override fun toString(): String = "RegisterRequest(userId=$userId, ***)"
}

/** Respuesta de `POST /auth/login` (EP-05). */
sealed interface LoginResult {
    class Success(val authenticated: Authenticated) : LoginResult {
        override fun toString(): String = "LoginResult.Success(***)"
    }

    /** Falta el segundo paso: el reto vale 5 min y un solo uso. */
    class TwoFactorRequired(val challengeToken: String, val expiresInSeconds: Int) : LoginResult {
        override fun toString(): String = "LoginResult.TwoFactorRequired(***)"
    }
}

/** Respuesta de `POST /auth/2fa/recovery` (EP-07). */
class RecoveryLogin(
    val authenticated: Authenticated,
    val recoveryCodesRemaining: Int,
) {
    override fun toString(): String = "RecoveryLogin(recoveryCodesRemaining=$recoveryCodesRemaining, ***)"
}

/** Registro, acceso y tokens (contrato `docs/api`, EP-01…EP-09). */
interface AuthApi {
    /** EP-01: siempre responde, aunque el correo no exista (parámetros falsos, DEC-07). */
    suspend fun prelogin(email: String): ApiResult<ServerKdf>

    /** EP-02: siempre 202 (DEC-13). */
    suspend fun register(request: RegisterRequest): ApiResult<Unit>

    /** EP-03: verifica el correo y crea la primera sesión. */
    suspend fun verifyEmail(email: String, code: String, deviceName: String): ApiResult<Authenticated>

    /** EP-04: siempre 202; puede responder `rate_limited`. */
    suspend fun resendVerification(email: String): ApiResult<Unit>

    /** EP-05. */
    suspend fun login(email: String, authKey: SecretReader, deviceName: String): ApiResult<LoginResult>

    /** EP-06: código TOTP. */
    suspend fun verifyTwoFactor(challengeToken: String, code: String): ApiResult<Authenticated>

    /** EP-07: código de respaldo ya normalizado (8 caracteres de Crockford, sin guion). */
    suspend fun verifyRecoveryCode(challengeToken: String, recoveryCode: String): ApiResult<RecoveryLogin>

    /** EP-08: una renovación a la vez (regla 9). */
    suspend fun refresh(refreshToken: String): ApiResult<SessionTokens>

    /** EP-09: cierra la sesión actual. */
    suspend fun logout(): ApiResult<Unit>
}
