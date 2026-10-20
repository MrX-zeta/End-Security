package com.luis.end_security.feature.account.domain

import com.luis.end_security.core.crypto.SecretReader
import com.luis.end_security.core.crypto.Sealed
import java.time.Instant
import java.util.UUID

/**
 * Parámetros de Argon2id tal como los manda el servidor (`KdfParams` del contrato). Sin validar:
 * quien los usa los convierte a `core.crypto.KdfParams`, que aplica el piso del cliente (DEC-05).
 */
class ServerKdf(
    val memoryKiB: Int,
    val iterations: Int,
    val parallelism: Int,
    val salt: ByteArray,
) {
    override fun toString(): String = "ServerKdf(memoryKiB=$memoryKiB, iterations=$iterations, parallelism=$parallelism)"
}

/** La cuenta (`Account` del contrato, EP-13). */
class AccountInfo(
    val userId: UUID,
    val email: String,
    val emailVerified: Boolean,
    val displayName: String?,
    val keyVersion: Int,
    val kdf: ServerKdf,
    val protectedVaultKey: Sealed,
    val twoFactorEnabled: Boolean,
) {
    override fun toString(): String = "AccountInfo(userId=$userId, keyVersion=$keyVersion, ***)"
}

/** Tokens de la sesión (DEC-16). Opacos para la app; nunca en logs. */
class SessionTokens(
    val accessToken: String,
    val accessTokenExpiresInSeconds: Int,
    val refreshToken: String,
    val refreshTokenExpiresAt: Instant,
    val sessionId: UUID,
) {
    override fun toString(): String = "SessionTokens(sessionId=$sessionId, ***)"
}

/** `Authenticated` del contrato: sesión creada. */
class Authenticated(
    val tokens: SessionTokens,
    val account: AccountInfo,
) {
    override fun toString(): String = "Authenticated(***)"
}

/** Reautenticación para operaciones sensibles (`Reauth` del contrato). */
class Reauth(
    val authKey: SecretReader,
    val totpCode: String?,
) {
    override fun toString(): String = "Reauth(***)"
}

/** Secreto TOTP pendiente de confirmar (EP-19). */
class TwoFactorSetup(
    /** Base32 en mayúsculas sin relleno. */
    val secret: String,
    val issuer: String,
    val accountName: String,
    val algorithm: String,
    val digits: Int,
    val periodSeconds: Int,
) {
    override fun toString(): String = "TwoFactorSetup(***)"
}

/** Cambio de contraseña maestra (EP-17). */
class MasterPasswordChange(
    val reauth: Reauth,
    val expectedKeyVersion: Int,
    val newAuthKey: SecretReader,
    val newKdf: ServerKdf,
    val newProtectedVaultKey: Sealed,
) {
    override fun toString(): String = "MasterPasswordChange(***)"
}
