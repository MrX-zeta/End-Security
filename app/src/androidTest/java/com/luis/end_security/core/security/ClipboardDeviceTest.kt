package com.luis.end_security.core.security

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** DEC-25 con el portapapeles real del sistema. */
@RunWith(AndroidJUnit4::class)
class ClipboardDeviceTest {

    private lateinit var manager: ClipboardManager
    private lateinit var port: AndroidClipboardPort

    // El proceso de pruebas no tiene el foco: para leer el portapapeles se pide el permiso del shell.
    private fun <T> readingInBackground(block: () -> T): T {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.adoptShellPermissionIdentity("android.permission.READ_CLIPBOARD_IN_BACKGROUND")
        try {
            return block()
        } finally {
            automation.dropShellPermissionIdentity()
        }
    }

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        port = AndroidClipboardPort(context)
    }

    @After
    fun tearDown() {
        runCatching { manager.clearPrimaryClip() }
    }

    @Test
    fun lo_copiado_lleva_EXTRA_IS_SENSITIVE() {
        assumeTrue("EXTRA_IS_SENSITIVE existe desde Android 13", Build.VERSION.SDK_INT >= 33)
        port.set("end-security:prueba", "Tortuga-Violeta-29")

        readingInBackground {
            val description = manager.primaryClipDescription
            assertNotNull(description)
            assertTrue(description!!.extras!!.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE))
            assertEquals("Tortuga-Violeta-29", manager.primaryClip!!.getItemAt(0).text.toString())
        }
    }

    @Test
    fun la_etiqueta_identifica_lo_que_copio_la_app_y_clear_lo_borra() {
        port.set("end-security:abc", "secreto")
        readingInBackground { assertEquals("end-security:abc", port.currentLabel()) }

        port.clear()
        readingInBackground { assertNull(port.currentLabel()) }
    }

    @Test
    fun una_copia_ajena_tiene_otra_etiqueta_y_no_se_toca() {
        port.set("end-security:abc", "secreto")
        // Otra cosa copiada después: la etiqueta ya no es la de la app.
        manager.setPrimaryClip(android.content.ClipData.newPlainText("otra-app", "lo ajeno"))
        readingInBackground { assertEquals("otra-app", port.currentLabel()) }
    }

    @Test
    fun sin_el_foco_la_etiqueta_no_se_puede_leer() {
        port.set("end-security:abc", "secreto")
        // Esto es lo que ve la app desde segundo plano: Android 10+ no le deja ver qué hay copiado.
        assertNull(port.currentLabel())
    }
}
