package com.luis.end_security.feature.auth.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForbiddenCharactersTest {

    private fun forbidden(raw: String) =
        MasterPasswordPolicy.hasForbiddenCharacters(MasterPasswordNormalizer.normalize(raw))

    @Test
    fun `una frase normal se acepta`() {
        assertFalse(forbidden("tortuga violeta salta 29 nubes"))
    }

    @Test
    fun `espacio de ancho cero U+200B se rechaza`() {
        assertTrue(forbidden("tortuga\u200Bvioleta-salta"))
    }

    @Test
    fun `marca de direccion U+202E se rechaza`() {
        assertTrue(forbidden("tortuga\u202Evioleta-salta"))
    }

    @Test
    fun `control U+0007 se rechaza`() {
        assertTrue(forbidden("tortuga\u0007violeta-salta"))
    }

    @Test
    fun `los demas invisibles de la lista se rechazan`() {
        listOf(0x2060, 0xFEFF, 0x200E, 0x200F, 0x202A, 0x202C, 0x2066, 0x2069, 0x0000, 0x009F).forEach { codePoint ->
            assertTrue("U+%04X".format(codePoint), forbidden("tortuga" + String(Character.toChars(codePoint))))
        }
    }

    @Test
    fun `emoji compuesto con ZWJ se acepta`() {
        assertFalse(forbidden("familia 👨\u200D👩\u200D👧 en casa")) // 👨\u200D👩\u200D👧
    }

    @Test
    fun `corazon con FE0F se acepta`() {
        assertFalse(forbidden("te quiero ❤\uFE0F mucho")) // ❤\uFE0F
    }

    @Test
    fun `un punto de codigo sin asignar se rechaza`() {
        // U+0378 no está asignado en ninguna versión de Unicode hasta hoy.
        assertTrue(forbidden("tortuga\u0378violeta"))
    }
}
