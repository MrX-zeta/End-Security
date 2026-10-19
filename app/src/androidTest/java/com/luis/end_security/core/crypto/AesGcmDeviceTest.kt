package com.luis.end_security.core.crypto

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import javax.crypto.Cipher

/**
 * Los vectores de `AesGcmTest` con el proveedor del teléfono (Conscrypt), que no es el de la JVM.
 */
@RunWith(AndroidJUnit4::class)
class AesGcmDeviceTest {

    private fun hex(value: String) = ByteArray(value.length / 2) { value.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

    private val key16 = hex("feffe9928665731c6d6a8f9467308308feffe9928665731c6d6a8f9467308308")
    private val nonce16 = hex("cafebabefacedbaddecaf888")
    private val plain16 = hex(
        "d9313225f88406e5a55909c5aff5269a86a7a9531534f7da2e4c303d8a318a72" +
            "1c3c0c95956809532fcf0e2449a6b525b16aedf5aa0de657ba637b39",
    )
    private val aad16 = hex("feedfacedeadbeeffeedfacedeadbeefabaddad2")

    @Test
    fun el_proveedor_es_conscrypt() {
        // Si alguien registra otro proveedor (p. ej. Bouncy Castle) antes, estas pruebas dejarían
        // de probar Conscrypt sin avisar.
        assertEquals("AndroidOpenSSL", Cipher.getInstance("AES/GCM/NoPadding").provider.name)
    }

    @Test
    fun caso_15_texto_sin_aad() {
        val plain = hex(
            "d9313225f88406e5a55909c5aff5269a86a7a9531534f7da2e4c303d8a318a72" +
                "1c3c0c95956809532fcf0e2449a6b525b16aedf5aa0de657ba637b391aafd255",
        )
        val sealed = AesGcm.sealWithNonce(key16, nonce16, plain, ByteArray(0))
        assertEquals(
            "522dc1f099567d07f47f37a32a84427d643a8cdcbfe5c0c97598a2bd2555d1aa" +
                "8cb08e48590dbb3da7b08b1056828838c5f61e6393ba7a0abcc9f662898015ad" +
                "b094dac5d93471bdec1a502270e3cc6c",
            sealed.ciphertext.toHex(),
        )
    }

    @Test
    fun caso_13_sin_texto_ni_aad() {
        val sealed = AesGcm.sealWithNonce(ByteArray(32), ByteArray(12), ByteArray(0), ByteArray(0))
        assertEquals("530f8afbc74536b9a963b4f1c4cb738b", sealed.ciphertext.toHex())
    }

    @Test
    fun caso_14_un_bloque_sin_aad() {
        val sealed = AesGcm.sealWithNonce(ByteArray(32), ByteArray(12), ByteArray(16), ByteArray(0))
        assertEquals("cea7403d4d606b6e074ec5d3baf39d18d0d1c8a799996bf0265b98b5d48ab919", sealed.ciphertext.toHex())
    }

    @Test
    fun caso_16_texto_con_aad_ida_y_vuelta() {
        val sealed = AesGcm.sealWithNonce(key16, nonce16, plain16, aad16)
        assertEquals(
            "522dc1f099567d07f47f37a32a84427d643a8cdcbfe5c0c97598a2bd2555d1aa" +
                "8cb08e48590dbb3da7b08b1056828838c5f61e6393ba7a0abcc9f662" +
                "76fc6ece0f4e1768cddf8853bb2d551b",
            sealed.ciphertext.toHex(),
        )
        assertArrayEquals(plain16, AesGcm.open(key16, sealed, aad16))
    }

    @Test
    fun alterar_un_bit_falla_con_conscrypt() {
        val sealed = AesGcm.sealWithNonce(key16, nonce16, plain16, aad16)
        fun ByteArray.flip(i: Int) = copyOf().also { it[i] = (it[i].toInt() xor 1).toByte() }
        val last = sealed.ciphertext.size - 1
        assertThrows("etiqueta", AuthenticationFailed::class.java) { AesGcm.open(key16, Sealed(nonce16, sealed.ciphertext.flip(last)), aad16) }
        assertThrows("cifrado", AuthenticationFailed::class.java) { AesGcm.open(key16, Sealed(nonce16, sealed.ciphertext.flip(0)), aad16) }
        assertThrows("nonce", AuthenticationFailed::class.java) { AesGcm.open(key16, Sealed(nonce16.flip(5), sealed.ciphertext), aad16) }
        assertThrows("AAD", AuthenticationFailed::class.java) { AesGcm.open(key16, sealed, aad16.flip(3)) }
    }

    @Test
    fun pvk_de_ida_y_vuelta_en_el_telefono() {
        val vaultKey = VaultKey.generate()
        val encKey = SecureBytes.copyOf(ByteArray(32) { 7 })
        val userId = UUID.fromString("0b9f3c2e-5d1a-4f7e-9a2b-6c8d1e4f7a90")
        val pvk = ProtectedVaultKey.wrap(encKey, vaultKey, userId, 1)
        val opened = ProtectedVaultKey.unwrap(encKey, pvk, userId, 1)
        assertEquals(true, opened.withBytes { a -> vaultKey.withBytes { b -> a.contentEquals(b) } })
        assertThrows(AuthenticationFailed::class.java) { ProtectedVaultKey.unwrap(encKey, pvk, userId, 2) }
    }
}
