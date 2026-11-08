package com.luis.end_security.feature.auth.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.luis.end_security.feature.auth.domain.UnicodeVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Con el ICU real del teléfono: las pruebas unitarias solo usan una tabla falsa. */
@RunWith(AndroidJUnit4::class)
class IcuUnicodeCharacterDataTest {

    private val unicode = IcuUnicodeCharacterData()

    @Test
    fun edad_de_caracteres_conocidos() {
        assertEquals(UnicodeVersion(1, 1), unicode.age('a'.code))
        // 🧡, de Unicode 10: el último que se acepta.
        assertEquals(UnicodeVersion(10, 0), unicode.age(0x1F9E1))
        // 🩰, de Unicode 12: si el teléfono lo conoce, su edad es posterior a 10.0.
        unicode.age(0x1FA70)?.let { assertTrue(it > UnicodeVersion.UNICODE_10) }
    }

    @Test
    fun sin_asignar_no_tiene_edad() {
        assertNull(unicode.age(0x0378))
    }

    @Test
    fun ignorables_por_defecto() {
        listOf(0x00AD, 0x180E, 0x200B, 0x3164, 0xFFA0, 0xFE00, 0xE0001, 0xE0100).forEach { codePoint ->
            assertTrue("U+%04X".format(codePoint), unicode.isDefaultIgnorable(codePoint))
        }
        listOf('a'.code, 0x2028, 0x2800, 0x1F600).forEach { codePoint ->
            assertFalse("U+%04X".format(codePoint), unicode.isDefaultIgnorable(codePoint))
        }
    }
}
