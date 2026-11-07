package com.luis.end_security.core.designsystem.component

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.luis.end_security.core.designsystem.theme.EndSecurityTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Captura la barra inferior **cuadro por cuadro** (reloj de Compose controlado, un cuadro de 16 ms) para revisar el movimiento de la
 * píldora, el color del ícono y el pulso a ojo (tarea 25). Las pantallas con `FLAG_SECURE` salen negras en `screenrecord`, así que
 * esta es la forma de ver la barra completa. Deja los PNG en `cache/captures/barra-<escena>-<n>.png` de la app. No afirma nada: la
 * comprobación numérica está en [BottomNavBarTest].
 */
@RunWith(AndroidJUnit4::class)
class BottomNavBarFramesTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var selected by mutableStateOf(BottomNavTab.Vault)

    private fun setBar() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            EndSecurityTheme { Box(Modifier.width(412.dp)) { BottomNavBar(selected = selected, onTabSelected = {}, reducedMotion = false) } }
        }
        repeat(3) { composeRule.mainClock.advanceTimeByFrame() }
    }

    private fun capture(scene: String, index: Int) {
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "captures").apply { mkdirs() }
        File(dir, "barra-$scene-%02d.png".format(index)).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun record(scene: String, frames: Int, start: Int = 0) {
        for (i in 0 until frames) {
            capture(scene, start + i)
            composeRule.mainClock.advanceTimeByFrame()
        }
    }

    @Test
    fun deBovedaAAjustes() {
        setBar()
        selected = BottomNavTab.Settings
        record("boveda-ajustes", frames = 22)
    }

    @Test
    fun cambioDeRumboAMitadDelCamino() {
        setBar()
        selected = BottomNavTab.Settings
        record("rumbo", frames = 6)
        selected = BottomNavTab.Health
        record("rumbo", frames = 16, start = 6)
    }

    @Test
    fun toquesRapidosSeguidos() {
        setBar()
        var n = 0
        listOf(BottomNavTab.Health, BottomNavTab.Generator, BottomNavTab.Settings, BottomNavTab.Generator, BottomNavTab.Vault).forEach { tab ->
            selected = tab
            record("rapidos", frames = 5, start = n)
            n += 5
        }
        record("rapidos", frames = 12, start = n)
    }
}
