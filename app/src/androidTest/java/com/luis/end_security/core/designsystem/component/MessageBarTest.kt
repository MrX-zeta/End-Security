package com.luis.end_security.core.designsystem.component

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.luis.end_security.R
import com.luis.end_security.core.designsystem.theme.EndSecurityTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Aviso de la Bóveda «N contraseñas necesitan atención · Ver» en un teléfono angosto (360 dp de ancho menos 32 de margen) y con
 * letra grande (escala 1.3): el texto nunca debe pasar por debajo del botón (tarea 23, paso 5.1).
 */
@RunWith(AndroidJUnit4::class)
class MessageBarTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun capture(name: String) {
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File(context.cacheDir, "captures").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun render(fontScale: Float, count: Int, widthDp: Int = 328) {
        val text = context.resources.getQuantityString(R.plurals.vault_attention_notice, count, count)
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                EndSecurityTheme {
                    Box(Modifier.padding(16.dp)) {
                        Column(Modifier.width(widthDp.dp)) {
                            MessageBar(text = text, icon = R.drawable.ic_alert_20, actionLabel = context.getString(R.string.vault_attention_action), onAction = {})
                        }
                    }
                }
            }
        }
    }

    private fun assertNoOverlap(count: Int) {
        val text = context.resources.getQuantityString(R.plurals.vault_attention_notice, count, count)
        val textBounds = composeRule.onNodeWithText(text).getBoundsInRoot()
        val actionNode = composeRule.onNodeWithText(context.getString(R.string.vault_attention_action))
        val actionBounds = actionNode.getBoundsInRoot()
        // El texto termina antes de donde empieza el botón (nunca pasa por debajo de él).
        assertTrue("el texto (${textBounds.right}) pasa por debajo del botón (${actionBounds.left})", textBounds.right <= actionBounds.left)
        // El área táctil del botón mide al menos 48 dp.
        actionNode.assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        // Y el botón queda centrado verticalmente respecto del aviso.
        val barCenter = (textBounds.top + textBounds.bottom) / 2
        val actionCenter = (actionBounds.top + actionBounds.bottom) / 2
        assertTrue("el botón no está centrado ($actionCenter vs $barCenter)", kotlin.math.abs(actionCenter.value - barCenter.value) < 2f)
    }

    @Test
    fun avisoConEscalaNormalNoSeEncima() {
        render(fontScale = 1f, count = 12)
        assertNoOverlap(12)
        capture("tarea23-aviso-bobeda-1x")
    }

    @Test
    fun avisoConLetraGrandeNoSeEncima() {
        render(fontScale = 1.3f, count = 12)
        assertNoOverlap(12)
        capture("tarea23-aviso-bobeda-1-3x")
    }

    @Test
    fun avisoConLetraGrandeYTelefonoAngostoNoSeEncima() {
        render(fontScale = 1.3f, count = 128, widthDp = 296)
        assertNoOverlap(128)
        capture("tarea23-aviso-bobeda-1-3x-angosto")
    }
}
