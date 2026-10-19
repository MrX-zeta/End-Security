package com.luis.end_security.core.crypto

import java.security.SecureRandom
import java.util.UUID

/** `vault_key`: 32 bytes de [SecureRandom] generados en el registro (DEC-05, INV-04). */
object VaultKey {
    const val LENGTH = 32

    private val random = SecureRandom()

    fun generate(): SecureBytes = SecureBytes.adopt(ByteArray(LENGTH).also(random::nextBytes))
}

/**
 * `protected_vault_key = AES-256-GCM(enc_key, vault_key)` (DEC-05), con
 * AAD = `"end-security/vault-key|" + userId + "|" + keyVersion` en ASCII (igual que UTF-8 aquí), con el `userId` en
 * forma canónica de UUID (minúsculas y guiones): un servidor no puede dar la `pvk` de otra
 * cuenta ni de otra versión de llave.
 */
object ProtectedVaultKey {
    private const val AAD_PREFIX = "end-security/vault-key|"

    fun wrap(encKey: SecureBytes, vaultKey: SecureBytes, userId: UUID, keyVersion: Int): Sealed {
        require(vaultKey.size == VaultKey.LENGTH) { "La vault_key debe tener ${VaultKey.LENGTH} bytes" }
        val aad = aad(userId, keyVersion)
        return encKey.withBytes { key ->
            vaultKey.withBytes { bytes ->
                require(bytes.any { it != 0.toByte() }) { "La vault_key está en ceros" }
                AesGcm.seal(key, bytes, aad)
            }
        }
    }

    /**
     * @throws AuthenticationFailed si la `enc_key` (es decir, la contraseña), el `userId` o la
     *   `keyVersion` no corresponden, o si la `pvk` se alteró.
     */
    fun unwrap(encKey: SecureBytes, pvk: Sealed, userId: UUID, keyVersion: Int): SecureBytes {
        val aad = aad(userId, keyVersion)
        val vaultKey = encKey.withBytes { AesGcm.open(it, pvk, aad) }
        if (vaultKey.size != VaultKey.LENGTH || vaultKey.all { it == 0.toByte() }) {
            vaultKey.wipe()
            throw AuthenticationFailed()
        }
        return SecureBytes.adopt(vaultKey)
    }

    private fun aad(userId: UUID, keyVersion: Int): ByteArray {
        // UUID.toString() siempre da la forma canónica (DEC-05): sin «|», así que la AAD es
        // inequívoca.
        require(keyVersion >= 1) { "keyVersion inválida" }
        return "$AAD_PREFIX$userId|$keyVersion".toByteArray(Charsets.US_ASCII)
    }
}
