package com.luis.end_security.feature.auth.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ZxcvbnPasswordStrengthEstimatorTest {

    private val estimator = ZxcvbnPasswordStrengthEstimator()

    @Test
    fun `una contrasena vacia vale cero`() {
        assertEquals(0, estimator.score(""))
    }

    @Test
    fun `las contrasenas comunes son debiles`() {
        listOf("password", "123456789012", "qwertyuiop12").forEach {
            assertTrue("«$it» debería puntuar 0–1", estimator.score(it) <= 1)
        }
    }

    @Test
    fun `una frase larga y poco comun es fuerte`() {
        assertTrue(estimator.score("tortuga-violeta-salta-29-nubes") >= 3)
    }

    @Test
    fun `acepta CharSequence sin convertirla a String`() {
        val buffer = StringBuilder("tortuga-violeta-salta-29-nubes")
        assertTrue(estimator.score(buffer) >= 3)
    }

    @Test
    fun `el puntaje siempre esta entre 0 y 4`() {
        listOf("a", "abc123", "Tr0ub4dor&3", "correct horse battery staple").forEach {
            assertTrue(estimator.score(it) in 0..4)
        }
    }
}
