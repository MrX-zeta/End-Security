package com.luis.end_security.core.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class ProtectedVaultKeyTest {

    private val userId = "0b9f3c2e-5d1a-4f7e-9a2b-6c8d1e4f7a90"

    @Test
    fun `wrap y unwrap de ida y vuelta`() {
        VaultKey.generate().let { vaultKey ->
            SecureBytes.copyOf(ByteArray(32) { 7 }).let { encKey ->
                val pvk = ProtectedVaultKey.wrap(encKey, vaultKey, userId, keyVersion = 1)
                assertEquals(12, pvk.nonce.size)
                assertEquals(48, pvk.ciphertext.size)
                val opened = ProtectedVaultKey.unwrap(encKey, pvk, userId, keyVersion = 1)
                val same = opened.use { a -> vaultKey.use { b -> a.contentEquals(b) } }
                assertEquals(true, same)
                opened.close()
                encKey.close()
            }
            vaultKey.close()
        }
    }

    @Test
    fun `unwrap con otro userId, otra version u otra enc_key falla`() {
        val vaultKey = VaultKey.generate()
        val encKey = SecureBytes.copyOf(ByteArray(32) { 7 })
        val pvk = ProtectedVaultKey.wrap(encKey, vaultKey, userId, keyVersion = 3)
        assertThrows(AuthenticationFailed::class.java) { ProtectedVaultKey.unwrap(encKey, pvk, "otro-usuario", 3) }
        assertThrows(AuthenticationFailed::class.java) { ProtectedVaultKey.unwrap(encKey, pvk, userId, 2) }
        assertThrows(AuthenticationFailed::class.java) {
            ProtectedVaultKey.unwrap(SecureBytes.copyOf(ByteArray(32) { 8 }), pvk, userId, 3)
        }
    }

    @Test
    fun `una pvk alterada o con texto que no mide 32 bytes falla`() {
        val vaultKey = VaultKey.generate()
        val encKey = SecureBytes.copyOf(ByteArray(32) { 7 })
        val pvk = ProtectedVaultKey.wrap(encKey, vaultKey, userId, keyVersion = 1)
        val altered = Sealed(pvk.nonce, pvk.ciphertext.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() })
        assertThrows(AuthenticationFailed::class.java) { ProtectedVaultKey.unwrap(encKey, altered, userId, 1) }
        // Un servidor que cifre 16 bytes con la AAD correcta: abre, pero no es una vault_key.
        val aad = "end-security/vault-key|$userId|1".toByteArray(Charsets.UTF_8)
        val short = encKey.use { AesGcm.seal(it, ByteArray(16) { 9 }, aad) }
        assertThrows(AuthenticationFailed::class.java) { ProtectedVaultKey.unwrap(encKey, short, userId, 1) }
    }

    @Test
    fun `wrap exige una vault_key de 32 bytes que no este en ceros`() {
        val encKey = SecureBytes.copyOf(ByteArray(32) { 7 })
        assertThrows(IllegalArgumentException::class.java) {
            ProtectedVaultKey.wrap(encKey, SecureBytes.copyOf(ByteArray(16) { 1 }), userId, 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ProtectedVaultKey.wrap(encKey, SecureBytes.copyOf(ByteArray(32)), userId, 1)
        }
    }

    @Test
    fun `userId con barra o vacio se rechaza`() {
        val vaultKey = VaultKey.generate()
        val encKey = SecureBytes.copyOf(ByteArray(32) { 7 })
        assertThrows(IllegalArgumentException::class.java) { ProtectedVaultKey.wrap(encKey, vaultKey, "a|1", 2) }
        assertThrows(IllegalArgumentException::class.java) { ProtectedVaultKey.wrap(encKey, vaultKey, "", 1) }
        assertThrows(IllegalArgumentException::class.java) { ProtectedVaultKey.wrap(encKey, vaultKey, userId, 0) }
    }

    @Test
    fun `VaultKey genera 32 bytes distintos cada vez`() {
        val first = VaultKey.generate()
        val second = VaultKey.generate()
        assertEquals(32, first.size)
        assertFalse(first.use { a -> second.use { b -> a.contentEquals(b) } })
    }
}
