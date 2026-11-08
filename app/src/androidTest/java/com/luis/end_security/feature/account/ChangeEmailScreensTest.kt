package com.luis.end_security.feature.account

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
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
import com.luis.end_security.feature.account.ui.ChangeEmailAction
import com.luis.end_security.feature.account.ui.ChangeEmailScreen
import com.luis.end_security.feature.account.ui.ChangeEmailUiState
import com.luis.end_security.feature.account.ui.EmailCodeError
import com.luis.end_security.feature.account.ui.EmailStep
import com.luis.end_security.feature.account.ui.EmailWait
import com.luis.end_security.feature.settings.domain.AccountProfile
import com.luis.end_security.feature.settings.ui.SettingsAction
import com.luis.end_security.feature.settings.ui.SettingsScreen
import com.luis.end_security.feature.settings.ui.SettingsUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Cambiar el correo (OP-26; Figma 227:4534, 227:4726, 227:4802, 229:4852) y la tarjeta de Ajustes con flecha (227:4476) dibujados
 * en el emulador. Las capturas van a `cache/captures`.
 */
@RunWith(AndroidJUnit4::class)
class ChangeEmailScreensTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun str(id: Int, vararg args: Any) = context.getString(id, *args)

    private fun capture(name: String) {
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File(context.cacheDir, "captures").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val enter = ChangeEmailUiState(currentEmail = "luis.ruiz@upchiapas.edu.mx", newEmail = "luis.ruiz@gmail.com", twoFactorRequired = false)

    private fun render(state: ChangeEmailUiState, actions: MutableList<ChangeEmailAction> = mutableListOf()) {
        composeRule.setContent {
            EndSecurityTheme {
                Box(Modifier.size(width = 412.dp, height = 917.dp)) {
                    ChangeEmailScreen(state = state, onAction = { actions += it })
                }
            }
        }
    }

    // ------------------------------------------------------------------ Correo nuevo

    @Test
    fun correoNuevoMuestraLosTextosDeFigma() {
        val actions = mutableListOf<ChangeEmailAction>()
        render(enter, actions)
        composeRule.onNodeWithText(str(R.string.email_title)).assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.email_notice)).assertIsDisplayed()
        composeRule.onNodeWithText("luis.ruiz@upchiapas.edu.mx", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("luis.ruiz@gmail.com", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.email_continue)).assertIsDisplayed()
        capture("tarea21-correo-nuevo")
        composeRule.onNodeWithText(str(R.string.email_continue)).performClick()
        assertTrue(actions.contains(ChangeEmailAction.Continue))
    }

    @Test
    fun laEsperaDeHorasMuestraElMensajeGris() {
        render(enter.copy(wait = EmailWait.Hours(18)))
        composeRule.onNodeWithText("Podrás cambiar tu correo de nuevo en 18 h.").assertIsDisplayed()
        capture("tarea21-correo-espera")
    }

    @Test
    fun laEsperaEnMinutosYSegundos() {
        render(enter.copy(wait = EmailWait.Minutes(45)))
        composeRule.onNodeWithText("Podrás cambiar tu correo de nuevo en 45 min.").assertIsDisplayed()
    }

    @Test
    fun laEsperaEnSegundos() {
        render(enter.copy(wait = EmailWait.Seconds(30)))
        composeRule.onNodeWithText("Podrás cambiar tu correo de nuevo en 30 s.").assertIsDisplayed()
    }

    @Test
    fun sinEsperaNoHayMensaje() {
        render(enter)
        composeRule.onAllNodesWithText("Podrás cambiar tu correo", substring = true).assertCountEquals(0)
    }

    @Test
    fun igualAlActualMuestraElMensaje() {
        render(enter.copy(newEmail = "luis.ruiz@upchiapas.edu.mx", showSameAsCurrent = true))
        composeRule.onNodeWithText("Es tu correo actual. Escribe uno distinto.").assertIsDisplayed()
        capture("tarea21-correo-igual")
    }

    @Test
    fun formatoInvalidoMuestraElMensajeDeLaTarea15() {
        render(enter.copy(newEmail = "esto-no", showFormatError = true))
        composeRule.onNodeWithText(str(R.string.auth_error_email_invalid)).assertIsDisplayed()
    }

    // ------------------------------------------------------------------ Hoja de reautenticación

    @Test
    fun laHojaConDosFactoresPideContrasenaYCodigo() {
        val actions = mutableListOf<ChangeEmailAction>()
        render(enter.copy(twoFactorRequired = true, sheetOpen = true), actions)
        composeRule.onNodeWithText("Para cambiar tu correo escribe tu contraseña maestra y el código de tu app.").assertIsDisplayed()
        composeRule.onNodeWithText("Enviar código").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText(str(R.string.tf_reauth_code_placeholder), substring = true).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun laHojaSinDosFactoresNoPideCodigo() {
        render(enter.copy(twoFactorRequired = false, sheetOpen = true))
        composeRule.onNodeWithText("Para cambiar tu correo escribe tu contraseña maestra.").assertIsDisplayed()
        composeRule.onAllNodesWithText(str(R.string.tf_reauth_code_placeholder), substring = true).assertCountEquals(0)
    }

    @Test
    fun credencialesIncorrectasSeMuestranUnaVezSinCodigo() {
        render(enter.copy(twoFactorRequired = false, sheetOpen = true, credentialsWrong = true))
        composeRule.onAllNodesWithText(str(R.string.tf_reauth_wrong)).assertCountEquals(1)
    }

    // ------------------------------------------------------------------ Código

    private val code = enter.copy(step = EmailStep.Code, newEmail = "luis.ruiz@gmail.com", code = "481")

    @Test
    fun codigoMuestraLosTextosDeFigma() {
        val actions = mutableListOf<ChangeEmailAction>()
        render(code, actions)
        composeRule.onNodeWithText("Revisa\ntu correo").assertIsDisplayed()
        composeRule.onNodeWithText("Te enviamos un código de 6 dígitos a luis.ruiz@gmail.com. Tu correo cambia cuando lo confirmes.").assertIsDisplayed()
        composeRule.onNodeWithText("Confirmar correo").assertIsDisplayed()
        composeRule.onNodeWithText("¿No te llegó?").assertIsDisplayed()
        composeRule.onNodeWithText("Pide otro código").assertIsDisplayed()
        capture("tarea21-correo-codigo")
        composeRule.onNodeWithText("Pide otro código").performClick()
        assertEquals(listOf<ChangeEmailAction>(ChangeEmailAction.RequestAnotherCode), actions)
    }

    @Test
    fun codigoMuestraElCodigoDeDemostracion() {
        render(code.copy(debugCode = "481920"))
        composeRule.onNodeWithText(str(R.string.verify_debug_code, "481920")).assertIsDisplayed()
    }

    @Test
    fun codigoIncorrectoVencidoYBloqueo() {
        render(code.copy(codeError = EmailCodeError.WrongCode(3)))
        composeRule.onNodeWithText("Código incorrecto. Te quedan 3 intentos.").assertIsDisplayed()
    }

    @Test
    fun codigoVencido() {
        render(code.copy(codeError = EmailCodeError.Expired))
        composeRule.onNodeWithText(str(R.string.verify_error_expired)).assertIsDisplayed()
    }

    @Test
    fun codigoBloqueado() {
        render(code.copy(codeError = EmailCodeError.RateLimited(3)))
        composeRule.onNodeWithText(str(R.string.auth_lockout, 3)).assertIsDisplayed()
    }

    @Test
    fun confirmarEnviaLaAccion() {
        val actions = mutableListOf<ChangeEmailAction>()
        render(code.copy(code = "481920"), actions)
        composeRule.onNodeWithText("Confirmar correo").performClick()
        assertTrue(actions.contains(ChangeEmailAction.ConfirmCode))
    }

    // ------------------------------------------------------------------ Ajustes

    @Test
    fun laTarjetaDeLaCuentaDeAjustesTieneFlechaYAbreCorreo() {
        val actions = mutableListOf<SettingsAction>()
        composeRule.setContent {
            EndSecurityTheme {
                Box(Modifier.size(width = 412.dp, height = 917.dp)) {
                    SettingsScreen(
                        state = SettingsUiState(profile = AccountProfile("Luis Ruiz", "luis.ruiz@upchiapas.edu.mx")),
                        onAction = { actions += it },
                    )
                }
            }
        }
        composeRule.onNodeWithText("luis.ruiz@upchiapas.edu.mx").assertIsDisplayed()
        capture("tarea21-ajustes")
        composeRule.onNodeWithText("luis.ruiz@upchiapas.edu.mx").performClick()
        assertEquals(listOf<SettingsAction>(SettingsAction.ProfileClicked), actions)
    }
}
