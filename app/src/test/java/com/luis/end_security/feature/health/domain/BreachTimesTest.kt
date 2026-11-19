package com.luis.end_security.feature.health.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class BreachTimesTest {
    private val mx = Locale.forLanguageTag("es-MX")

    @Test
    fun `1 es singular y 2 es plural`() {
        assertEquals(1, BreachTimes.of(1, mx).quantity)
        assertEquals("1", BreachTimes.of(1, mx).formatted)
        assertEquals(2, BreachTimes.of(2, mx).quantity)
    }

    @Test
    fun `9999 lleva separador de miles y no se topa`() {
        val times = BreachTimes.of(9_999, mx)
        assertFalse(times.capped)
        assertEquals("9,999", times.formatted)
    }

    @Test
    fun `desde 10000 no da la cifra exacta`() {
        for (count in listOf(10_000, 42_267_808, Int.MAX_VALUE)) {
            val times = BreachTimes.of(count, mx)
            assertTrue(times.capped)
            assertEquals("10,000", times.formatted)
        }
    }

    @Test
    fun `el separador sigue al idioma`() {
        assertEquals("9.999", BreachTimes.of(9_999, Locale.GERMANY).formatted)
    }
}
