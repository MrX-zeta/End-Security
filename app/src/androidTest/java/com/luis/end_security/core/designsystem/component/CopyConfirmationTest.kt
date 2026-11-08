package com.luis.end_security.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.luis.end_security.core.designsystem.theme.EndSecurityTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** El botón de copiar confirma en el propio botón (tarea 29): palomita con fundido de 150 ms, 1,5 s y vuelve; otro toque reinicia. */
@RunWith(AndroidJUnit4::class)
class CopyConfirmationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var reduced by mutableStateOf(false)
    private lateinit var confirmation: CopyConfirmation
    private var copies = 0

    private fun setButton() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            EndSecurityTheme {
                confirmation = rememberCopyConfirmation()
                Box(
                    Modifier
                        .size(48.dp)
                        .testTag("copiar")
                        .copiedSemantics(confirmation)
                        .clickable {
                            copies++
                            confirmation.mark()
                        },
                ) {
                    CopyIcon(confirmation, contentDescription = "Copiar contraseña", reducedMotion = reduced)
                }
            }
        }
        composeRule.mainClock.advanceTimeByFrame()
    }

    private fun stateDescription(): String? = composeRule.onNodeWithTag("copiar").fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)

    private fun iconCount() = composeRule.onAllNodes(hasContentDescription("Copiar contraseña"), useUnmergedTree = true).fetchSemanticsNodes().size

    @Test
    fun al_copiar_el_icono_pasa_a_palomita_y_a_los_1_5_s_vuelve() {
        setButton()
        assertFalse(confirmation.active)
        assertNull("sin copiar no anuncia nada", stateDescription())
        composeRule.onNodeWithTag("copiar").performClick()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeByFrame()
        assertTrue("pasó a palomita", confirmation.active)
        assertEquals("anuncia «Copiado»", "Copiado", stateDescription())
        composeRule.mainClock.advanceTimeBy(1_400)
        assertTrue("sigue la palomita a los 1,4 s", confirmation.active)
        composeRule.mainClock.advanceTimeBy(250)
        assertFalse("vuelve al ícono de copiar pasados 1,5 s", confirmation.active)
        assertNull(stateDescription())
    }

    @Test
    fun el_cambio_de_icono_es_un_fundido_cruzado_de_150_ms() {
        setButton()
        assertEquals(1, iconCount())
        composeRule.onNodeWithTag("copiar").performClick()
        repeat(3) { composeRule.mainClock.advanceTimeByFrame() }
        assertEquals("a la mitad del fundido conviven los dos íconos", 2, iconCount())
        composeRule.mainClock.advanceTimeBy(250)
        assertEquals("terminó el fundido", 1, iconCount())
    }

    @Test
    fun otro_toque_mientras_tanto_vuelve_a_copiar_y_reinicia_los_1_5_s() {
        setButton()
        composeRule.onNodeWithTag("copiar").performClick()
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.onNodeWithTag("copiar").performClick()
        assertEquals("el segundo toque también copia", 2, copies)
        composeRule.mainClock.advanceTimeBy(1_000) // 2 s desde el primero, 1 s desde el segundo
        assertTrue("el segundo toque reinició el tiempo", confirmation.active)
        composeRule.mainClock.advanceTimeBy(650)
        assertFalse(confirmation.active)
    }

    @Test
    fun con_quitar_animaciones_el_cambio_es_instantaneo() {
        reduced = true
        setButton()
        composeRule.onNodeWithTag("copiar").performClick()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeByFrame()
        assertTrue(confirmation.active)
        assertEquals("sin fundido: un solo ícono a la vez", 1, iconCount())
    }

    @Test
    fun el_nombre_del_boton_no_cambia_con_la_palomita() {
        setButton()
        composeRule.onNodeWithTag("copiar").performClick()
        composeRule.mainClock.advanceTimeBy(400)
        assertTrue(confirmation.active)
        assertEquals(1, composeRule.onAllNodes(hasContentDescription("Copiar contraseña"), useUnmergedTree = true).fetchSemanticsNodes().size)
    }
}
