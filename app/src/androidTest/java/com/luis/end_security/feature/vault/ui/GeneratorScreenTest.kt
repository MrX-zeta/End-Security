package com.luis.end_security.feature.vault.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.luis.end_security.core.crypto.GeneratorOptions
import com.luis.end_security.core.designsystem.theme.EndSecurityTheme
import com.luis.end_security.feature.vault.ui.generator.GeneratorScreen
import com.luis.end_security.feature.vault.ui.generator.GeneratorSheetContent
import com.luis.end_security.feature.vault.ui.generator.GeneratorToggle
import com.luis.end_security.feature.vault.ui.generator.GeneratorUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * SCR-15 dibujado en el emulador (Figma 46:996). Dibuja `GeneratorScreen`, sin `SecureScreen` (solo está en el
 * `Route`), así que la captura de `cache/captures` no debilita ninguna protección de la app.
 */
@RunWith(AndroidJUnit4::class)
class GeneratorScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var state by mutableStateOf(
        GeneratorUiState(GeneratorOptions(), password = "q7#Vt!m2Lp@9xR4w", strengthScore = 4, generation = 1),
    )
    private var regenerated = 0
    private var copied = 0
    private var saved = 0
    private val toggles = mutableListOf<GeneratorToggle>()

    private fun render() {
        composeRule.setContent {
            EndSecurityTheme {
                Box(Modifier.size(width = 412.dp, height = 917.dp)) {
                    GeneratorScreen(
                        state = state,
                        onTabSelected = {},
                        onRegenerate = { regenerated++ },
                        onCopy = { copied++ },
                        onLengthChange = { state = GeneratorUiState(state.options.copy(length = it), state.password, 4, state.generation) },
                        onLengthChangeFinished = {},
                        onToggle = { toggles += it },
                        onSave = { saved++ },
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun capture(name: String) {
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "captures").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun muestra_el_titulo_la_contrasena_y_los_cuatro_interruptores() {
        render()
        capture("scr15_generador")

        composeRule.onNodeWithText("Generador").assertIsDisplayed()
        composeRule.onNodeWithText("crea una contraseña segura", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("q7#Vt!m2Lp@9xR4w").assertIsDisplayed()
        composeRule.onNodeWithText("Mayúsculas (A–Z)").assertIsDisplayed()
        composeRule.onNodeWithText("Números (0–9)").assertIsDisplayed()
        composeRule.onNodeWithText("Símbolos (!@#)").assertIsDisplayed()
        composeRule.onNodeWithText("Evitar parecidos").assertIsDisplayed()
        composeRule.onNodeWithText("Como l, 1, O y 0").assertIsDisplayed()
        composeRule.onNodeWithText("Guardar en la bóveda").assertIsDisplayed()
    }

    @Test
    fun la_contrasena_se_anuncia_caracter_por_caracter() {
        render()
        val node = composeRule.onNodeWithContentDescription("Contraseña generada: q 7 # V t ! m 2 L p @ 9 x R 4 w")
        node.assertIsDisplayed()
    }

    @Test
    fun el_dado_copiar_guardar_y_los_interruptores_responden() {
        render()

        composeRule.onNodeWithContentDescription("Generar otra").performClick()
        composeRule.onNodeWithContentDescription("Copiar contraseña").performClick()
        composeRule.onNodeWithText("Guardar en la bóveda").performClick()
        composeRule.onNodeWithText("Mayúsculas (A–Z)").performClick()
        composeRule.onNodeWithText("Símbolos (!@#)").performClick()
        composeRule.onNodeWithText("Evitar parecidos").performClick()

        assertEquals(1, regenerated)
        assertEquals(1, copied)
        assertEquals(1, saved)
        assertEquals(listOf(GeneratorToggle.Uppercase, GeneratorToggle.Symbols, GeneratorToggle.AvoidAmbiguous), toggles)
    }

    @Test
    fun el_deslizador_anuncia_la_longitud_y_los_botones_miden_48_dp() {
        render()
        val slider = composeRule.onNodeWithContentDescription("Longitud").fetchSemanticsNode()
        assertEquals("16 caracteres", slider.config.getOrNull(SemanticsProperties.StateDescription))

        val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        for (label in listOf("Generar otra", "Copiar contraseña")) {
            val size = composeRule.onNodeWithContentDescription(label).fetchSemanticsNode().size
            assertTrue("$label ≥ 48 dp", size.width / density >= 47.5f && size.height / density >= 47.5f)
        }
    }

    @Test
    fun copiada_y_sin_contrasena() {
        state = GeneratorUiState(GeneratorOptions(24, symbols = false, avoidAmbiguous = true), "abc", 2, 1, copiedSeconds = 30)
        render()
        capture("scr15_generador_copiada")
        composeRule.onNodeWithText("Copiada · se borra del portapapeles en 30 s").assertIsDisplayed()

        state = GeneratorUiState()
        composeRule.waitForIdle()
        // Sin contraseña (salió de primer plano o se bloqueó) no se puede guardar ni copiar.
        composeRule.onNodeWithText("Guardar en la bóveda").assertIsDisplayed()
        assertFalse(state.password.isNotEmpty())
    }

    @Test
    fun la_hoja_de_Nueva_ofrece_Usar_esta_contrasena() {
        var used = 0
        composeRule.setContent {
            EndSecurityTheme {
                Box(Modifier.size(width = 412.dp, height = 917.dp)) {
                    GeneratorSheetContent(
                        state = state,
                        onRegenerate = { regenerated++ },
                        onCopy = {},
                        onLengthChange = {},
                        onLengthChangeFinished = {},
                        onToggle = { toggles += it },
                        onUse = { used++ },
                    )
                }
            }
        }
        composeRule.waitForIdle()
        capture("scr15_hoja_de_nueva")

        composeRule.onNodeWithText("Usar esta contraseña").assertIsDisplayed()
        composeRule.onNodeWithText("Guardar en la bóveda", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Usar esta contraseña").performClick()
        composeRule.onNodeWithContentDescription("Generar otra").performClick()
        assertEquals(1, used)
        assertEquals(1, regenerated)
    }
}
