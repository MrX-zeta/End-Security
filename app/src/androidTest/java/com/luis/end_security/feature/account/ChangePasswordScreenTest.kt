package com.luis.end_security.feature.account

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.luis.end_security.R
import com.luis.end_security.core.designsystem.theme.EndSecurityTheme
import com.luis.end_security.feature.account.ui.BreachCheck
import com.luis.end_security.feature.account.ui.ChangePasswordAction
import com.luis.end_security.feature.account.ui.ChangePasswordScreen
import com.luis.end_security.feature.account.ui.ChangePasswordUiState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Contraseña maestra (SCR-35, Figma 47:2098) dibujada en el emulador. Dibuja `ChangePasswordScreen` sin `SecureScreen`
 * (solo está en el `Route`), así que las capturas de `cache/captures` no debilitan ninguna protección de la app.
 */
@RunWith(AndroidJUnit4::class)
class ChangePasswordScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun str(id: Int) = context.getString(id)
    private val hasError = SemanticsMatcher.keyIsDefined(SemanticsProperties.Error)

    private val allMet = ChangePasswordUiState(
        twoFactorRequired = false,
        newTyped = true,
        passwordLength = 26,
        confirmationLength = 26,
        strengthScore = 3,
        meetsLength = true,
        hasUppercase = true,
        hasLowercase = true,
        hasDigit = true,
        confirmationMatches = true,
        breach = BreachCheck.Clean,
    )

    private fun render(state: ChangePasswordUiState, actions: MutableList<ChangePasswordAction> = mutableListOf(), onSubmit: () -> Unit = {}) {
        composeRule.setContent {
            EndSecurityTheme {
                Box(Modifier.size(width = 412.dp, height = 917.dp)) {
                    ChangePasswordScreen(
                        state = state,
                        currentState = TextFieldState("contraseña actual"),
                        newState = TextFieldState("Nube-gris-camina-41-rapido"),
                        confirmationState = TextFieldState("Nube-gris-camina-41-rapido"),
                        currentVisible = false,
                        onCurrentVisibleChange = {},
                        newVisible = false,
                        onNewVisibleChange = {},
                        onConfirmationFocusLost = {},
                        onSubmit = onSubmit,
                        onAction = { actions += it },
                        onBack = {},
                    )
                }
            }
        }
    }

    private fun capture(name: String) {
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File(context.cacheDir, "captures").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun muestraLosTextosDeFigmaYLosSeisRequisitos() {
        var submitted = false
        render(allMet, onSubmit = { submitted = true })
        composeRule.onNodeWithText(str(R.string.cp_title)).assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.cp_notice)).assertIsDisplayed()
        for (id in listOf(
            R.string.signup_req_length, R.string.signup_req_upper, R.string.signup_req_lower, R.string.signup_req_digit,
            R.string.signup_req_match, R.string.cp_req_breach,
        )) {
            // Cada línea es `texto, cumplido`: la descripción de TalkBack lleva su estado.
            assertTrue(composeRule.onAllNodes(androidx.compose.ui.test.hasContentDescription(str(id), substring = true)).fetchSemanticsNodes().isNotEmpty())
        }
        capture("tarea18-contrasena")
        composeRule.onNodeWithText(str(R.string.cp_submit)).performClick()
        assertTrue(submitted)
    }

    @Test
    fun elCampoDelCodigoSoloSaleConLa2FAActiva() {
        render(allMet.copy(twoFactorRequired = false))
        // Etiqueta del campo del código: dentro de la semántica del campo; se comprueba por su «6 dígitos».
        assertTrue(composeRule.onAllNodesWithText(str(R.string.tf_reauth_code_placeholder), substring = true).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun conLa2FAActivaSeMuestraElCampoDelCodigo() {
        render(allMet.copy(twoFactorRequired = true))
        assertTrue(composeRule.onAllNodesWithText(str(R.string.tf_reauth_code_placeholder), substring = true).fetchSemanticsNodes().isNotEmpty())
        capture("tarea18-contrasena-2fa")
    }

    @Test
    fun conFiltracionMarcaLaNuevaConSuMensaje() {
        render(allMet.copy(breach = BreachCheck.Found))
        composeRule.onNodeWithText(str(R.string.cp_breach_found)).assertIsDisplayed()
        assertTrue(composeRule.onAllNodes(hasError).fetchSemanticsNodes().isNotEmpty())
        capture("tarea18-filtracion")
    }

    @Test
    fun sinConexionLaLineaDiceNoSePudoRevisarYNoMarcaError() {
        render(allMet.copy(breach = BreachCheck.Offline))
        composeRule.onNodeWithText(str(R.string.cp_req_breach_offline)).assertIsDisplayed()
        composeRule.onAllNodesWithText(str(R.string.cp_breach_found)).assertCountEquals(0)
    }

    @Test
    fun antesDeRevisarLaLineaDeFiltracionVaNeutra() {
        render(allMet.copy(breach = BreachCheck.Pending))
        composeRule.onNodeWithText(str(R.string.cp_req_breach)).assertIsDisplayed()
    }

    @Test
    fun igualALaActualMuestraDebeSerDistinta() {
        render(allMet.copy(showSameAsCurrent = true))
        composeRule.onNodeWithText(str(R.string.cp_same)).assertIsDisplayed()
    }

    @Test
    fun credencialesIncorrectasMuestranElMensajeUnaSolaVez() {
        render(allMet.copy(twoFactorRequired = true, credentialsWrong = true))
        composeRule.onAllNodesWithText(str(R.string.cp_wrong)).assertCountEquals(1)
        capture("tarea18-credenciales")
    }

    @Test
    fun elConflictoDeVersionYElErrorGenericoSeMuestran() {
        render(allMet.copy(conflict = true))
        composeRule.onNodeWithText(str(R.string.cp_conflict)).assertIsDisplayed()
    }

    @Test
    fun conErroresDeVacioMuestraLosMensajesDeLaTarea15() {
        render(allMet.copy(showCurrentRequired = true, showNewRequired = true, showMismatch = true))
        composeRule.onAllNodesWithText(str(R.string.auth_error_password_required)).assertCountEquals(2)
        composeRule.onNodeWithText(str(R.string.signup_mismatch)).assertIsDisplayed()
    }

    @Test
    fun mientrasCargaElBotonNoSeVuelveATocar() {
        var submitted = 0
        render(allMet.copy(isLoading = true), onSubmit = { submitted++ })
        composeRule.onNodeWithText(str(R.string.cp_submit)).performClick()
        assertTrue(submitted == 0)
    }
}
