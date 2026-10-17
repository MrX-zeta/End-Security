package com.luis.end_security.feature.auth.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Los caracteres invisibles se arman con [cp] y no se escriben en el código: un editor podría
 * guardarlos como caracteres reales que no se ven (U+202E es el truco «Trojan Source»).
 */
class ForbiddenCharactersTest {

    private val unicode = FakeUnicodeCharacterData(
        ages = mapOf(BALLET_SHOES to UnicodeVersion(12, 0), SEGMENTED_DIGIT_ZERO to UnicodeVersion(13, 0)),
    )

    private fun forbidden(raw: String) =
        MasterPasswordPolicy.hasForbiddenCharacters(raw, MasterPasswordNormalizer.normalize(raw), unicode)

    private fun cp(vararg codePoints: Int): String = String(codePoints, 0, codePoints.size)

    @Test
    fun `una frase normal se acepta`() {
        assertFalse(forbidden("tortuga violeta salta 29 nubes"))
    }

    @Test
    fun `espacio de ancho cero U+200B se rechaza`() {
        assertTrue(forbidden("tortuga" + cp(0x200B) + "violeta-salta"))
    }

    @Test
    fun `marca de direccion U+202E se rechaza`() {
        assertTrue(forbidden("tortuga" + cp(0x202E) + "violeta-salta"))
    }

    @Test
    fun `control U+0007 se rechaza`() {
        assertTrue(forbidden("tortuga" + cp(0x0007) + "violeta-salta"))
    }

    @Test
    fun `los demas invisibles se rechazan`() {
        listOf(0x2060, 0xFEFF, 0x200E, 0x200F, 0x202A, 0x202C, 0x2066, 0x2069, 0x0000, 0x009F, 0x034F, 0x061C)
            .forEach { codePoint ->
                assertTrue("U+%04X".format(codePoint), forbidden("tortuga" + cp(codePoint)))
            }
    }

    @Test
    fun `guion suave, rellenos hangul y selector de variacion se rechazan`() {
        // U+3164 pasa a U+1160 con NFKC, que también es ignorable.
        listOf(0x00AD, 0x115F, 0x3164, 0xFE00).forEach { codePoint ->
            assertTrue("U+%04X".format(codePoint), forbidden("tortuga" + cp(codePoint) + "violeta"))
        }
    }

    @Test
    fun `separadores de linea y parrafo y braille vacio se rechazan`() {
        listOf(0x2028, 0x2029, 0x2800).forEach { codePoint ->
            assertTrue("U+%04X".format(codePoint), forbidden("tortuga" + cp(codePoint) + "violeta"))
        }
    }

    @Test
    fun `un surrogate solitario se rechaza`() {
        assertTrue(forbidden("tortuga" + Char(0xD800) + "violeta"))
        assertTrue(forbidden("tortuga" + Char(0xDC00)))
    }

    @Test
    fun `un caracter asignado despues de Unicode 10 se rechaza`() {
        assertTrue(forbidden("zapatillas " + cp(BALLET_SHOES) + " de ballet"))
    }

    @Test
    fun `un caracter nuevo que NFKC vuelve antiguo se rechaza`() {
        // U+1FBF0 (Unicode 13) pasa a «0» con NFKC solo en los teléfonos que lo conocen.
        assertTrue(forbidden("clave-" + cp(SEGMENTED_DIGIT_ZERO) + "-tortuga"))
    }

    @Test
    fun `una etiqueta de idioma U+E0001 se rechaza`() {
        assertTrue(forbidden("tortuga" + cp(0xE0001) + "violeta"))
    }

    @Test
    fun `un punto de codigo sin asignar se rechaza`() {
        // U+0378 no está asignado en ninguna versión de Unicode hasta hoy.
        assertTrue(forbidden("tortuga" + cp(0x0378) + "violeta"))
    }

    @Test
    fun `emoji compuesto con ZWJ se acepta`() {
        assertFalse(forbidden("familia " + cp(0x1F468, 0x200D, 0x1F469, 0x200D, 0x1F467) + " en casa"))
    }

    @Test
    fun `corazon con FE0F se acepta`() {
        // La normalización quita el selector antes de revisar.
        assertFalse(forbidden("te quiero " + cp(0x2764, 0xFE0F) + " mucho"))
    }

    @Test
    fun `bandera de Mexico se acepta`() {
        assertFalse(forbidden("viva " + cp(0x1F1F2, 0x1F1FD) + " siempre"))
    }

    @Test
    fun `bandera con etiquetas se acepta`() {
        // Escocia: bandera negra + etiquetas «gbsct» + fin de etiqueta.
        val scotland = cp(0x1F3F4, 0xE0067, 0xE0062, 0xE0073, 0xE0063, 0xE0074, 0xE007F)
        assertFalse(forbidden("gaitas de $scotland en la tarde"))
    }

    @Test
    fun `U+200C del persa se acepta`() {
        assertFalse(forbidden("mi" + cp(0x200C) + "khaham-salam-29"))
    }

    private companion object {
        /** 🩰, de Unicode 12. */
        const val BALLET_SHOES = 0x1FA70

        /** Dígito cero segmentado, de Unicode 13; NFKC lo convierte en «0». */
        const val SEGMENTED_DIGIT_ZERO = 0x1FBF0
    }
}
