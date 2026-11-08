package com.luis.end_security.feature.account

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.luis.end_security.R
import com.luis.end_security.core.designsystem.theme.EndSecurityTheme
import com.luis.end_security.feature.account.ui.AccountDeletedRoute
import com.luis.end_security.feature.account.ui.AccountDeletedScreen
import com.luis.end_security.feature.account.ui.DeleteAccountAction
import com.luis.end_security.feature.account.ui.DeleteAccountScreen
import com.luis.end_security.feature.account.ui.DeleteAccountUiState
import com.luis.end_security.feature.account.ui.DeleteConfirmSheetContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Eliminar cuenta (SCR-36, Figma 47:2290), su hoja de confirmación (SCR-37, 62:3233) y Cuenta eliminada (SCR-38, 62:3270)
 * dibujadas en el emulador. Dibujan las pantallas sin `SecureScreen` (solo está en el `Route`), así que las capturas de
 * `cache/captures` no debilitan ninguna protección de la app.
 */
@RunWith(AndroidJUnit4::class)
class DeleteAccountScreensTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun str(id: Int) = context.getString(id)

    private fun capture(name: String) {
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File(context.cacheDir, "captures").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val withTwoFactor = DeleteAccountUiState(twoFactorRequired = true)

    private fun render(
        state: DeleteAccountUiState,
        actions: MutableList<DeleteAccountAction> = mutableListOf(),
        onSubmit: () -> Unit = {},
        onConfirm: () -> Unit = {},
    ) {
        composeRule.setContent {
            EndSecurityTheme {
                Box(Modifier.size(width = 412.dp, height = 917.dp)) {
                    DeleteAccountScreen(
                        state = state,
                        passwordState = TextFieldState("contraseña maestra"),
                        passwordVisible = false,
                        onPasswordVisibleChange = {},
                        onSubmit = onSubmit,
                        onConfirm = onConfirm,
                        onAction = { actions += it },
                        onBack = {},
                    )
                }
            }
        }
    }

    // ------------------------------------------------------------------ SCR-36

    @Test
    fun eliminarMuestraLosTextosDeFigma() {
        var submitted = false
        render(withTwoFactor, onSubmit = { submitted = true })
        composeRule.onNodeWithText(str(R.string.delete_title)).assertIsDisplayed()
        composeRule.onNodeWithText("Eliminar tu cuenta\nno se puede deshacer").assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.delete_consequence_passwords)).assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.delete_consequence_sessions)).assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.delete_consequence_history)).assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.delete_submit)).assertIsDisplayed()
        capture("tarea20-eliminar")
        composeRule.onNodeWithText(str(R.string.delete_submit)).performClick()
        assertTrue(submitted)
    }

    @Test
    fun elCodigoSoloSaleConLa2FAActiva() {
        render(withTwoFactor)
        // El campo es un solo nodo de texto editable (etiqueta + marcador): se comprueba que existe, igual que en Contraseña maestra.
        assertTrue(composeRule.onAllNodesWithText(str(R.string.tf_reauth_code_placeholder), substring = true).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun sinLa2FAOSinSaberloNoSeMuestraElCodigo() {
        render(DeleteAccountUiState(twoFactorRequired = false))
        composeRule.onAllNodesWithText(str(R.string.tf_reauth_code_placeholder), substring = true).assertCountEquals(0)
        capture("tarea20-eliminar-sin-2fa")
    }

    @Test
    fun contrasenaVaciaMuestraElErrorDeLaTarea15() {
        render(withTwoFactor.copy(showPasswordRequired = true, showCodeIncomplete = true))
        composeRule.onNodeWithText(str(R.string.auth_error_password_required)).assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.code_error_incomplete)).assertIsDisplayed()
    }

    @Test
    fun credencialesIncorrectasMuestranElMensajeUnaSolaVez() {
        render(withTwoFactor.copy(credentialsWrong = true))
        composeRule.onAllNodesWithText(str(R.string.cp_wrong)).assertCountEquals(1)
        capture("tarea20-eliminar-credenciales")
    }

    @Test
    fun conflictoLimiteYGenericoSeMuestran() {
        render(withTwoFactor.copy(conflict = true))
        composeRule.onNodeWithText(str(R.string.cp_conflict)).assertIsDisplayed()
    }

    @Test
    fun elErrorGenericoSeMuestra() {
        render(withTwoFactor.copy(genericError = true))
        composeRule.onNodeWithText(str(R.string.auth_error_generic)).assertIsDisplayed()
    }

    // ------------------------------------------------------------------ SCR-37

    @Test
    fun laHojaMuestraElNumeroConPlural() {
        val actions = mutableListOf<DeleteAccountAction>()
        var confirmed = false
        render(withTwoFactor.copy(confirmCount = 24), actions, onConfirm = { confirmed = true })
        composeRule.onNodeWithText("¿Eliminar tu cuenta para siempre?").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Se borrarán tus 24 contraseñas cifradas y se cerrará tu sesión en todos tus dispositivos.",
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Sí, eliminar mi cuenta").assertIsDisplayed()
        composeRule.onNodeWithText("Cancelar").assertIsDisplayed()
        composeRule.onNodeWithText("Sí, eliminar mi cuenta").performClick()
        assertTrue(confirmed)
        composeRule.onNodeWithText("Cancelar").performClick()
        assertEquals(listOf<DeleteAccountAction>(DeleteAccountAction.SheetDismissed), actions)
    }

    @Test
    fun laHojaConUnaContrasenaUsaElSingular() {
        render(withTwoFactor.copy(confirmCount = 1))
        composeRule.onNodeWithText(
            "Se borrará tu 1 contraseña cifrada y se cerrará tu sesión en todos tus dispositivos.",
        ).assertIsDisplayed()
    }

    @Test
    fun laHojaSinElementosHablaDeLaCuenta() {
        render(withTwoFactor.copy(confirmCount = 0))
        composeRule.onNodeWithText(str(R.string.delete_sheet_body_empty)).assertIsDisplayed()
    }

    @Test
    fun mientrasEliminaLaHojaNoSeCancelaNiSeConfirmaOtraVez() {
        val actions = mutableListOf<DeleteAccountAction>()
        var confirmed = 0
        render(withTwoFactor.copy(confirmCount = 3, isLoading = true), actions, onConfirm = { confirmed++ })
        composeRule.onNodeWithText("Sí, eliminar mi cuenta").performClick()
        composeRule.onNodeWithText("Cancelar").performClick()
        assertEquals(0, confirmed)
        assertTrue(actions.isEmpty())
    }

    @Test
    fun laHojaMuestraElGenericoSinCerrarse() {
        render(withTwoFactor.copy(confirmCount = 3, sheetError = true))
        composeRule.onNodeWithText(str(R.string.auth_error_generic)).assertIsDisplayed()
        composeRule.onNodeWithText("Sí, eliminar mi cuenta").assertIsDisplayed()
    }

    @Test
    fun elContenidoDeLaHojaSeVeEnUnaCaptura() {
        composeRule.setContent {
            EndSecurityTheme {
                Box(Modifier.size(width = 412.dp, height = 420.dp)) {
                    DeleteConfirmSheetContent(count = 24, onConfirm = {}, onCancel = {})
                }
            }
        }
        composeRule.onNodeWithText("¿Eliminar tu cuenta para siempre?").assertIsDisplayed()
        capture("tarea20-hoja")
    }

    // ------------------------------------------------------------------ SCR-38

    @Test
    fun cuentaEliminadaMuestraLosTextosDeFigma() {
        var home = 0
        composeRule.setContent {
            EndSecurityTheme {
                Box(Modifier.size(width = 412.dp, height = 917.dp)) { AccountDeletedScreen(onGoHome = { home++ }) }
            }
        }
        composeRule.onNodeWithText("Tu cuenta fue eliminada").assertIsDisplayed()
        composeRule.onNodeWithText("Borramos tus contraseñas cifradas. Gracias por usar End Security.").assertIsDisplayed()
        capture("tarea20-cuenta-eliminada")
        composeRule.onNodeWithText("Ir al inicio").performClick()
        assertEquals(1, home)
    }

    @Test
    fun atrasEnCuentaEliminadaHaceLoMismoQueIrAlInicio() {
        var home = 0
        composeRule.setContent { EndSecurityTheme { AccountDeletedRoute(onGoHome = { home++ }) } }
        composeRule.onNodeWithText("Tu cuenta fue eliminada").assertIsDisplayed()
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        assertEquals(1, home)
    }
}
