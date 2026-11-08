package com.luis.end_security.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Todo lo que se copia pasa por [SecretClipboard] (DEC-25), que marca la copia con `EXTRA_IS_SENSITIVE` (Android 13+): así el teclado y la
 * vista previa del sistema la muestran oculta. La hoja del Generador usa el mismo `GeneratorViewModel` que la pestaña, que copia con
 * `SecretClipboard`; esta prueba vigila que **ningún otro archivo** hable con el `ClipboardManager` (tarea 29).
 */
class ClipboardOnlyThroughSecretClipboardTest {

    private fun sourceRoot(): File {
        // Gradle ejecuta las pruebas con el módulo `app` como directorio de trabajo.
        val candidates = listOf(File("src/main/java"), File("app/src/main/java"))
        return candidates.first { it.isDirectory }
    }

    @Test
    fun solo_SecretClipboard_escribe_en_el_portapapeles_del_sistema() {
        val offenders = sourceRoot().walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { file ->
                val text = file.readText()
                text.contains("setPrimaryClip(") || text.contains("ClipboardManager") || text.contains("LocalClipboardManager")
            }
            .map { it.name }
            .toList()
        assertEquals("solo SecretClipboard.kt puede tocar el portapapeles del sistema (encontrado en $offenders)", listOf("SecretClipboard.kt"), offenders)
    }

    @Test
    fun la_copia_del_sistema_lleva_la_marca_de_sensible() {
        val text = File(sourceRoot(), "com/luis/end_security/core/security/SecretClipboard.kt").readText()
        assertTrue(text.contains("android.content.extra.IS_SENSITIVE"))
        assertTrue("la marca se pone antes de copiar", text.indexOf("putBoolean(EXTRA_IS_SENSITIVE, true)") < text.indexOf("setPrimaryClip(clip)"))
    }
}
