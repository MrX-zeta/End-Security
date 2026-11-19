package com.luis.end_security.core.network

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** BR-26: la configuración de release fija exactamente dos pines SPKI para api.endsecurity.lat y no toca debug. */
class NetworkSecurityConfigTest {
    private fun res(name: String): String {
        val f = listOf("src/main/res/xml/$name", "app/src/main/res/xml/$name").map(::File).first { it.exists() }
        return f.readText()
    }

    @Test
    fun releasePinsApiHostWithTwoSha256Pins() {
        val xml = res("network_security_config.xml")
        val domain = Regex("""<domain-config[^>]*>\s*<domain[^>]*>api\.endsecurity\.lat</domain>.*?</domain-config>""", RegexOption.DOT_MATCHES_ALL)
            .find(xml)?.value ?: error("falta domain-config de api.endsecurity.lat")
        val pins = Regex("<pin digest=\"SHA-256\">([A-Za-z0-9+/]{43}=)</pin>").findAll(domain).map { it.groupValues[1] }.toList()
        assertEquals(2, pins.size)
        assertEquals(2, pins.toSet().size)
        assertTrue(domain.contains("expiration=\"2027-12-31\""))
    }

    @Test
    fun debugConfigHasNoPins() {
        val f = listOf("src/debug/res/xml/network_security_config.xml", "app/src/debug/res/xml/network_security_config.xml").map(::File).first { it.exists() }
        assertFalse(f.readText().contains("<pin "))
    }
}
