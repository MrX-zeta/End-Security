package com.luis.end_security.core.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

class BiometricWrapTest {

    private val keyStore = FakeBiometricKeyStore()
    private val userId = UUID.fromString("0b9f3c2e-5d1a-4f7e-9a2b-6c8d1e4f7a90")
    private val vaultKeyBytes = ByteArray(32) { (it * 7 + 1).toByte() }

    private fun wrap(keyVersion: Int = 1): Pair<ByteArray, ByteArray> {
        val cipher = keyStore.newEncryptCipher(userId)
        val wrapped = SecureBytes.copyOf(vaultKeyBytes).use { BiometricWrap.wrap(cipher, it, userId, keyVersion) }
        return cipher.iv to wrapped
    }

    private fun decryptCipher(iv: ByteArray): Cipher = keyStore.decryptCipher(userId, iv)

    @Test
    fun `ida y vuelta devuelve la misma vault_key`() {
        val (iv, wrapped) = wrap()
        val opened = BiometricWrap.unwrap(decryptCipher(iv), wrapped, userId, 1)
        opened.use { it.withBytes { bytes -> assertArrayEquals(vaultKeyBytes, bytes) } }
    }

    @Test
    fun `el bloque lleva la etiqueta GCM de 16 bytes`() {
        val (_, wrapped) = wrap()
        assertEquals(32 + 16, wrapped.size)
    }

    @Test
    fun `un bit alterado del cifrado no abre`() {
        val (iv, wrapped) = wrap()
        wrapped[3] = (wrapped[3].toInt() xor 1).toByte()
        assertFails { BiometricWrap.unwrap(decryptCipher(iv), wrapped, userId, 1) }
    }

    @Test
    fun `un bit alterado del IV no abre`() {
        val (iv, wrapped) = wrap()
        iv[0] = (iv[0].toInt() xor 1).toByte()
        assertFails { BiometricWrap.unwrap(decryptCipher(iv), wrapped, userId, 1) }
    }

    @Test
    fun `otra AAD no abre ni con otra version de llaves ni con otro usuario`() {
        val (iv, wrapped) = wrap(keyVersion = 1)
        assertFails { BiometricWrap.unwrap(decryptCipher(iv), wrapped, userId, 2) }
        assertFails { BiometricWrap.unwrap(decryptCipher(iv), wrapped, UUID.randomUUID(), 1) }
    }

    @Test
    fun `la AAD es end-security bio, usuario y version`() {
        assertEquals("end-security/bio|$userId|3", String(BiometricWrap.aad(userId, 3), Charsets.UTF_8))
    }

    @Test
    fun `un texto de otro largo no se toma por vault_key`() {
        val cipher = keyStore.newEncryptCipher(userId)
        cipher.updateAAD(BiometricWrap.aad(userId, 1))
        val short = cipher.doFinal(ByteArray(16))
        assertFails { BiometricWrap.unwrap(decryptCipher(cipher.iv), short, userId, 1) }
    }

    @Test
    fun `los IV de 10 000 cifrados son distintos`() {
        val ivs = HashSet<String>()
        repeat(10_000) {
            val cipher = keyStore.newEncryptCipher(userId)
            assertTrue(ivs.add(cipher.iv.joinToString(",")))
            assertEquals(12, cipher.iv.size)
        }
        assertEquals(10_000, ivs.size)
    }

    @Test
    fun `la llave de otro usuario no abre`() {
        val (iv, wrapped) = wrap()
        val other = UUID.randomUUID()
        keyStore.newEncryptCipher(other)
        val cipher = keyStore.decryptCipher(other, iv)
        assertFails { BiometricWrap.unwrap(cipher, wrapped, userId, 1) }
        assertFalse(keyStore.has(UUID.randomUUID()))
    }

    private fun assertFails(block: () -> Unit) {
        try {
            block()
            fail("debía fallar")
        } catch (_: BiometricUnwrapFailed) {
        }
    }

    @Suppress("unused")
    private fun spec(iv: ByteArray) = GCMParameterSpec(128, iv)
}
