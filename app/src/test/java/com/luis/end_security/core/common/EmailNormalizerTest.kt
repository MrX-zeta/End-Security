package com.luis.end_security.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.util.Locale

class EmailNormalizerTest {

    @Test
    fun `recorta espacios y pasa a minusculas`() {
        assertEquals("ana@correo.com", EmailNormalizer.normalize("  Ana@Correo.COM \n"))
    }

    @Test
    fun `una e con acento compuesta y descompuesta quedan iguales en NFC`() {
        val composed = "josé@correo.com"
        val decomposed = "josé@correo.com"
        assertNotEquals(composed, decomposed)
        assertEquals(composed, EmailNormalizer.normalize(decomposed))
        assertEquals(EmailNormalizer.normalize(composed), EmailNormalizer.normalize(decomposed))
    }

    @Test
    fun `las mayusculas acentuadas tambien se normalizan`() {
        assertEquals("josé@correo.com", EmailNormalizer.normalize("JOSÉ@Correo.com"))
    }

    @Test
    fun `los puntos y los alias de Gmail no se quitan`() {
        assertEquals("luis.ruiz@gmail.com", EmailNormalizer.normalize("Luis.Ruiz@gmail.com"))
        assertNotEquals(EmailNormalizer.normalize("luis.ruiz@gmail.com"), EmailNormalizer.normalize("luisruiz@gmail.com"))
        assertEquals("luis+compras@gmail.com", EmailNormalizer.normalize("Luis+Compras@gmail.com"))
    }

    @Test
    fun `no depende del idioma del telefono (la I turca no cambia)`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            // Con el idioma turco, `"I".lowercase()` sin Locale.ROOT daría «ı» (i sin punto).
            assertEquals("info@correo.com", EmailNormalizer.normalize("INFO@Correo.com"))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `es idempotente`() {
        val once = EmailNormalizer.normalize("  José@Correo.COM ")
        assertEquals(once, EmailNormalizer.normalize(once))
    }
}
