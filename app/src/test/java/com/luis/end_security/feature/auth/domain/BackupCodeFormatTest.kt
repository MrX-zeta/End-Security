package com.luis.end_security.feature.auth.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodeFormatTest {

    @Test
    fun `minusculas con espacio quedan en la forma de Crockford con guion`() {
        val normalized = BackupCodeFormat.CROCKFORD.normalize("k7qd 2m9x")
        assertEquals("K7QD2M9X", normalized)
        assertEquals("K7QD-2M9X", BackupCodeFormat.CROCKFORD.format(normalized))
    }

    @Test
    fun `O se vuelve 0 e I y L se vuelven 1`() {
        assertEquals("0011", BackupCodeFormat.CROCKFORD.normalize("OoIL"))
    }

    @Test
    fun `se quitan guiones y lo que no es del alfabeto, U incluida`() {
        assertEquals("K7QD2M9X", BackupCodeFormat.CROCKFORD.normalize("K7QD-2M9X"))
        assertEquals("AB", BackupCodeFormat.CROCKFORD.normalize("a.u#b"))
    }

    @Test
    fun `como maximo 8 caracteres`() {
        assertEquals("K7QD2M9X", BackupCodeFormat.CROCKFORD.normalize("K7QD2M9XZZ"))
    }

    @Test
    fun `el guion solo aparece despues del cuarto caracter`() {
        assertEquals("K7Q", BackupCodeFormat.CROCKFORD.format("K7Q"))
        assertEquals("K7QD", BackupCodeFormat.CROCKFORD.format("K7QD"))
        assertEquals("K7QD-2", BackupCodeFormat.CROCKFORD.format("K7QD2"))
    }

    @Test
    fun `valido solo si ya esta en la forma de Crockford`() {
        assertTrue(BackupCodeFormat.CROCKFORD.isValid("K7QD2M9X"))
        assertFalse("con guion", BackupCodeFormat.CROCKFORD.isValid("K7QD-2M9"))
        assertFalse("minusculas", BackupCodeFormat.CROCKFORD.isValid("k7qd2m9x"))
        assertFalse("con U", BackupCodeFormat.CROCKFORD.isValid("K7QD2M9U"))
        assertFalse("corto", BackupCodeFormat.CROCKFORD.isValid("K7QD2M9"))
    }

    @Test
    fun `completo con 8`() {
        assertTrue(BackupCodeFormat.CROCKFORD.isComplete("K7QD2M9X"))
        assertFalse(BackupCodeFormat.CROCKFORD.isComplete("K7QD2M9"))
    }

    @Test
    fun `formato del servidor real tal como lo entrega (BR-10)`() {
        val server = BackupCodeFormat.SERVER
        val normalized = server.normalize(" K7M2Q-x9rtb ")
        assertEquals("k7m2qx9rtb", normalized)
        assertEquals("k7m2q-x9rtb", server.format(normalized))
        assertTrue(server.isValid(normalized))
        assertFalse("con guion", server.isValid("k7m2q-x9rt"))
        assertEquals("se quitan 0, o, 1, l e i, que no estan en su alfabeto", "ab", server.normalize("a0o1lib"))
        assertEquals(10, server.normalize("abcdefghjkmnpq").length)
    }
}
