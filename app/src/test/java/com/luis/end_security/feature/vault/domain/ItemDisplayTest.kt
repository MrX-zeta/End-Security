package com.luis.end_security.feature.vault.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant

class ItemDisplayTest {

    private val now = Instant.parse("2026-10-22T12:00:00Z")

    private fun ago(duration: Duration) = EditedAge.of(now.minus(duration), now)

    @Test
    fun `editada hace usa la unidad mas grande que cabe`() {
        assertEquals(EditedAge.Now, ago(Duration.ofSeconds(59)))
        assertEquals(EditedAge.Minutes(1), ago(Duration.ofSeconds(60)))
        assertEquals(EditedAge.Minutes(59), ago(Duration.ofMinutes(59)))
        assertEquals(EditedAge.Hours(1), ago(Duration.ofMinutes(60)))
        assertEquals(EditedAge.Hours(23), ago(Duration.ofHours(23)))
        assertEquals(EditedAge.Days(1), ago(Duration.ofHours(24)))
        assertEquals(EditedAge.Days(29), ago(Duration.ofDays(29)))
        assertEquals(EditedAge.Months(1), ago(Duration.ofDays(30)))
        assertEquals(EditedAge.Months(3), ago(Duration.ofDays(95)))
        assertEquals(EditedAge.Years(1), ago(Duration.ofDays(365)))
        assertEquals(EditedAge.Years(2), ago(Duration.ofDays(800)))
    }

    @Test
    fun `una fecha futura cuenta como ahora`() {
        assertEquals(EditedAge.Now, EditedAge.of(now.plusSeconds(500), now))
    }

    @Test
    fun `el sitio sin esquema se abre con https`() {
        assertEquals("https://netflix.com", SiteUrl.toOpenable("netflix.com"))
        assertEquals("https://accounts.google.com/login", SiteUrl.toOpenable("  accounts.google.com/login "))
        assertEquals("http://intranet.local", SiteUrl.toOpenable("http://intranet.local"))
        assertEquals("https://netflix.com:8080/x", SiteUrl.toOpenable("netflix.com:8080/x"))
        assertEquals("https://localhost:3000", SiteUrl.toOpenable("localhost:3000"))
    }

    @Test
    fun `solo se abren http y https`() {
        listOf(
            "javascript:alert(1)", "JAVASCRIPT:alert(1)", "intent://scan/#Intent;scheme=zxing;end", "file:///sdcard/x",
            "content://contacts/people", "mailto:ana@correo.com", "tel:+525551234", "ftp://servidor.com", "data:text/html,hola",
        ).forEach { assertNull("«$it» no debe abrirse", SiteUrl.toOpenable(it)) }
    }

    @Test
    fun `texto vacio o con espacios o sin host no se abre`() {
        listOf(null, "", "   ", "mi sitio.com", "https://", "http:///x", "https://a b.com").forEach {
            assertNull("«$it» no debe abrirse", SiteUrl.toOpenable(it))
        }
    }
}
