package com.luis.end_security.core.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class Argon2idKdfTest {

    /** RFC 9106 §5.3: Argon2id, versión 0x13, con secreto y datos asociados. */
    @Test
    fun `vector de RFC 9106 seccion 5_3`() {
        val tag = Argon2idKdf.argon2id(
            password = ByteArray(32) { 0x01 },
            salt = ByteArray(16) { 0x02 },
            memoryKiB = 32,
            iterations = 3,
            parallelism = 4,
            outputLength = 32,
            secret = ByteArray(8) { 0x03 },
            associatedData = ByteArray(12) { 0x04 },
        )
        assertEquals("0d640df58d78766c08c037a34a8b53c9d01ef0452d75b65eb52520e96b01e659", tag.toHex())
    }

    @Test
    fun `derive da 32 bytes y no toca la contrasena ni la sal`() {
        val password = "tortuga violeta".toByteArray()
        val salt = ByteArray(16) { it.toByte() }
        val passwordCopy = password.copyOf()
        val saltCopy = salt.copyOf()
        val key = Argon2idKdf.derive(password, salt, KdfParams.DEFAULT)
        assertEquals(32, key.size)
        assertArrayEquals(passwordCopy, password)
        assertArrayEquals(saltCopy, salt)
    }

    @Test
    fun `otra sal da otra llave`() {
        val params = KdfParams.DEFAULT
        val password = "tortuga violeta".toByteArray()
        val first = Argon2idKdf.derive(password, ByteArray(16) { 1 }, params)
        val second = Argon2idKdf.derive(password, ByteArray(16) { 2 }, params)
        assertFalse(first.contentEquals(second))
    }

    @Test
    fun `la sal debe medir exactamente 16 bytes`() {
        listOf(0, 15, 17, 32).forEach { size ->
            assertThrows("sal de $size bytes", IllegalArgumentException::class.java) {
                Argon2idKdf.derive("x".toByteArray(), ByteArray(size), KdfParams.DEFAULT)
            }
        }
    }
}
