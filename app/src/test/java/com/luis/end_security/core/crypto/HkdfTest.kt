package com.luis.end_security.core.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** RFC 5869, apéndice A, casos 1 a 3 (SHA-256). */
class HkdfTest {

    @Test
    fun `caso 1 de RFC 5869`() {
        val okm = Hkdf.sha256(
            ikm = ByteArray(22) { 0x0b },
            salt = bytesRange(0x00, 0x0c),
            info = bytesRange(0xf0, 0xf9),
            length = 42,
        )
        assertEquals(
            "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865",
            okm.toHex(),
        )
    }

    @Test
    fun `caso 2 de RFC 5869`() {
        val okm = Hkdf.sha256(
            ikm = bytesRange(0x00, 0x4f),
            salt = bytesRange(0x60, 0xaf),
            info = bytesRange(0xb0, 0xff),
            length = 82,
        )
        assertEquals(
            "b11e398dc80327a1c8e7f78c596a49344f012eda2d4efad8a050cc4c19afa97c" +
                "59045a99cac7827271cb41c65e590e09da3275600c2f09b8367793a9aca3db71" +
                "cc30c58179ec3e87c14c01d5c1f3434f1d87",
            okm.toHex(),
        )
    }

    /** Sin sal ni info: el mismo modo que usan encKey y authKey (sin sal). */
    @Test
    fun `caso 3 de RFC 5869`() {
        val okm = Hkdf.sha256(ikm = ByteArray(22) { 0x0b }, salt = null, info = ByteArray(0), length = 42)
        assertEquals(
            "8da4e775a563c18f715f802a063c5a31b8a11f5c5ee1879ec3454e5f3c738d2d9d201395faa4b61a96c8",
            okm.toHex(),
        )
    }

    @Test
    fun `una sal vacia equivale a no tener sal`() {
        val ikm = ByteArray(22) { 0x0b }
        assertEquals(
            Hkdf.sha256(ikm, salt = null, info = ByteArray(0), length = 42).toHex(),
            Hkdf.sha256(ikm, salt = ByteArray(0), info = ByteArray(0), length = 42).toHex(),
        )
    }

    @Test
    fun `enc_key y auth_key son distintas`() {
        val masterKey = ByteArray(32) { it.toByte() }
        val enc = Hkdf.encKey(masterKey)
        val auth = Hkdf.authKey(masterKey)
        assertEquals(32, enc.size)
        assertEquals(32, auth.size)
        assertFalse(enc.contentEquals(auth))
    }
}
