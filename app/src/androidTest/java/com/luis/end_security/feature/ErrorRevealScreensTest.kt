package com.luis.end_security.feature

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.luis.end_security.R
import com.luis.end_security.core.designsystem.theme.EndSecurityTheme
import com.luis.end_security.feature.unlock.ui.UnlockScreen
import com.luis.end_security.feature.unlock.ui.UnlockUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Los errores que ahora se despliegan con fundido (tarea 28, Paso 3): el aviso aparece y lo de abajo se desplaza suave, sin saltar. */
@RunWith(AndroidJUnit4::class)
class ErrorRevealScreensTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun str(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private var unlock by mutableStateOf(UnlockUiState(displayName = "Ana"))

    @Test
    fun desbloquear_el_aviso_de_la_huella_se_despliega_y_se_pliega_sin_quedarse_a_medias() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            EndSecurityTheme {
                Box(Modifier.size(width = 412.dp, height = 917.dp)) {
                    UnlockScreen(
                        state = unlock, passwordState = TextFieldState(), passwordVisible = false,
                        onPasswordVisibleChange = {}, onSubmit = {}, onAction = {},
                    )
                }
            }
        }
        repeat(3) { composeRule.mainClock.advanceTimeByFrame() }
        composeRule.mainClock.advanceTimeBy(400)
        val notice = str(R.string.unlock_biometric_invalidated)
        composeRule.onNodeWithText(notice, substring = true).assertDoesNotExist()

        // Aparece: ya está en el árbol a los 2 cuadros (se está desplegando) y, pasados 300 ms, a la vista.
        unlock = unlock.copy(biometricInvalidated = true)
        repeat(2) { composeRule.mainClock.advanceTimeByFrame() }
        composeRule.onNodeWithText(notice, substring = true).assertExists()
        composeRule.mainClock.advanceTimeBy(300)
        composeRule.onNodeWithText(notice, substring = true).assertIsDisplayed()

        // Desaparece: sigue un momento mientras se pliega (150 ms) y luego se quita.
        unlock = unlock.copy(biometricInvalidated = false)
        repeat(2) { composeRule.mainClock.advanceTimeByFrame() }
        composeRule.onNodeWithText(notice, substring = true).assertExists()
        composeRule.mainClock.advanceTimeBy(300)
        composeRule.onNodeWithText(notice, substring = true).assertDoesNotExist()
    }
}
