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
            Argon2idKdf.derive(password, ByteArray(16) { it.toByte() }, KdfParams(19_456, 2, 1))
        } finally {
            password.wipe()
        }
        try {
            assertEquals("0bfa616a4c019318f582f037d12df4c2060683d95b1e2d000dc59d09339ce14d", masterKey.toHex())
            assertEquals("3d11ddea9a66613f9a9ec88743ef3100c63f6811709a24a5031f97dc8575df3a", Hkdf.encKey(masterKey).toHex())
            assertEquals("146cb3e701b532a43e32657827b4a2c3a484af1d3143a7ec1d93e75da041607a", Hkdf.authKey(masterKey).toHex())
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
