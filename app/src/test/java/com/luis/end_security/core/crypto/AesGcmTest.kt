package com.luis.end_security.core.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Vectores de AES-256-GCM con nonce de 96 bits de McGrew y Viega, «The Galois/Counter Mode of
 * Operation (GCM)», apéndice B, casos 13 a 16 (los mismos que usa el CAVS de NIST).
 */
class AesGcmTest {

    private val key16 = hex("feffe9928665731c6d6a8f9467308308feffe9928665731c6d6a8f9467308308")
    private val nonce16 = hex("cafebabefacedbaddecaf888")
    private val plain16 = hex(
        "d9313225f88406e5a55909c5aff5269a86a7a9531534f7da2e4c303d8a318a72" +
            "1c3c0c95956809532fcf0e2449a6b525b16aedf5aa0de657ba637b39",
    )
    private val aad16 = hex("feedfacedeadbeeffeedfacedeadbeefabaddad2")

    @Test
    fun `caso 13 sin texto ni AAD`() {
        val sealed = AesGcm.sealWithNonce(ByteArray(32), ByteArray(12), ByteArray(0), ByteArray(0))
        assertEquals("530f8afbc74536b9a963b4f1c4cb738b", sealed.ciphertext.toHex())
    }

    @Test
    fun `caso 14 un bloque sin AAD`() {
        val sealed = AesGcm.sealWithNonce(ByteArray(32), ByteArray(12), ByteArray(16), ByteArray(0))
        assertEquals("cea7403d4d606b6e074ec5d3baf39d18" + "d0d1c8a799996bf0265b98b5d48ab919", sealed.ciphertext.toHex())
    }

    @Test
    fun `caso 15 texto sin AAD`() {
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
    fun `caso 16 texto con AAD, ida y vuelta`() {
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
    fun `cambiar un bit del cifrado, la etiqueta, el nonce o la AAD hace fallar open`() {
        val sealed = AesGcm.sealWithNonce(key16, nonce16, plain16, aad16)
        val tagStart = sealed.ciphertext.size - AesGcm.TAG_LENGTH
        val alterations = mapOf(
            "cifrado" to { Sealed(sealed.nonce, sealed.ciphertext.flipBit(0)) to aad16 },
            "etiqueta" to { Sealed(sealed.nonce, sealed.ciphertext.flipBit(tagStart)) to aad16 },
            "nonce" to { Sealed(sealed.nonce.flipBit(5), sealed.ciphertext) to aad16 },
            "AAD" to { sealed to aad16.flipBit(3) },
        )
        alterations.forEach { (name, alter) ->
            val (altered, aad) = alter()
            assertThrows(name, AuthenticationFailed::class.java) { AesGcm.open(key16, altered, aad) }
        }
        assertThrows("otra llave", AuthenticationFailed::class.java) {
            AesGcm.open(key16.flipBit(7), sealed, aad16)
        }
    }

    @Test
    fun `10 000 seal con la misma llave no repiten nonce`() {
        val nonces = (1..10_000).map { AesGcm.seal(key16, plain16, aad16).nonce.toHex() }.toSet()
        assertEquals(10_000, nonces.size)
    }

    @Test
    fun `seal da nonce de 12 bytes y abre con open`() {
        val sealed = AesGcm.seal(key16, plain16, aad16)
        assertEquals(12, sealed.nonce.size)
        assertEquals(plain16.size + 16, sealed.ciphertext.size)
        assertArrayEquals(plain16, AesGcm.open(key16, sealed, aad16))
    }

    @Test
    fun `tamanos invalidos se rechazan`() {
        assertThrows(IllegalArgumentException::class.java) { AesGcm.seal(ByteArray(16), plain16, aad16) }
        assertThrows(IllegalArgumentException::class.java) { Sealed(ByteArray(16), ByteArray(32)) }
    }

    @Test
    fun `un cifrado truncado es AuthenticationFailed`() {
        assertThrows(AuthenticationFailed::class.java) { AesGcm.open(key16, Sealed(nonce16, ByteArray(15)), aad16) }
        assertThrows(AuthenticationFailed::class.java) { AesGcm.open(key16, Sealed(nonce16, ByteArray(0)), aad16) }
    }

    @Test
    fun `seal y open rechazan una llave en ceros`() {
        assertThrows(IllegalArgumentException::class.java) { AesGcm.seal(ByteArray(32), plain16, aad16) }
        val sealed = AesGcm.sealWithNonce(ByteArray(32), ByteArray(12), ByteArray(0), ByteArray(0))
        assertThrows(IllegalArgumentException::class.java) { AesGcm.open(ByteArray(32), sealed, ByteArray(0)) }
    }

    @Test
    fun `toString de Sealed no muestra el contenido`() {
        val sealed = AesGcm.sealWithNonce(key16, nonce16, plain16, aad16)
        assertEquals("Sealed(nonce=12 B, ciphertext=76 B)", sealed.toString())
        assertFalse(AuthenticationFailed().toString().contains(":"))
    }

    private fun ByteArray.flipBit(byteIndex: Int): ByteArray =
        copyOf().also { it[byteIndex] = (it[byteIndex].toInt() xor 0x01).toByte() }
}
