package com.luis.end_security.feature.vault.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.UUID

class ItemSearchTest {

    private fun item(name: String, username: String = "", url: String? = null) =
        VaultItem(name, username, "x", url, null, Instant.EPOCH, Instant.EPOCH)

    private fun entry(name: String) = ReadableEntry(UUID.randomUUID(), 1, item(name))

    @Test
    fun `busca sin acentos ni mayusculas`() {
        val gmail = item("Gmail")
        assertTrue(ItemSearch.matches(gmail, "gmáil"))
        assertTrue(ItemSearch.matches(gmail, "GMAIL"))
        assertTrue(ItemSearch.matches(item("Café Ñandú"), "cafe nandu".substringBefore(' ')))
        assertTrue(ItemSearch.matches(item("Café"), "CAFÉ"))
        assertFalse(ItemSearch.matches(gmail, "amazon"))
    }

    @Test
    fun `busca por nombre, usuario y sitio`() {
        val netflix = item("Netflix", username = "luis.ruiz@gmail.com", url = "https://www.netflix.com")
        assertTrue(ItemSearch.matches(netflix, "net"))
        assertTrue(ItemSearch.matches(netflix, "RUIZ"))
        assertTrue(ItemSearch.matches(netflix, "netflix.com"))
        assertFalse("las notas y la contraseña no cuentan", ItemSearch.matches(netflix, "zzz"))
    }

    @Test
    fun `una consulta vacia o en blanco muestra todo`() {
        assertTrue(ItemSearch.matches(item("Gmail"), ""))
        assertTrue(ItemSearch.matches(item("Gmail"), "   "))
    }

    @Test
    fun `el orden es alfabetico sin acentos ni mayusculas`() {
        val sorted = ItemSearch.sorted(listOf(entry("zoom"), entry("Éter"), entry("amazon"), entry("Bbva"), entry("etsy")))
        assertEquals(listOf("amazon", "Bbva", "Éter", "etsy", "zoom"), sorted.map { (it as ReadableEntry).item.name })
    }

    @Test
    fun `los danados y los de otra version van al final`() {
        val damaged = DamagedEntry(UUID.randomUUID(), 1)
        val newer = UnsupportedEntry(UUID.randomUUID(), 1, 2)
        val sorted = ItemSearch.sorted(listOf(damaged, entry("Zeta"), newer, entry("Alfa")))
        assertEquals("Alfa", (sorted[0] as ReadableEntry).item.name)
        assertEquals("Zeta", (sorted[1] as ReadableEntry).item.name)
        assertTrue(sorted.drop(2).all { it !is ReadableEntry })
    }
}
