package com.luis.end_security.feature.settings.ui.legal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Datos reales (E4, tarea 23): ningún texto que la app muestre debe traer los marcadores de antes. El único que puede quedar es el
 * del proveedor de hosting, hasta que el backend esté desplegado.
 */
class LegalPlaceholdersTest {

    private val forbidden = listOf("[privacidad@correo.com]", "[Nombre completo")

    private fun file(vararg candidates: String): File = candidates.map { File(it) }.first { it.exists() }

    private fun shownTexts(): Map<String, String> = buildMap {
        put("aviso-de-privacidad.md", file("../docs/legal/aviso-de-privacidad.md", "docs/legal/aviso-de-privacidad.md").readText(Charsets.UTF_8))
        put("terminos-y-condiciones.md", file("../docs/legal/terminos-y-condiciones.md", "docs/legal/terminos-y-condiciones.md").readText(Charsets.UTF_8))
        put("strings.xml", file("src/main/res/values/strings.xml", "app/src/main/res/values/strings.xml").readText(Charsets.UTF_8))
        val licenses = file("src/main/assets/licenses", "app/src/main/assets/licenses")
        licenses.walkTopDown().filter { it.isFile }.forEach { put("licenses/${it.name}", it.readText(Charsets.UTF_8)) }
    }

    @Test
    fun `ningun texto de la app trae los marcadores de privacidad ni de nombres`() {
        for ((name, text) in shownTexts()) {
            for (marker in forbidden) assertFalse("$name todavía trae «$marker»", text.contains(marker))
        }
    }

    @Test
    fun `los documentos nombran a los responsables y el canal de privacidad`() {
        val aviso = shownTexts().getValue("aviso-de-privacidad.md")
        assertTrue(aviso.contains("Brian Luis Ruiz Pérez"))
        assertTrue(aviso.contains("Diego Alberto Zárate Vázquez"))
        assertTrue(aviso.contains(LegalInfo.PRIVACY_EMAIL))
    }

    @Test
    fun `los correos de LegalInfo son los del equipo`() {
        assertEquals("endsecurity.privacidad@gmail.com", LegalInfo.PRIVACY_EMAIL)
        assertEquals("endsecurity.soporte@gmail.com", LegalInfo.SUPPORT_EMAIL)
        assertEquals("Soporte End Security", LegalInfo.SUPPORT_SUBJECT)
        assertEquals("Reporte de seguridad", LegalInfo.SECURITY_REPORT_SUBJECT)
    }
}
