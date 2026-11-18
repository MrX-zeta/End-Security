package com.luis.end_security.core.network.backend

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// Forma del backend real (end-security-backend, rama integracion/app). Solo la usa el traductor de `prod`.
// Los bytes van en Base64 estándar; las fechas en ISO-8601. Ver specs/001-adaptador-backend-real/contracts/translation.md.

/** Error de Nest (`HttpExceptionFilter`): el `error` es el nombre que se traduce a `code`. */
@Serializable
internal class BeError(
    val statusCode: Int = 0,
    val error: String = "",
    val details: JsonObject? = null,
)

@Serializable
internal class BeKdf(val salt: String, val memoryKib: Int, val iterations: Int, val parallelism: Int)

@Serializable
internal class BePvk(val ciphertext: String, val nonce: String)

@Serializable
internal class BePrelogin(val kdf: BeKdf)

@Serializable
internal class BeRegister(
    val userId: String,
    val email: String,
    val displayName: String? = null,
    val authHash: String,
    val kdf: BeKdf,
    val pvk: BePvk,
    val deviceName: String,
    val marketingOptIn: Boolean,
)

@Serializable
internal class BeLogin(val email: String, val authHash: String, val deviceName: String)

@Serializable
internal class BeTokens(val accessToken: String, val expiresIn: Int, val refreshToken: String)

@Serializable
internal class BeUser(
    val id: String,
    val email: String,
    val displayName: String = "",
    val emailVerified: Boolean,
    val twoFactorEnabled: Boolean,
    val marketingOptIn: Boolean = false,
)

@Serializable
internal class BeKeys(val kdf: BeKdf, val pvk: BePvk, val keyVersion: Int)

/** Las tres respuestas del login, de la verificación del correo y del segundo paso (BR-09). */
@Serializable
internal class BeAuth(
    val requiresEmailVerification: Boolean = false,
    val requires2FA: Boolean = false,
    val challengeToken: String? = null,
    val tokens: BeTokens? = null,
    val user: BeUser? = null,
    val keys: BeKeys? = null,
)

@Serializable
internal class BeChallenge(val challengeToken: String)

@Serializable
internal class BeChallengeCode(val challengeToken: String, val code: String)

@Serializable
internal class BeTwoFactorLogin(val challengeToken: String, val code: String? = null, val recoveryCode: String? = null)

@Serializable
internal class BeRefresh(val refreshToken: String)

@Serializable
internal class BeReauth(val authHash: String, val code: String? = null)

@Serializable
internal class BeAuthHash(val authHash: String)

@Serializable
internal class BeCode(val code: String)

@Serializable
internal class BeEmailChange(val newEmail: String, val authHash: String, val code: String? = null)

@Serializable
internal class BePasswordChange(
    val currentAuthHash: String,
    val newAuthHash: String,
    val kdf: BeKdf,
    val pvk: BePvk,
    val code: String? = null,
    val expectedKeyVersion: Int,
)

@Serializable
internal class BeKeyVersion(val keyVersion: Int)

@Serializable
internal class BeTwoFactorSetup(val secret: String, val otpauthUrl: String)

@Serializable
internal class BeTwoFactorStatus(val recoveryCodesRemaining: Int)

@Serializable
internal class BeRecoveryCodes(val recoveryCodes: List<String>)

@Serializable
internal class BeSession(
    val id: String,
    val deviceName: String,
    val createdAt: String,
    val lastUsedAt: String? = null,
    val current: Boolean,
)

@Serializable
internal class BeAuditItem(
    val id: Long,
    val eventType: String,
    val outcome: String,
    val occurredAt: String,
    val metadata: JsonObject? = null,
    val deviceName: String? = null,
)

@Serializable
internal class BeAuditPage(val items: List<BeAuditItem>, val nextCursor: Long? = null)

@Serializable
internal class BeVaultItem(
    val id: String,
    val version: Long,
    val revision: Long,
    val deleted: Boolean,
    val nonce: String? = null,
    val ciphertext: String? = null,
    val formatVersion: Int,
    val updatedAt: String,
)

@Serializable
internal class BeVaultSync(
    val revision: Long,
    val keyVersion: Int,
    val fullResync: Boolean,
    val items: List<BeVaultItem>,
)

@Serializable
internal class BeVaultChange(
    val id: String,
    val baseVersion: Long,
    val deleted: Boolean? = null,
    val nonce: String? = null,
    val ciphertext: String? = null,
    val formatVersion: Int? = null,
)

@Serializable
internal class BeVaultPush(val items: List<BeVaultChange>)

@Serializable
internal class BeApplied(val id: String, val version: Long, val revision: Long)

@Serializable
internal class BeVaultPushed(val revision: Long, val applied: List<BeApplied>)
