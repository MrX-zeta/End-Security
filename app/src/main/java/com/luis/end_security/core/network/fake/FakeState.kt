package com.luis.end_security.core.network.fake

import kotlinx.serialization.Serializable

// Estado del backend falso, guardado en `filesDir/fake_server.json`. Solo contiene lo que
// guardaría el servidor real (`docs/bd/schema.sql`): hashes, sal, parámetros, pvk y bloques
// cifrados; nunca la contraseña, la authKey, tokens ni códigos en claro. Bytes en Base64url.

@Serializable
class FakeState(
    /** Secreto de la sal falsa del prelogin (DEC-07). */
    val preloginSecret: String,
    /** Pepper de los códigos cortos (DEC-43). */
    val pepper: String,
    val users: MutableMap<String, FakeUser> = mutableMapOf(),
    val items: MutableMap<String, FakeItem> = mutableMapOf(),
    val sessions: MutableMap<String, FakeSession> = mutableMapOf(),
    /** Retos del 2FA por SHA-256 del `challengeToken`. */
    val challenges: MutableMap<String, FakeChallenge> = mutableMapOf(),
    /** Límites de intentos por HMAC del correo (sin correos en claro, DEC-32). */
    val loginLimits: MutableMap<String, FakeLimit> = mutableMapOf(),
    val events: MutableList<FakeEvent> = mutableListOf(),
    var nextEventId: Long = 1,
)

@Serializable
class FakeUser(
    val id: String,
    var email: String,
    var emailVerified: Boolean,
    var displayName: String?,
    var authSalt: String,
    var authHash: String,
    var kdf: FakeKdf,
    var pvkNonce: String,
    var pvkCiphertext: String,
    var keyVersion: Int = 1,
    var vaultRevision: Long = 0,
    var purgedThroughRevision: Long = 0,
    var deleted: Boolean = false,
    var verification: FakeVerification? = null,
    var twoFactor: FakeTwoFactor? = null,
    var twoFactorFailures: Int = 0,
    var twoFactorLockedUntil: Long = 0,
    /** Envíos de correo de verificación (epoch ms) de la última hora. */
    val resends: MutableList<Long> = mutableListOf(),
)

@Serializable
class FakeKdf(val memoryKiB: Int, val iterations: Int, val parallelism: Int, val salt: String)

@Serializable
class FakeVerification(
    val codeHash: String,
    var attempts: Int,
    val expiresAt: Long,
)

@Serializable
class FakeTwoFactor(
    /** Base32. */
    val secret: String,
    var enabled: Boolean,
    var lastUsedStep: Long? = null,
    /** HMAC con pepper de los códigos de respaldo sin usar (DEC-12, DEC-43). */
    val recoveryCodeHashes: MutableList<String> = mutableListOf(),
)

@Serializable
class FakeItem(
    val id: String,
    val userId: String,
    var version: Long,
    var revision: Long,
    var formatVersion: Int,
    var nonce: String?,
    var ciphertext: String?,
    var deleted: Boolean,
    var updatedAt: Long,
)

@Serializable
class FakeSession(
    val id: String,
    val userId: String,
    val deviceName: String,
    val createdAt: Long,
    var lastUsedAt: Long,
    var accessHash: String,
    var accessExpiresAt: Long,
    var refreshHash: String,
    var refreshExpiresAt: Long,
    /** Refresh ya rotados: reusarlos revoca la sesión (DEC-16). */
    val oldRefreshHashes: MutableList<String> = mutableListOf(),
    var revoked: Boolean = false,
)

@Serializable
class FakeChallenge(
    val userId: String,
    val deviceName: String,
    val expiresAt: Long,
)

@Serializable
class FakeLimit(var failures: Int = 0, var lockedUntil: Long = 0)

@Serializable
class FakeEvent(
    val id: Long,
    val userId: String,
    val occurredAt: Long,
    val eventType: String,
    val success: Boolean,
    val deviceName: String?,
)
