package com.luis.end_security.core.network.dto

import kotlinx.serialization.Serializable

// DTOs del contrato `docs/api/openapi.yaml` v1.0.0, con sus nombres exactos (camelCase). Los
// bytes van en Base64url sin relleno; los UUID en forma canónica; las fechas en ISO-8601 con Z.
// Un campo opcional con valor por defecto `null` no se envía; uno obligatorio que admite `null`
// (como `expectedVersion`) no tiene valor por defecto y se envía siempre.

@Serializable
class EmailRequestDto(val email: String)

@Serializable
class KdfDto(
    val algorithm: String,
    val memoryKiB: Int,
    val iterations: Int,
    val parallelism: Int,
    val salt: String,
)

@Serializable
class PreloginResponseDto(val kdf: KdfDto)

@Serializable
class ProtectedVaultKeyDto(val nonce: String, val ciphertext: String)

@Serializable
class RegisterRequestDto(
    val userId: String,
    val email: String,
    val displayName: String? = null,
    val authKey: String,
    val kdf: KdfDto,
    val protectedVaultKey: ProtectedVaultKeyDto,
)

@Serializable
class AcceptedDto(val status: String)

@Serializable
class VerifyEmailRequestDto(val email: String, val code: String, val deviceName: String)

@Serializable
class TokensDto(
    val accessToken: String,
    val accessTokenExpiresIn: Int,
    val refreshToken: String,
    val refreshTokenExpiresAt: String,
    val sessionId: String,
)

@Serializable
class AccountDto(
    val userId: String,
    val email: String,
    val emailVerified: Boolean,
    val displayName: String?,
    val keyVersion: Int,
    val kdf: KdfDto,
    val protectedVaultKey: ProtectedVaultKeyDto,
    val twoFactorEnabled: Boolean,
)

@Serializable
class LoginRequestDto(val email: String, val authKey: String, val deviceName: String)

/**
 * `Authenticated` / `TwoFactorRequired` del login (discriminados por `status`), y `Authenticated`
 * con `recoveryCodesRemaining` del código de respaldo.
 */
@Serializable
class AuthResponseDto(
    val status: String,
    val tokens: TokensDto? = null,
    val account: AccountDto? = null,
    val challengeToken: String? = null,
    val challengeExpiresIn: Int? = null,
    val recoveryCodesRemaining: Int? = null,
)

@Serializable
class TwoFactorVerifyRequestDto(val challengeToken: String, val code: String)

@Serializable
class RecoveryCodeRequestDto(val challengeToken: String, val recoveryCode: String)

@Serializable
class RefreshRequestDto(val refreshToken: String)

@Serializable
class VaultItemDto(
    val id: String,
    val version: Long,
    val revision: Long,
    val formatVersion: Int,
    val deleted: Boolean,
    val nonce: String? = null,
    val ciphertext: String? = null,
    val updatedAt: String,
)

@Serializable
class VaultChangesDto(
    val items: List<VaultItemDto>,
    val nextSince: Long,
    val hasMore: Boolean,
    val currentRevision: Long,
)

@Serializable
class PutItemRequestDto(
    val expectedVersion: Long?,
    val formatVersion: Int,
    val nonce: String,
    val ciphertext: String,
)

@Serializable
class WriteResultDto(val result: String, val version: Long, val revision: Long)

@Serializable
class UpdateAccountRequestDto(val displayName: String?)

@Serializable
class ReauthDto(val authKey: String, val totpCode: String? = null)

@Serializable
class EmailChangeRequestDto(val newEmail: String, val reauth: ReauthDto)

@Serializable
class CodeRequestDto(val code: String)

@Serializable
class ReauthRequestDto(val reauth: ReauthDto)

@Serializable
class MasterPasswordRequestDto(
    val reauth: ReauthDto,
    val expectedKeyVersion: Int,
    val newAuthKey: String,
    val newKdf: KdfDto,
    val newProtectedVaultKey: ProtectedVaultKeyDto,
)

@Serializable
class KeyVersionDto(val keyVersion: Int)

@Serializable
class TwoFactorSetupDto(
    val secret: String,
    val issuer: String,
    val accountName: String,
    val algorithm: String,
    val digits: Int,
    val periodSeconds: Int,
)

@Serializable
class RecoveryCodesDto(val recoveryCodes: List<String>)

@Serializable
class SessionDto(
    val id: String,
    val deviceName: String,
    val createdAt: String,
    val lastUsedAt: String,
    val current: Boolean,
)

@Serializable
class SessionsDto(val sessions: List<SessionDto>)

@Serializable
class ActivityEventDto(
    val id: Long,
    val occurredAt: String,
    val eventType: String,
    val outcome: String,
    val deviceName: String? = null,
)

@Serializable
class ActivityDto(val events: List<ActivityEventDto>, val hasMore: Boolean)

/** `Problem` (RFC 9457) con los campos extra del contrato. */
@Serializable
class ProblemDto(
    val type: String,
    val title: String,
    val status: Int,
    val code: String,
    val retryAfterSeconds: Int? = null,
    val attemptsRemaining: Int? = null,
    val currentVersion: Long? = null,
    val currentRevision: Long? = null,
)
