package com.luis.end_security.core.crypto

import com.luis.end_security.feature.auth.domain.MasterPasswordNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.nio.charset.CharacterCodingException

/**
 * Vector propio de la cadena completa (DEC-05, DEC-31): contraseña escrita → normalización →
 * UTF-8 → Argon2id → enc_key y auth_key. Los valores se calcularon aparte con argon2-cffi 25.1.0
 * y HKDF con `hmac` de Python, no con Bouncy Castle. Si esta prueba falla, cambió la
 * normalización, la codificación o la librería: **todas las bóvedas existentes dejarían de abrir**.
 */
class MasterKeyChainTest {

    /** «ñandú tortuga ２０２６ ❤ con U+FE0F» con la «ñ» descompuesta, dígitos de ancho completo y U+FE0F. */
    private val typed = String(
        intArrayOf(0x6E, 0x303) + "andú tortuga ".map { it.code }.toIntArray() +
            intArrayOf(0xFF12, 0xFF10, 0xFF12, 0xFF16, 0x20, 0x2764, 0xFE0F),
        0,
        2 + 13 + 7,
    )
    private val salt = ByteArray(16) { it.toByte() }
    // Fijos (no DEFAULT): si un día suben los de las cuentas nuevas, las viejas siguen con estos.
    private val params = KdfParams(memoryKiB = 65_536, iterations = 3, parallelism = 1)

    @Test
    fun `normalizacion y UTF-8`() {
        val encoded = MasterPasswordEncoder.encode(MasterPasswordNormalizer.normalize(typed))
        assertEquals("c3b1616e64c3ba20746f7274756761203230323620e29da4", encoded.toHex())
    }

    @Test
    fun `cadena completa hasta enc_key y auth_key`() {
        val password = MasterPasswordEncoder.encode(MasterPasswordNormalizer.normalize(typed))
        val masterKey = try {
            Argon2idKdf.derive(password, salt, params)
        } finally {
            password.wipe()
        }
        try {
            assertEquals("2af2fd06f7566ae126a6eafcd3240b5bcc508ff9fb3ddef6a42259a4b0f01f81", masterKey.toHex())
            assertEquals("7c90a1fd322d91ed0ac3ef53c7e8c209ce82412da51e7c6365aa92de3bd96d70", Hkdf.encKey(masterKey).toHex())
            assertEquals("dcf81e4ceb6fcf6a162b23e98558cb06236d9f68d81c5717038f46bf1b37b3ec", Hkdf.authKey(masterKey).toHex())
        } finally {
            masterKey.wipe()
        }
    }

    @Test
    fun `un surrogate solitario no se codifica, este donde este`() {
        listOf(
            "tortuga" + Char(0xD800) + "violeta",
            "tortuga-violeta" + Char(0xD83D),
            Char(0xDC00) + "tortuga-violeta",
            "tortuga" + Char(0xDE00) + Char(0xD83D) + "violeta",
        ).forEach { text ->
            assertThrows(CharacterCodingException::class.java) { MasterPasswordEncoder.encode(text) }
        }
    }

    @Test
    fun `un emoji con su pareja de surrogates si se codifica`() {
        assertEquals("f09f9490", MasterPasswordEncoder.encode(String(Character.toChars(0x1F510))).toHex())
    }

    @Test
    fun `HKDF rechaza una master_key en ceros o de otro largo`() {
        assertThrows(IllegalArgumentException::class.java) { Hkdf.encKey(ByteArray(32)) }
        assertThrows(IllegalArgumentException::class.java) { Hkdf.authKey(ByteArray(31) { 1 }) }
    }

    @Test
    fun `wipe deja el arreglo en ceros`() {
        val key = ByteArray(32) { (it + 1).toByte() }
        key.wipe()
        assertEquals(ByteArray(32).toHex(), key.toHex())
    }
}
