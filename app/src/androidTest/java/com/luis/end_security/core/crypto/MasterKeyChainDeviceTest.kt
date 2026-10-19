package com.luis.end_security.core.crypto

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.luis.end_security.feature.auth.domain.MasterPasswordNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.charset.CharacterCodingException

/**
 * El mismo vector de `MasterKeyChainTest`, pero en el teléfono: aquí `Normalizer` y el
 * codificador UTF-8 son los de ICU de Android, no los de la JVM. Si falla aquí y no en la JVM,
 * las llaves dependerían de la plataforma.
 */
@RunWith(AndroidJUnit4::class)
class MasterKeyChainDeviceTest {

    private val typed = String(
        intArrayOf(0x6E, 0x303) + "andú tortuga ".map { it.code }.toIntArray() +
            intArrayOf(0xFF12, 0xFF10, 0xFF12, 0xFF16, 0x20, 0x2764, 0xFE0F),
        0,
        2 + 13 + 7,
    )

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    @Test
    fun cadena_completa_igual_que_en_la_jvm() {
        val password = MasterPasswordEncoder.encode(MasterPasswordNormalizer.normalize(typed))
        assertEquals("c3b1616e64c3ba20746f7274756761203230323620e29da4", password.toHex())
        val masterKey = try {
            Argon2idKdf.derive(password, ByteArray(16) { it.toByte() }, KdfParams.DEFAULT)
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
    fun surrogate_al_final_falla_tambien_con_icu() {
        assertThrows(CharacterCodingException::class.java) {
            MasterPasswordEncoder.encode("tortuga-violeta" + Char(0xD83D))
        }
    }

    @Test
    fun selector_antes_de_nfkc_con_icu() {
        val withSelector = "A" + String(intArrayOf(0xFE0F, 0x030A), 0, 2)
        assertEquals(String(Character.toChars(0x00C5)), MasterPasswordNormalizer.normalize(withSelector))
    }
}
