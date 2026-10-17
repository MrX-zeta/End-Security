package com.luis.end_security.feature.auth.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodeFormatTest {

    @Test
    fun `minusculas con espacio quedan en la forma de Crockford con guion`() {
        val normalized = BackupCodeFormat.normalize("k7qd 2m9x")
        assertEquals("K7QD2M9X", normalized)
        assertEquals("K7QD-2M9X", BackupCodeFormat.format(normalized))
    }

    @Test
    fun `O se vuelve 0 e I y L se vuelven 1`() {
        assertEquals("0011", BackupCodeFormat.normalize("OoIL"))
    }

    @Test
    fun `se quitan guiones y lo que no es del alfabeto, U incluida`() {
        assertEquals("K7QD2M9X", BackupCodeFormat.normalize("K7QD-2M9X"))
        assertEquals("AB", BackupCodeFormat.normalize("a.u#b"))
    }

    @Test
    fun `como maximo 8 caracteres`() {
        assertEquals("K7QD2M9X", BackupCodeFormat.normalize("K7QD2M9XZZ"))
    }

    @Test
    fun `el guion solo aparece despues del cuarto caracter`() {
        assertEquals("K7Q", BackupCodeFormat.format("K7Q"))
        assertEquals("K7QD", BackupCodeFormat.format("K7QD"))
        assertEquals("K7QD-2", BackupCodeFormat.format("K7QD2"))
    }

    @Test
    fun `valido solo si ya esta en la forma de Crockford`() {
        assertTrue(BackupCodeFormat.isValid("K7QD2M9X"))
        assertFalse("con guion", BackupCodeFormat.isValid("K7QD-2M9"))
        assertFalse("minusculas", BackupCodeFormat.isValid("k7qd2m9x"))
        assertFalse("con U", BackupCodeFormat.isValid("K7QD2M9U"))
        assertFalse("corto", BackupCodeFormat.isValid("K7QD2M9"))
    }

    @Test
    fun `completo con 8`() {
        assertTrue(BackupCodeFormat.isComplete("K7QD2M9X"))
        assertFalse(BackupCodeFormat.isComplete("K7QD2M9"))
    }
}
