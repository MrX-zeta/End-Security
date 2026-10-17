package com.luis.end_security.core.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Test

class CodeFieldFilterTest {

    @Test
    fun `solo deja digitos`() {
        assertEquals("123", CodeFieldFilter.filter("1a2-3"))
        assertEquals("", CodeFieldFilter.filter("abc"))
    }

    @Test
    fun `como maximo 6`() {
        assertEquals("123456", CodeFieldFilter.filter("12345678"))
    }

    @Test
    fun `al pegar acepta los 6 digitos de una vez aunque traigan espacios o guiones`() {
        assertEquals("123456", CodeFieldFilter.filter("123 456"))
        assertEquals("123456", CodeFieldFilter.filter("Tu código: 123-456"))
    }

    @Test
    fun `no acepta digitos de otros alfabetos`() {
        // «١٢٣» son dígitos arábigos orientales: el servidor solo espera 0–9.
        assertEquals("", CodeFieldFilter.filter("١٢٣"))
    }
}
