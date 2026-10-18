package com.luis.end_security.core.crypto

import java.security.SecureRandom

/** `vault_key`: 32 bytes de [SecureRandom] generados en el registro (DEC-05, INV-04). */
object VaultKey {
    const val LENGTH = 32

    private val random = SecureRandom()

    fun generate(): SecureBytes = SecureBytes.adopt(ByteArray(LENGTH).also(random::nextBytes))
}

/**
 * `protected_vault_key = AES-256-GCM(enc_key, vault_key)` (DEC-05), con
 * AAD = `"end-security/vault-key|" + userId + "|" + keyVersion` en UTF-8: un servidor no puede
 * dar la `pvk` de otra cuenta ni de otra versión de llave.
 */
object ProtectedVaultKey {
    private const val AAD_PREFIX = "end-security/vault-key|"

    fun wrap(encKey: SecureBytes, vaultKey: SecureBytes, userId: String, keyVersion: Int): Sealed {
        require(vaultKey.size == VaultKey.LENGTH) { "La vault_key debe tener ${VaultKey.LENGTH} bytes" }
        val aad = aad(userId, keyVersion)
        return encKey.use { key ->
            vaultKey.use { bytes ->
                require(bytes.any { it != 0.toByte() }) { "La vault_key está en ceros" }
                AesGcm.seal(key, bytes, aad)
            }
        }
    }

    /**
     * @throws AuthenticationFailed si la `enc_key` (es decir, la contraseña), el `userId` o la
     *   `keyVersion` no corresponden, o si la `pvk` se alteró.
     */
    fun unwrap(encKey: SecureBytes, pvk: Sealed, userId: String, keyVersion: Int): SecureBytes {
        val aad = aad(userId, keyVersion)
        val vaultKey = encKey.use { AesGcm.open(it, pvk, aad) }
        if (vaultKey.size != VaultKey.LENGTH || vaultKey.all { it == 0.toByte() }) {
            vaultKey.wipe()
            throw AuthenticationFailed()
        }
        return SecureBytes.adopt(vaultKey)
    }

    private fun aad(userId: String, keyVersion: Int): ByteArray {
        // Sin «|» en el userId, dos combinaciones distintas no pueden dar la misma AAD.
        require(userId.isNotEmpty() && '|' !in userId) { "userId inválido para la AAD" }
        require(keyVersion >= 1) { "keyVersion inválida" }
        return "$AAD_PREFIX$userId|$keyVersion".toByteArray(Charsets.UTF_8)
    }
}
