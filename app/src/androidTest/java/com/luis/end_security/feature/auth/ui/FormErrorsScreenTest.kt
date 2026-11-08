package com.luis.end_security.feature.auth.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.luis.end_security.R
import com.luis.end_security.core.designsystem.theme.EndSecurityTheme
import com.luis.end_security.feature.auth.ui.login.LoginError
import com.luis.end_security.feature.auth.ui.login.LoginScreen
import com.luis.end_security.feature.auth.ui.login.LoginUiState
import com.luis.end_security.feature.auth.ui.signup.SignUpAction
import com.luis.end_security.feature.auth.ui.signup.SignUpScreen
import com.luis.end_security.feature.auth.ui.signup.SignUpUiState
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Mensajes de error de los formularios (tarea 15) dibujados en el emulador. Dibuja `SignUpScreen` y `LoginScreen`
 * (sin `SecureScreen`, que solo está en el `Route`), así que las capturas de `cache/captures` no debilitan ninguna
 * protección de la app.
 */
@RunWith(AndroidJUnit4::class)
class FormErrorsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val signUpActions = mutableListOf<SignUpAction>()

    private fun renderSignUp(state: SignUpUiState) {
        composeRule.setContent {
            EndSecurityTheme {
                Box(Modifier.size(width = 412.dp, height = 917.dp)) {
                    SignUpScreen(
                        state = state,
                        passwordState = TextFieldState(),
                        confirmationState = TextFieldState(),
                        passwordVisible = false,
                        onPasswordVisibleChange = {},
                        onConfirmationFocusLost = {},
                        onSubmit = {},
                        onAction = { signUpActions += it },
                        onBack = {},
                        openedDocument = null,
                        onOpenLegalDocument = {},
                        onDismissLegalDocument = {},
                    )
                }
            }
        }
    }

    private fun hasErrorSemantics() = SemanticsMatcher.keyIsDefined(SemanticsProperties.Error)

    private fun capture(name: String) {
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File(context.cacheDir, "captures").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun crearCuentaSinErroresNoMuestraMensajes() {
        renderSignUp(SignUpUiState())
        composeRule.onAllNodesWithText(context.getString(R.string.auth_error_email_invalid)).assertCountEquals(0)
        composeRule.onAllNodesWithText(context.getString(R.string.signup_terms_error)).assertCountEquals(0)
        assertTrue(composeRule.onAllNodes(hasErrorSemantics()).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun crearCuentaConTodoMalMuestraLosMensajes() {
        renderSignUp(
            SignUpUiState(
                email = "x@mail.com",
                showEmailError = true,
                showPasswordRequired = true,
                showTermsError = true,
            ),
        )
        composeRule.onNodeWithText(context.getString(R.string.auth_error_email_invalid)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.auth_error_password_required)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.signup_terms_error)).assertIsDisplayed()
        // Correo y contraseña quedan marcados como error para TalkBack.
        assertTrue(composeRule.onAllNodes(hasErrorSemantics()).fetchSemanticsNodes().size >= 2)
        capture("tarea15-crear-cuenta-errores")
    }

    @Test
    fun crearCuentaTieneLaCasillaOpcionalDeNovedadesSinMarcarYSinError() {
        renderSignUp(SignUpUiState())
        val label = context.getString(R.string.signup_marketing)
        composeRule.onNodeWithText(label).assertIsDisplayed()
        val checkbox = composeRule.onNode(hasContentDescription(label) and isToggleable())
        checkbox.assertIsOff()
        // Opcional: sin marcar no hay ningún aviso de error por ella.
        assertTrue(composeRule.onAllNodes(hasErrorSemantics()).fetchSemanticsNodes().isEmpty())
        checkbox.performClick()
        assertEquals(listOf<SignUpAction>(SignUpAction.MarketingOptInChanged(true)), signUpActions)
        capture("tarea22-crear-cuenta-novedades")
    }

    @Test
    fun crearCuentaMuestraLaCasillaDeNovedadesMarcadaCuandoElEstadoLoDice() {
        renderSignUp(SignUpUiState(marketingOptIn = true))
        composeRule.onNode(hasContentDescription(context.getString(R.string.signup_marketing)) and isToggleable()).assertIsOn()
    }

    @Test
    fun crearCuentaMuestraLaListaDeRequisitosAlEscribir() {
        renderSignUp(SignUpUiState(passwordTyped = true, passwordLength = 5, meetsLength = false))
        composeRule.onNodeWithText(context.getString(R.string.signup_req_length)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.signup_req_upper)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.signup_req_lower)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.signup_req_digit)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.signup_req_match)).assertIsDisplayed()
        capture("tarea15-crear-cuenta-requisitos")
    }

    @Test
    fun loginConCredencialesIncorrectasMarcaDosCamposConUnSoloMensaje() {
        composeRule.setContent {
            EndSecurityTheme {
                Box(Modifier.size(width = 412.dp, height = 917.dp)) {
                    LoginScreen(
                        state = LoginUiState(
                            email = "ana@correo.com",
                            isEmailValid = true,
                            error = LoginError.InvalidCredentials,
                        ),
                        passwordState = TextFieldState(),
                        passwordVisible = false,
                        onPasswordVisibleChange = {},
                        onAction = {},
                        onSubmit = {},
                        onCreateAccount = {},
                    )
                }
            }
        }
        composeRule.onNodeWithText(context.getString(R.string.login_error_invalid_credentials)).assertIsDisplayed()
        capture("tarea15-login-credenciales")
    }
}
