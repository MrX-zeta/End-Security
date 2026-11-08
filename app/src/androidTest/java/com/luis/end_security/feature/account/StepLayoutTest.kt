package com.luis.end_security.feature.account

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.luis.end_security.R
import com.luis.end_security.core.designsystem.theme.EndSecurityTheme
import com.luis.end_security.feature.account.ui.ChangeEmailAction
import com.luis.end_security.feature.account.ui.ChangeEmailScreen
import com.luis.end_security.feature.account.ui.ChangeEmailUiState
import com.luis.end_security.feature.account.ui.CodeFieldTag
import com.luis.end_security.feature.account.ui.CodeTimerTag
import com.luis.end_security.feature.account.ui.InstructionsTag
import com.luis.end_security.feature.account.ui.KeyCardTag
import com.luis.end_security.feature.account.ui.KeyWarningTag
import com.luis.end_security.feature.account.ui.SetupStep
import com.luis.end_security.feature.account.ui.StepTitleTag
import com.luis.end_security.feature.account.ui.TwoFactorSetupScreen
import com.luis.end_security.feature.account.ui.TwoFactorSetupUiState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Los pasos de los flujos quedan **en orden vertical sin solaparse** (tarea 28): `AnimatedContent` coloca su contenido en un `Box`, así
 * que antes el título, la tarjeta y la lista del Paso 1 del 2FA (y el título y las casillas del Paso 2) se apilaban en el mismo lugar.
 * Se compara con los límites de cada elemento: el `bottom` de uno debe ser ≤ el `top` del siguiente, también a la mitad de la
 * transición. Sin capturas. Un cuadro = 16 ms.
 */
@RunWith(AndroidJUnit4::class)
class StepLayoutTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var state by mutableStateOf(TwoFactorSetupUiState(step = SetupStep.Key, keyDisplay = "JBSWY3DPEHPK3PXP", canOpenAuthenticator = false))

    private fun frames(count: Int) = repeat(count) { composeRule.mainClock.advanceTimeByFrame() }

    private fun setTwoFactor() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            EndSecurityTheme {
                Box(Modifier.size(width = 412.dp, height = 917.dp)) { TwoFactorSetupScreen(state = state, onAction = {}, onTabSelected = {}) }
            }
        }
        frames(3)
    }

    private fun bounds(node: SemanticsNodeInteraction): Rect = node.getBoundsInRoot().let { Rect(it.left.value, it.top.value, it.right.value, it.bottom.value) }

    private fun below(upper: Rect, lower: Rect, what: String) =
        assertTrue("$what: el de arriba termina en ${upper.bottom} y el de abajo empieza en ${lower.top}", upper.bottom <= lower.top + 0.5f)

    /** El título del paso que más se parece (en horizontal) a [reference]: durante la transición hay dos títulos. */
    private fun titleNear(reference: Rect): Rect {
        val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        return composeRule.onAllNodes(hasTestTag(StepTitleTag)).fetchSemanticsNodes()
            .map { Rect(it.boundsInRoot.left / density, it.boundsInRoot.top / density, it.boundsInRoot.right / density, it.boundsInRoot.bottom / density) }
            .minByOrNull { kotlin.math.abs(it.left - reference.left) }!!
    }

    private fun checkKeyStep() {
        val card = bounds(composeRule.onNodeWithTag(KeyCardTag))
        val instructions = bounds(composeRule.onNodeWithTag(InstructionsTag))
        val warning = bounds(composeRule.onNodeWithTag(KeyWarningTag))
        below(titleNear(card), card, "título → tarjeta de la clave")
        below(card, instructions, "tarjeta de la clave → instrucciones")
        below(instructions, warning, "instrucciones → «La clave solo se muestra ahora»")
    }

    private fun checkCodeStep() {
        val code = bounds(composeRule.onNodeWithTag(CodeFieldTag))
        val timer = bounds(composeRule.onNodeWithTag(CodeTimerTag))
        below(titleNear(code), code, "título → casillas del código")
        below(code, timer, "casillas → «El código cambia en…»")
    }

    @Test
    fun paso_1_del_2FA_titulo_tarjeta_instrucciones_y_nota_en_orden_vertical() {
        setTwoFactor()
        composeRule.mainClock.advanceTimeBy(400)
        checkKeyStep()
    }

    @Test
    fun paso_2_del_2FA_titulo_y_casillas_en_orden_vertical() {
        state = TwoFactorSetupUiState(step = SetupStep.Code, secondsLeft = 18)
        setTwoFactor()
        composeRule.mainClock.advanceTimeBy(400)
        checkCodeStep()
    }

    @Test
    fun a_la_mitad_de_la_transicion_el_paso_que_entra_no_se_encima_consigo_mismo() {
        setTwoFactor()
        composeRule.mainClock.advanceTimeBy(400)
        // Avanzar: el Paso 2 entra desde la derecha.
        state = TwoFactorSetupUiState(step = SetupStep.Code, secondsLeft = 18)
        frames(6) // ~100 ms: a la mitad de los 260 ms
        checkCodeStep()
        composeRule.mainClock.advanceTimeBy(400)
        // Regresar: el Paso 1 entra desde la izquierda.
        state = TwoFactorSetupUiState(step = SetupStep.Key, keyDisplay = "JBSWY3DPEHPK3PXP", canOpenAuthenticator = false)
        frames(6)
        checkKeyStep()
        composeRule.mainClock.advanceTimeBy(400)
        checkKeyStep()
    }

    // ---- Cambiar correo

    private var emailState by mutableStateOf(ChangeEmailUiState(currentEmail = "ana@correo.com"))

    private fun str(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test
    fun cambiar_correo_aviso_correo_actual_y_boton_en_orden_vertical() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            EndSecurityTheme {
                Box(Modifier.size(width = 412.dp, height = 917.dp)) { ChangeEmailScreen(state = emailState, onAction = { _: ChangeEmailAction -> }) }
            }
        }
        frames(3)
        composeRule.mainClock.advanceTimeBy(400)
        val notice = bounds(composeRule.onNodeWithText(str(R.string.email_notice), substring = true))
        val current = bounds(composeRule.onNodeWithText("ana@correo.com"))
        val button = bounds(composeRule.onNodeWithText(str(R.string.email_continue)))
        below(notice, current, "aviso → correo actual")
        below(current, button, "correo actual → botón Continuar")
    }
}
