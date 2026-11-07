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
import androidx.compose.ui.test.assertIsNotEnabled
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
import com.luis.end_security.feature.account.ui.ManageAction
import com.luis.end_security.feature.account.ui.ReauthSheetContent
import com.luis.end_security.feature.account.ui.ReauthSheetState
import com.luis.end_security.feature.account.ui.SetupAction
import com.luis.end_security.feature.account.ui.SetupStep
import com.luis.end_security.feature.account.ui.TwoFactorActiveScreen
import com.luis.end_security.feature.account.ui.TwoFactorManageUiState
import com.luis.end_security.feature.account.ui.TwoFactorSetupScreen
import com.luis.end_security.feature.account.ui.TwoFactorSetupUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Verificación en dos pasos dibujada en el emulador (Figma 47:1509, 47:1616, 47:1714, 69:3626, 218:4304 y 218:4421).
 * Dibuja `TwoFactorSetupScreen`, `TwoFactorActiveScreen` y el contenido de la hoja, sin `SecureScreen` (solo está en
 * los `Route`), así que las capturas de `cache/captures` no debilitan ninguna protección de la app.
 */
@RunWith(AndroidJUnit4::class)
class TwoFactorScreensTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun str(id: Int) = context.getString(id)

    private val codes = listOf(
        "4F7K-92QX", "M8TR-3VRA", "Q2ZN-7HCE", "X9PD-5KWT", "R6BY-1JMF",
        "T3HW-8NQS", "C7WE-4ZGA", "K5VY-9RDP", "H1XM-6TBC", "P8SJ-2FYN",
    )

    private fun capture(name: String) {
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File(context.cacheDir, "captures").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun renderSetup(state: TwoFactorSetupUiState, actions: MutableList<SetupAction> = mutableListOf()) {
        composeRule.setContent {
            EndSecurityTheme {
                Box(Modifier.size(width = 412.dp, height = 917.dp)) {
                    TwoFactorSetupScreen(state = state, onAction = { actions += it }, onTabSelected = {})
                }
            }
        }
    }

    private val hasError = SemanticsMatcher.keyIsDefined(SemanticsProperties.Error)

    @Test
    fun paso1MuestraLosTextosDeFigmaYLaClaveEnGrupos() {
        val actions = mutableListOf<SetupAction>()
        renderSetup(TwoFactorSetupUiState(keyDisplay = "JBSW Y3DP\nEHPK 3PXP", canOpenAuthenticator = true), actions)
        composeRule.onNodeWithText(str(R.string.tf_title)).assertIsDisplayed()
        composeRule.onNodeWithText("Paso 1 de 3").assertIsDisplayed()
        composeRule.onNodeWithText("Copia tu clave", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.tf_key_label)).assertIsDisplayed()
        // La clave se reparte en renglones completos (tarea 26): los grupos de 4, en orden, con los saltos que quepan en el ancho.
        val key = composeRule.onNode(androidx.compose.ui.test.hasText("JBSW", substring = true))
        key.assertIsDisplayed()
        org.junit.Assert.assertEquals(
            "JBSW Y3DP EHPK 3PXP",
            key.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].first().text.replace("\n", " "),
        )
        composeRule.onNodeWithText(str(R.string.tf_instruction_1)).assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.tf_instruction_2)).assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.tf_instruction_3)).assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.tf_key_warning)).assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.tf_open_authenticator)).assertIsDisplayed()
        capture("tarea17-paso1")
        composeRule.onNodeWithText(str(R.string.tf_continue)).performClick()
        assertTrue(actions.contains(SetupAction.Continue))
    }

    @Test
    fun paso1SinAppDeAutenticacionNoMuestraElBotonSecundario() {
        renderSetup(TwoFactorSetupUiState(keyDisplay = "JBSW Y3DP", canOpenAuthenticator = false))
        composeRule.onAllNodesWithText(str(R.string.tf_open_authenticator)).assertCountEquals(0)
    }

    @Test
    fun cargandoMuestraGenerandoTuClaveYContinuarDeshabilitado() {
        renderSetup(TwoFactorSetupUiState(isLoadingKey = true))
        composeRule.onNodeWithText(str(R.string.tf_key_generating)).assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.tf_continue)).assertIsNotEnabledCompat()
        capture("tarea17-cargando")
    }

    @Test
    fun sinRedMuestraElErrorYReintentar() {
        val actions = mutableListOf<SetupAction>()
        renderSetup(TwoFactorSetupUiState(keyError = true), actions)
        composeRule.onNodeWithText(str(R.string.tf_key_error)).assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.tf_key_retry)).performClick()
        assertTrue(actions.contains(SetupAction.Retry))
    }

    @Test
    fun paso2MuestraElCodigoYLosSegundos() {
        renderSetup(TwoFactorSetupUiState(step = SetupStep.Code, code = "482", secondsLeft = 18))
        composeRule.onNodeWithText("Paso 2 de 3").assertIsDisplayed()
        composeRule.onNodeWithText("Escribe el código", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("El código cambia en 18 s").assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.tf_confirm)).assertIsDisplayed()
        capture("tarea17-paso2")
    }

    @Test
    fun paso2ConErrorMarcaElCampoConSemanticaDeError() {
        renderSetup(TwoFactorSetupUiState(step = SetupStep.Code, showIncomplete = true, secondsLeft = 10))
        composeRule.onNodeWithText(str(R.string.code_error_incomplete)).assertIsDisplayed()
        assertTrue(composeRule.onAllNodes(hasError).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun paso2ConCodigoIncorrectoMuestraElMensajeDeFigma() {
        renderSetup(TwoFactorSetupUiState(step = SetupStep.Code, wrongCode = true, secondsLeft = 10))
        composeRule.onNodeWithText(str(R.string.two_factor_error_wrong_code)).assertIsDisplayed()
    }

    @Test
    fun paso3MuestraLosDiezCodigosEnDosColumnas() {
        val actions = mutableListOf<SetupAction>()
        renderSetup(TwoFactorSetupUiState(step = SetupStep.Codes, codes = codes), actions)
        composeRule.onNodeWithText("Paso 3 de 3").assertIsDisplayed()
        composeRule.onNodeWithText("Guarda estos códigos", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.tf_codes_notice)).assertIsDisplayed()
        for (code in codes) composeRule.onNodeWithText(code).assertIsDisplayed()
        capture("tarea17-paso3")
        composeRule.onNodeWithText(str(R.string.tf_copy_codes)).performClick()
        composeRule.onNodeWithText(str(R.string.tf_codes_saved)).performClick()
        assertTrue(actions.containsAll(listOf(SetupAction.CopyCodes, SetupAction.Finish)))
    }

    @Test
    fun avisoDeCopiadoSeMuestra() {
        renderSetup(TwoFactorSetupUiState(keyDisplay = "JBSW Y3DP", copiedSeconds = 30))
        composeRule.onNodeWithText("Copiado · se borra del portapapeles en 30 s").assertIsDisplayed()
    }

    @Test
    fun pantallaActivaMuestraLosTextosDeFigma() {
        val actions = mutableListOf<ManageAction>()
        composeRule.setContent {
            EndSecurityTheme {
                Box(Modifier.size(width = 412.dp, height = 917.dp)) {
                    TwoFactorActiveScreen(state = TwoFactorManageUiState(), onAction = { actions += it }, onBack = {})
                }
            }
        }
        // El título de la barra y el encabezado grande.
        composeRule.onAllNodesWithText("Verificación en dos pasos", substring = true).assertCountEquals(2)
        composeRule.onNodeWithText("está activa", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.tf_protected_label)).assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.tf_protected_body)).assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.tf_active_codes_hint)).assertIsDisplayed()
        capture("tarea17-activa")
        composeRule.onNodeWithText(str(R.string.tf_regenerate)).performClick()
        composeRule.onNodeWithText(str(R.string.tf_disable)).performClick()
        assertTrue(actions.containsAll(listOf(ManageAction.RegenerateClicked, ManageAction.DisableClicked)))
    }

    @Test
    fun codigosNuevosUsanElDisenoDelPaso3SinPasoYConElAvisoDeLosAnteriores() {
        composeRule.setContent {
            EndSecurityTheme {
                Box(Modifier.size(width = 412.dp, height = 917.dp)) {
                    TwoFactorActiveScreen(
                        state = TwoFactorManageUiState(newCodes = codes),
                        onAction = {},
                        onBack = {},
                    )
                }
            }
        }
        composeRule.onNodeWithText(str(R.string.tf_new_codes_notice)).assertIsDisplayed()
        composeRule.onAllNodesWithText("Paso 3 de 3").assertCountEquals(0)
        for (code in codes) composeRule.onNodeWithText(code).assertIsDisplayed()
        capture("tarea17-codigos-nuevos")
    }

    private fun renderSheet(state: ReauthSheetState, danger: Boolean, confirm: Int, body: Int) {
        composeRule.setContent {
            EndSecurityTheme {
                Box(Modifier.size(width = 412.dp, height = 917.dp)) {
                    ReauthSheetContent(
                        body = str(body),
                        confirmLabel = str(confirm),
                        danger = danger,
                        state = state,
                        passwordState = TextFieldState("contraseña"),
                        onCodeChange = {},
                        onConfirm = {},
                        onCancel = {},
                    )
                }
            }
        }
    }

    @Test
    fun hojaDeReautenticacionMuestraLosTextosDeFigma() {
        renderSheet(ReauthSheetState(), danger = true, confirm = R.string.tf_disable, body = R.string.tf_reauth_body_disable)
        capture("tarea17-reauth")
        composeRule.onNodeWithText(str(R.string.tf_reauth_title)).assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.tf_reauth_body_disable)).assertIsDisplayed()
        // Las etiquetas y el «6 dígitos» van dentro de la semántica de cada campo (se comprobaron en la captura).
        composeRule.onNodeWithText(str(R.string.tf_disable)).assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.tf_reauth_cancel)).assertIsDisplayed()
    }

    @Test
    fun hojaParaGenerarUsaSuTextoYSuBoton() {
        renderSheet(ReauthSheetState(), danger = false, confirm = R.string.tf_reauth_generate, body = R.string.tf_reauth_body_regenerate)
        composeRule.onNodeWithText(str(R.string.tf_reauth_body_regenerate)).assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.tf_reauth_generate)).assertIsDisplayed()
    }

    @Test
    fun credencialesIncorrectasMarcanLosDosCamposConUnSoloMensaje() {
        renderSheet(ReauthSheetState(credentialsWrong = true), danger = true, confirm = R.string.tf_disable, body = R.string.tf_reauth_body_disable)
        composeRule.onAllNodesWithText(str(R.string.tf_reauth_wrong)).assertCountEquals(1)
        assertTrue(composeRule.onAllNodes(hasError).fetchSemanticsNodes().isNotEmpty())
        capture("tarea17-reauth-error")
    }

    @Test
    fun erroresDeVacioIncompletoSeMuestranConLosMensajesDeLaTarea15() {
        renderSheet(
            ReauthSheetState(showPasswordRequired = true, showCodeIncomplete = true),
            danger = true, confirm = R.string.tf_disable, body = R.string.tf_reauth_body_disable,
        )
        composeRule.onNodeWithText(str(R.string.auth_error_password_required)).assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.code_error_incomplete)).assertIsDisplayed()
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertIsNotEnabledCompat() {
        assertIsNotEnabled()
    }
}
