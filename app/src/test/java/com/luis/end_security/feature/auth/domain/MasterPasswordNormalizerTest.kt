package com.luis.end_security.feature.auth.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MasterPasswordNormalizerTest {

    // «contraseña» con la ñ compuesta (NFC) y como «n» + tilde combinante (NFD).
    private val nfc = "contraseña"
    private val nfd = "contraseña"

    @Test
    fun `NFC y NFD dan el mismo texto normalizado`() {
        assertEquals(MasterPasswordNormalizer.normalize(nfc), MasterPasswordNormalizer.normalize(nfd))
    }

    @Test
    fun `NFC y NFD tienen la misma longitud`() {
        val nfcLength = MasterPasswordNormalizer.codePointLength(MasterPasswordNormalizer.normalize(nfc))
        val nfdLength = MasterPasswordNormalizer.codePointLength(MasterPasswordNormalizer.normalize(nfd))
        assertEquals(10, nfcLength)
        assertEquals(nfcLength, nfdLength)
    }

    @Test
    fun `un emoji cuenta como 1`() {
        val emoji = "🔐" // 🔐: dos unidades UTF-16, un punto de código
        assertEquals(2, emoji.length)
        assertEquals(1, MasterPasswordNormalizer.codePointLength(MasterPasswordNormalizer.normalize(emoji)))
    }

    @Test
    fun `11 caracteres con uno compuesto no cumplen el minimo`() {
        val elevenWithCombining = "contraseña1" // 12 unidades UTF-16, 11 caracteres
        assertEquals(12, elevenWithCombining.length)
        val length = MasterPasswordNormalizer.codePointLength(MasterPasswordNormalizer.normalize(elevenWithCombining))
        assertEquals(11, length)
        assertFalse(MasterPasswordPolicy.meetsLength(length))
    }

    @Test
    fun `12 caracteres con uno compuesto si cumplen el minimo`() {
        val twelve = "contraseña12"
        val length = MasterPasswordNormalizer.codePointLength(MasterPasswordNormalizer.normalize(twelve))
        assertTrue(MasterPasswordPolicy.meetsLength(length))
    }

    @Test
    fun `NFKC puede expandir un caracter y pasar el maximo`() {
        val expanding = "ﷺ".repeat(MasterPasswordPolicy.MAX_LENGTH) // «ﷺ»: 1 unidad, 18 tras NFKC
        assertEquals(MasterPasswordPolicy.MAX_LENGTH, expanding.length)
        val length = MasterPasswordNormalizer.codePointLength(MasterPasswordNormalizer.normalize(expanding))
        assertTrue(MasterPasswordPolicy.exceedsMaxLength(length))
        assertFalse(MasterPasswordPolicy.isAcceptable(length, score = 4))
    }

    @Test
    fun `128 emojis caben y 129 se pasan`() {
        val emoji = "🔐"
        val ok = MasterPasswordNormalizer.codePointLength(MasterPasswordNormalizer.normalize(emoji.repeat(128)))
        val tooLong = MasterPasswordNormalizer.codePointLength(MasterPasswordNormalizer.normalize(emoji.repeat(129)))
        assertFalse(MasterPasswordPolicy.exceedsMaxLength(ok))
        assertTrue(MasterPasswordPolicy.isAcceptable(ok, score = 4))
        assertTrue(MasterPasswordPolicy.exceedsMaxLength(tooLong))
        assertTrue("el campo deja escribir 128 emojis", emoji.repeat(128).length <= MasterPasswordPolicy.MAX_FIELD_LENGTH)
    }

    @Test
    fun `NFKC unifica variantes de compatibilidad como los digitos de ancho completo`() {
        assertEquals("2026", MasterPasswordNormalizer.normalize("２０２６"))
    }
}
