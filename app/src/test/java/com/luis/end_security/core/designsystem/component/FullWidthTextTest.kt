package com.luis.end_security.core.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FullWidthTextTest {

    @Test
    fun `partir en renglones conserva el texto original al unirlo`() {
        val password = "RISEfuU=9}MuzEFhqMgEd\$R0ASG&2@lLV"
        for (perLine in listOf(1, 7, 16, 33, 64)) {
            assertEquals(password, splitInLines(password, perLine).joinToString(""))
        }
    }

    @Test
    fun `todos los renglones menos el ultimo van completos`() {
        val lines = splitInLines("a".repeat(33), 16)
        assertEquals(listOf(16, 16, 1), lines.map { it.length })
        val exact = splitInLines("a".repeat(32), 16)
        assertEquals(listOf(16, 16), exact.map { it.length })
    }

    @Test
    fun `un texto vacio da un renglon vacio y nunca falla`() {
        assertEquals(listOf(""), splitInLines("", 10))
        assertEquals(listOf("ab", "c"), splitInLines("abc", 2))
    }

    @Test
    fun `cuantos caracteres caben deja un pixel de margen y al menos uno`() {
        assertEquals(15, charsPerLine(availablePx = 160, advancePx = 10f)) // 16 justos no caben: el redondeo los sacaría
        assertEquals(16, charsPerLine(availablePx = 161, advancePx = 10f))
        assertEquals(1, charsPerLine(availablePx = 5, advancePx = 10f))
        assertEquals(1, charsPerLine(availablePx = 100, advancePx = 0f))
    }

    @Test
    fun `los grupos se reparten con tantos por renglon como quepan sin partir ninguno`() {
        val groups = "YRHLPYJNZ4BTHQ5R2FN7U5MUHTHG3U2Y".chunked(4)
        // Cada grupo mide 40 px y el espacio 10: tres grupos ocupan 140, cuatro 190.
        val widthOf = { line: String -> line.split(" ").size * 40 + (line.split(" ").size - 1) * 10 }
        val lines = packGroups(groups, availablePx = 150, widthOf = widthOf)
        assertEquals(listOf(3, 3, 2), lines.map { it.split(" ").size })
        assertEquals("YRHL PYJN Z4BT", lines.first())
        assertEquals(groups, lines.flatMap { it.split(" ") })
    }

    @Test
    fun `un grupo mas ancho que el renglon va solo en el suyo`() {
        val lines = packGroups(listOf("AAAA", "BBBB"), availablePx = 10) { 100 }
        assertEquals(listOf("AAAA", "BBBB"), lines)
    }

    @Test
    fun `sin grupos da un renglon vacio`() {
        assertEquals(listOf(""), packGroups(emptyList(), 100) { 0 })
        assertTrue(packGroups(listOf("AAAA"), 100) { 10 }.single() == "AAAA")
    }
}
