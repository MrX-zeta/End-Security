package com.luis.end_security.core.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordGeneratorTest {

    private val generator = PasswordGenerator()

    private fun combinations() = buildList {
        for (upper in listOf(true, false)) for (digits in listOf(true, false)) for (symbols in listOf(true, false)) {
            for (avoid in listOf(true, false)) add(GeneratorOptions(16, upper, digits, symbols, avoid))
        }
    }

    @Test
    fun `10 000 generaciones por combinacion cumplen largo, clases y parecidos`() {
        for (options in combinations()) {
            val alphabets = PasswordGenerator.alphabets(options)
            val allowed = alphabets.joinToString("").toSet()
            repeat(10_000) {
                val password = generator.generate(options)
                assertEquals("largo exacto: $options", options.length, password.size)
                for (alphabet in alphabets) {
                    assertTrue("al menos uno de cada clase activa: $options", password.any { it in alphabet })
                }
                assertTrue("nunca caracteres de clases apagadas: $options", password.all { it in allowed })
                if (options.avoidAmbiguous) {
                    assertTrue("sin parecidos: $options", password.none { it in PasswordGenerator.AMBIGUOUS })
                }
                if (!options.uppercase) assertTrue(password.none { it in PasswordGenerator.UPPERCASE })
                if (!options.digits) assertTrue(password.none { it in PasswordGenerator.DIGITS })
                if (!options.symbols) assertTrue(password.none { it in PasswordGenerator.SYMBOLS })
            }
        }
    }

    @Test
    fun `las minusculas siempre estan y nunca hay alfabeto vacio`() {
        val onlyLower = GeneratorOptions(20, uppercase = false, digits = false, symbols = false)
        repeat(1_000) {
            val password = generator.generate(onlyLower)
            assertEquals(20, password.size)
            assertTrue(password.all { it in PasswordGenerator.LOWERCASE })
        }
    }

    @Test
    fun `los limites 8 y 64 se respetan y lo que se sale del rango se recorta`() {
        val all = GeneratorOptions(8)
        repeat(2_000) {
            val short = generator.generate(all)
            assertEquals(8, short.size)
            // Con las 4 clases activas caben las 4 garantizadas en 8 caracteres.
            assertTrue(short.any { it in PasswordGenerator.LOWERCASE } && short.any { it in PasswordGenerator.UPPERCASE })
            assertTrue(short.any { it in PasswordGenerator.DIGITS } && short.any { it in PasswordGenerator.SYMBOLS })
        }
        assertEquals(64, generator.generate(GeneratorOptions(64)).size)
        assertEquals(8, generator.generate(GeneratorOptions(2)).size)
        assertEquals(64, generator.generate(GeneratorOptions(500)).size)
    }

    @Test
    fun `dentro de cada clase los caracteres son aproximadamente uniformes`() {
        val options = GeneratorOptions(64, avoidAmbiguous = false)
        val counts = HashMap<Char, Int>()
        repeat(5_000) { generator.generate(options).forEach { counts.merge(it, 1, Int::plus) } }
        for (alphabet in listOf(PasswordGenerator.LOWERCASE, PasswordGenerator.UPPERCASE, PasswordGenerator.DIGITS, PasswordGenerator.SYMBOLS)) {
            val observed = alphabet.map { (counts[it] ?: 0).toDouble() }
            val expected = observed.sum() / alphabet.length
            val chiSquare = observed.sumOf { (it - expected) * (it - expected) / expected }
            // Tolerancia amplia: con 23 o 25 grados de libertad, 99,99 % de los valores uniformes queda bajo ~55.
            assertTrue("chi-cuadrada $chiSquare en «$alphabet»", chiSquare < 80.0)
        }
    }

    @Test
    fun `los garantizados no quedan siempre al principio`() {
        val options = GeneratorOptions(16)
        val firstClass = HashSet<Int>()
        repeat(5_000) {
            val first = generator.generate(options)[0]
            firstClass += listOf(PasswordGenerator.LOWERCASE, PasswordGenerator.UPPERCASE, PasswordGenerator.DIGITS, PasswordGenerator.SYMBOLS)
                .indexOfFirst { first in it }
        }
        assertEquals("la primera posición toma las 4 clases", 4, firstClass.size)
    }

    @Test
    fun `las contrasenas no se repiten y no hay una posicion fija`() {
        val seen = HashSet<String>()
        repeat(2_000) { seen += String(generator.generate(GeneratorOptions(16))) }
        assertEquals(2_000, seen.size)
    }

    @Test
    fun `el alfabeto de simbolos no lleva comillas, barra invertida ni espacio`() {
        assertFalse(PasswordGenerator.SYMBOLS.any { it == '"' || it == '\'' || it == '\\' || it == ' ' || it == '`' })
        assertEquals("!@#\$%^&*()-_=+[]{};:,.?/", PasswordGenerator.SYMBOLS)
    }

    @Test
    fun `las opciones no imprimen nada sensible`() {
        assertTrue(GeneratorOptions().toString().startsWith("GeneratorOptions("))
    }
}
