package com.luis.end_security.core.designsystem.component

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.luis.end_security.core.designsystem.theme.EndSecurityTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regresión de DEC-26 / regla no negociable 6: con la contraseña oculta, el árbol de accesibilidad (lo que
 * leen TalkBack, `uiautomator` o cualquier servicio) solo ve una máscara de la misma longitud, nunca el texto.
 * La prueba con TalkBack encendido en el Redmi la hace la persona; esta cubre el árbol semántico que lee.
 */
@RunWith(AndroidJUnit4::class)
class PasswordFieldMaskTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val secret = "Tortuga-Violeta-29"

    private fun passwordNode() = composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))

    /** Todo el texto que el árbol de accesibilidad expone para el campo (texto editable y etiquetas). */
    private fun exposedText(): String {
        val config = passwordNode().fetchSemanticsNode().config
        val editable = config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()
        val texts = config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }.orEmpty()
        return "$editable|$texts"
    }

    @Test
    fun oculta_el_arbol_solo_ve_puntos_de_la_misma_longitud() {
        composeRule.setContent {
            EndSecurityTheme {
                val state = remember { TextFieldState(secret) }
                PasswordField(state = state, label = "Contraseña", visible = false, onVisibleChange = {})
            }
        }
        composeRule.waitForIdle()

        val editable = passwordNode().fetchSemanticsNode().config[SemanticsProperties.EditableText].text
        assertEquals("•".repeat(secret.length), editable)
        assertFalse("el texto real no sale en el árbol", exposedText().contains(secret))
    }

    @Test
    fun al_mostrarla_y_volver_a_ocultarla_la_mascara_se_restablece() {
        var visible by mutableStateOf(false)
        composeRule.setContent {
            EndSecurityTheme {
                val state = remember { TextFieldState(secret) }
                PasswordField(state = state, label = "Contraseña", visible = visible, onVisibleChange = { visible = it })
            }
        }
        composeRule.waitForIdle()
        assertFalse(exposedText().contains(secret))

        visible = true
        composeRule.waitForIdle()
        // A la vista, la persona eligió verla: el árbol puede tener el texto.
        assertTrue(passwordNode().fetchSemanticsNode().config[SemanticsProperties.EditableText].text.isNotEmpty())

        visible = false
        composeRule.waitForIdle()
        val editable = passwordNode().fetchSemanticsNode().config[SemanticsProperties.EditableText].text
        assertEquals("•".repeat(secret.length), editable)
        assertFalse(exposedText().contains(secret))
    }

    @Test
    fun la_mascara_sigue_a_la_longitud_al_escribir() {
        lateinit var state: TextFieldState
        composeRule.setContent {
            EndSecurityTheme {
                state = remember { TextFieldState("abc") }
                PasswordField(state = state, label = "Contraseña", visible = false, onVisibleChange = {})
            }
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { state.edit { append("defgh") } }
        composeRule.waitForIdle()

        val editable = passwordNode().fetchSemanticsNode().config[SemanticsProperties.EditableText].text
        assertEquals("•".repeat(8), editable)
    }
}
