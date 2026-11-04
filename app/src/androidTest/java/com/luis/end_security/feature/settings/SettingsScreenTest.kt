package com.luis.end_security.feature.settings

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.luis.end_security.R
import com.luis.end_security.core.designsystem.theme.EndSecurityTheme
import com.luis.end_security.feature.settings.domain.AccountProfile
import com.luis.end_security.feature.settings.ui.AutoLockSheetContent
import com.luis.end_security.feature.settings.ui.FutureRow
import com.luis.end_security.feature.settings.ui.SettingsAction
import com.luis.end_security.feature.settings.ui.SettingsScreen
import com.luis.end_security.feature.settings.ui.SettingsUiState
import com.luis.end_security.feature.settings.ui.SignOutSheet
import com.luis.end_security.feature.settings.ui.SignOutSheetContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * SCR-29 y sus hojas dibujados en el emulador (Figma 47:1303, 215:4229, 215:3811 y 215:4020). Dibuja `SettingsScreen` y
 * el contenido de las hojas: no muestran secretos y no llevan `SecureScreen`, así que las capturas de `cache/captures`
 * no debilitan ninguna protección.
 */
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun str(id: Int) = context.getString(id)

    private var state by mutableStateOf(
        SettingsUiState(
            profile = AccountProfile("Luis Ruiz", "luis.ruiz@upchiapas.edu.mx"),
            twoFactorEnabled = true,
            deviceCount = 3,
            biometricRowVisible = true,
            biometricEnabled = true,
            autolockMinutes = 1,
        ),
    )
    private val actions = mutableListOf<SettingsAction>()

    private fun render() {
        composeRule.setContent {
            EndSecurityTheme {
                Box(Modifier.size(width = 412.dp, height = 917.dp)) {
                    SettingsScreen(state = state, onAction = { actions += it }, onTabSelected = {})
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
    fun ajustesMuestraLosTextosDelDiseno() {
        render()
        for (id in listOf(
            R.string.settings_section_security, R.string.settings_two_factor,
            R.string.settings_two_factor_active, R.string.settings_biometric, R.string.settings_autolock,
            R.string.settings_master_password, R.string.settings_section_account, R.string.settings_devices,
            R.string.settings_activity, R.string.settings_sign_out, R.string.settings_delete_account,
        )) {
            composeRule.onNodeWithText(str(id)).assertIsDisplayed()
        }
        composeRule.onNodeWithText("Ajustes y seguridad").assertIsDisplayed()
        composeRule.onNodeWithText("Luis Ruiz").assertIsDisplayed()
        composeRule.onNodeWithText("luis.ruiz@upchiapas.edu.mx").assertIsDisplayed()
        composeRule.onNodeWithText("1 min").assertIsDisplayed()
        composeRule.onNodeWithText("3").assertIsDisplayed()
        capture("tarea16-ajustes")
    }

    @Test
    fun sinSensorLaFilaDeHuellaNoSeMuestra() {
        state = state.copy(biometricRowVisible = false)
        render()
        composeRule.onAllNodesWithTextCount(str(R.string.settings_biometric), 0)
    }

    @Test
    fun conAvisoDeHuellaSinRegistrarMuestraElMensaje() {
        state = state.copy(biometricEnabled = false, biometricNeedsEnrollment = true)
        render()
        composeRule.onNodeWithText(str(R.string.settings_biometric_needs_enrollment)).assertIsDisplayed()
    }

    @Test
    fun sinRedLasFilasSoloMuestranLaFlecha() {
        state = state.copy(twoFactorEnabled = null, deviceCount = null)
        render()
        composeRule.onAllNodesWithTextCount(str(R.string.settings_two_factor_active), 0)
        composeRule.onAllNodesWithTextCount(str(R.string.settings_two_factor_inactive), 0)
    }

    @Test
    fun elInterruptorEsUnaFilaConRolSwitchYAvisaAlTocar() {
        render()
        val switchNode = SemanticsMatcher("rol Switch") { it.config.getOrNull(SemanticsProperties.Role) == Role.Switch }
        composeRule.onNode(switchNode).assertIsDisplayed()
        composeRule.onNode(switchNode).performClick()
        assertTrue(actions.contains(SettingsAction.BiometricToggled(false)))
    }

    @Test
    fun tocarLasFilasEnvianSusAcciones() {
        render()
        composeRule.onNodeWithText(str(R.string.settings_autolock)).performClick()
        composeRule.onNodeWithText(str(R.string.settings_two_factor)).performClick()
        composeRule.onNodeWithText(str(R.string.settings_sign_out)).performClick()
        assertTrue(actions.contains(SettingsAction.AutoLockClicked))
        assertTrue(actions.contains(SettingsAction.FutureRowClicked(FutureRow.TwoFactor)))
        assertTrue(actions.contains(SettingsAction.SignOutClicked))
    }

    @Test
    fun hojaDeBloqueoAutomaticoMarcaLaOpcionActualYEsGrupoDeRadios() {
        var chosen = -1
        composeRule.setContent {
            EndSecurityTheme {
                Box(Modifier.size(width = 412.dp, height = 917.dp)) {
                    AutoLockSheetContent(selected = 5, onSelect = { chosen = it })
                }
            }
        }
        composeRule.onNodeWithText(str(R.string.settings_autolock_title)).assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.settings_autolock_body)).assertIsDisplayed()
        for (id in listOf(
            R.string.settings_autolock_option_0, R.string.settings_autolock_option_1, R.string.settings_autolock_option_5,
            R.string.settings_autolock_option_15, R.string.settings_autolock_option_30,
        )) {
            composeRule.onNodeWithText(str(id)).assertIsDisplayed()
        }
        val selectedRadio = SemanticsMatcher("radio marcado") {
            it.config.getOrNull(SemanticsProperties.Role) == Role.RadioButton &&
                it.config.getOrNull(SemanticsProperties.Selected) == true
        }
        val radios = composeRule.onAllNodes(SemanticsMatcher("radio") { it.config.getOrNull(SemanticsProperties.Role) == Role.RadioButton })
        assertEquals(5, radios.fetchSemanticsNodes().size)
        assertEquals(1, composeRule.onAllNodes(selectedRadio).fetchSemanticsNodes().size)
        composeRule.onNodeWithText(str(R.string.settings_autolock_option_15)).performClick()
        assertEquals(15, chosen)
        capture("tarea16-bloqueo-automatico")
    }

    @Test
    fun hojaCerrarSesionNormal() {
        var confirmed = false
        composeRule.setContent {
            EndSecurityTheme {
                Box(Modifier.size(width = 412.dp, height = 917.dp)) {
                    SignOutSheetContent(sheet = SignOutSheet.Confirm, onConfirm = { confirmed = true }, onCancel = {})
                }
            }
        }
        composeRule.onNodeWithText(str(R.string.settings_sign_out_title)).assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.settings_sign_out_body)).assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.settings_sign_out_cancel)).assertIsDisplayed()
        capture("tarea16-cerrar-sesion")
        composeRule.onNodeWithText(str(R.string.settings_sign_out_confirm)).performClick()
        assertTrue(confirmed)
    }

    @Test
    fun hojaCerrarSesionConCambiosSinSubir() {
        composeRule.setContent {
            EndSecurityTheme {
                Box(Modifier.size(width = 412.dp, height = 917.dp)) {
                    SignOutSheetContent(sheet = SignOutSheet.Unsynced(2), onConfirm = {}, onCancel = {})
                }
            }
        }
        composeRule.onNodeWithText("Tienes 2 cambios sin subir").assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.settings_sign_out_unsynced_body)).assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.settings_sign_out_anyway)).assertIsDisplayed()
        capture("tarea16-cerrar-sesion-sin-subir")
    }

    @Test
    fun hojaConUnSoloCambioUsaElSingular() {
        composeRule.setContent {
            EndSecurityTheme {
                SignOutSheetContent(sheet = SignOutSheet.Unsynced(1), onConfirm = {}, onCancel = {})
            }
        }
        composeRule.onNodeWithText("Tienes 1 cambio sin subir").assertIsDisplayed()
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextCount(text: String, expected: Int) {
        assertEquals(expected, onAllNodes(androidx.compose.ui.test.hasText(text, substring = true)).fetchSemanticsNodes().size)
    }
}
