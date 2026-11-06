package com.luis.end_security.feature

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.luis.end_security.R
import com.luis.end_security.core.designsystem.theme.EndSecurityTheme
import com.luis.end_security.feature.account.ui.ChangeEmailScreen
import com.luis.end_security.feature.account.ui.ChangeEmailUiState
import com.luis.end_security.feature.account.ui.ChangePasswordScreen
import com.luis.end_security.feature.account.ui.ChangePasswordUiState
import com.luis.end_security.feature.account.ui.DeleteAccountScreen
import com.luis.end_security.feature.account.ui.DeleteAccountUiState
import com.luis.end_security.feature.account.ui.EmailStep
import com.luis.end_security.feature.account.ui.ReauthSheet
import com.luis.end_security.feature.account.ui.ReauthSheetState
import com.luis.end_security.feature.account.ui.SetupStep
import com.luis.end_security.feature.account.ui.TwoFactorSetupScreen
import com.luis.end_security.feature.account.ui.TwoFactorSetupUiState
import com.luis.end_security.feature.auth.ui.backupcode.BackupCodeScreen
import com.luis.end_security.feature.auth.ui.backupcode.BackupCodeUiState
import com.luis.end_security.feature.auth.ui.login.LoginScreen
import com.luis.end_security.feature.auth.ui.login.LoginUiState
import com.luis.end_security.feature.auth.ui.signup.SignUpScreen
import com.luis.end_security.feature.auth.ui.signup.SignUpUiState
import com.luis.end_security.feature.auth.ui.twofactor.TwoFactorScreen
import com.luis.end_security.feature.auth.ui.twofactor.TwoFactorUiState
import com.luis.end_security.feature.auth.ui.verify.VerifyEmailScreen
import com.luis.end_security.feature.auth.ui.verify.VerifyEmailUiState
import com.luis.end_security.feature.unlock.ui.UnlockScreen
import com.luis.end_security.feature.unlock.ui.UnlockUiState
import com.luis.end_security.feature.vault.ui.edit.EditStatus
import com.luis.end_security.feature.vault.ui.edit.FormFields
import com.luis.end_security.feature.vault.ui.edit.ItemEditScreen
import com.luis.end_security.feature.vault.ui.edit.ItemEditUiState
import com.luis.end_security.feature.vault.ui.list.VaultListScreen
import com.luis.end_security.feature.vault.ui.list.VaultListUiState
import com.luis.end_security.feature.vault.ui.list.VaultRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/**
 * Auditoría del teclado (tarea 23, paso 5.5). Cada vista con campos se dibuja en la ventana real, se enfoca su primer campo para
 * que salga el teclado de verdad (hace falta `settings put secure show_ime_with_hard_keyboard 1` en el emulador), se toma una
 * captura de pantalla completa en `cache/captures/teclado-<vista>-<ancho>x<alto>.png` y se comprueba que el botón principal quede
 * **completo y a la vista encima del teclado** (o se alcance desplazando) y que el campo enfocado no quede tapado.
 * Se corre con el emulador de 412×917 y con uno de 360×640 (`wm size 1080x1920; wm density 480`).
 */
@RunWith(AndroidJUnit4::class)
class KeyboardAuditTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun str(id: Int) = context.getString(id)

    private fun imeTopPx(): Int {
        val decor = composeRule.activity.window.decorView
        val insets = ViewCompat.getRootWindowInsets(decor)
        val windowHeight = decor.height
        return if (insets != null && insets.isVisible(WindowInsetsCompat.Type.ime())) {
            windowHeight - insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
        } else {
            windowHeight
        }
    }

    private fun imeVisible(): Boolean =
        ViewCompat.getRootWindowInsets(composeRule.activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true

    private fun screenshot(name: String) {
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val dir = File(context.cacheDir, "captures").apply { mkdirs() }
        val metrics = context.resources.displayMetrics
        val size = "${(metrics.widthPixels / metrics.density).toInt()}x${(metrics.heightPixels / metrics.density).toInt()}"
        File(dir, "teclado-$name-$size.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /**
     * Dibuja [content] en la ventana real, abre el teclado enfocando el campo [fieldIndex], captura y comprueba que [primary]
     * (el texto del botón principal) queda completo encima del teclado. `primary = null`: solo captura y revisa el campo.
     */
    private fun audit(
        name: String,
        primary: String?,
        fieldIndex: Int = 0,
        /** El botón no cabe con el teclado abierto y se alcanza desplazando (p. ej. Crear cuenta, que tiene cuatro campos). */
        reachableByScroll: Boolean = false,
        content: @Composable () -> Unit,
    ) {
        composeRule.runOnUiThread { composeRule.activity.enableEdgeToEdge() }
        composeRule.setContent { EndSecurityTheme { content() } }
        composeRule.waitForIdle()
        composeRule.onAllNodes(hasSetTextAction())[fieldIndex].performClick()
        // Al tocar el campo la ventana de la prueba no siempre pide el teclado: se pide explícitamente, como lo haría el sistema.
        composeRule.runOnUiThread {
            val window = composeRule.activity.window
            WindowCompat.getInsetsController(window, window.decorView).show(WindowInsetsCompat.Type.ime())
        }
        // Espera a que el teclado esté a la vista y termine su animación (y el acomodo del contenido).
        val deadline = System.currentTimeMillis() + 10_000
        while (!imeVisible() && System.currentTimeMillis() < deadline) Thread.sleep(100)
        assertTrue("el teclado no salió (¿hw.keyboard=no en el AVD?)", imeVisible())
        Thread.sleep(1_500)
        composeRule.waitForIdle()
        screenshot(name)

        val imeTop = imeTopPx()
        composeRule.onAllNodes(hasSetTextAction())[fieldIndex].assertIsDisplayed()
        val field = composeRule.onAllNodes(hasSetTextAction())[fieldIndex].fetchSemanticsNode()
        // En 360×640 (≈250 dp útiles con el teclado) el campo puede quedar rozando el borde del área desplazable: ahí basta con que se
        // vea y se alcance desplazando; a 412×891 tiene que verse completo.
        val smallScreen = context.resources.displayMetrics.run { heightPixels / density } < 800f
        if (smallScreen) {
            assertTrue("el campo enfocado queda bajo el teclado", field.boundsInWindow.top < imeTop)
        } else {
            assertFullyAbove("el campo enfocado", field.boundsInWindow.bottom, field.boundsInWindow.height, field.size.height, imeTop)
        }
        if (primary != null) {
            val node = composeRule.onNodeWithText(primary)
            val before = node.fetchSemanticsNode()
            val seenWithoutScrolling = before.boundsInWindow.bottom <= imeTop + 2 && before.boundsInWindow.height >= before.size.height - 1
            // En un teléfono de 640 dp de alto el teclado deja ~250 dp: ahí basta con alcanzarlo desplazando.
            if (!reachableByScroll && !smallScreen) {
                // Lo que promete la regla: completo y a la vista, sin que la persona tenga que desplazar.
                assertTrue("«$primary» no se ve completo con el teclado abierto (abajo=${before.boundsInWindow.bottom}, teclado=$imeTop, alto=${before.boundsInWindow.height}/${before.size.height})", seenWithoutScrolling)
            }
            runCatching { node.performScrollTo() }
            composeRule.waitForIdle()
            val after = node.fetchSemanticsNode()
            assertFullyAbove("«$primary»", after.boundsInWindow.bottom, after.boundsInWindow.height, after.size.height, imeTop)
            assertTrue("«$primary» queda cortado por arriba (${after.boundsInWindow.top})", after.boundsInWindow.top >= 0f)
            screenshot("$name-boton")
        }
    }

    private fun assertFullyAbove(what: String, bottom: Float, visibleHeight: Float, fullHeight: Int, imeTop: Int) {
        assertTrue("$what queda bajo el teclado ($bottom > $imeTop)", bottom <= imeTop + 2)
        assertTrue("$what queda cortado ($visibleHeight de $fullHeight px a la vista)", visibleHeight >= fullHeight - 1)
    }

    // ---------------------------------------------------------------- Acceso

    @Test
    fun iniciarSesion() = audit("login", str(R.string.login_submit)) {
        LoginScreen(
            state = LoginUiState(),
            passwordState = TextFieldState(),
            passwordVisible = false,
            onPasswordVisibleChange = {},
            onAction = {},
            onSubmit = {},
            onCreateAccount = {},
        )
    }

    @Test
    fun crearCuenta() = audit("crear-cuenta", str(R.string.signup_submit), reachableByScroll = true) {
        SignUpScreen(
            state = SignUpUiState(),
            passwordState = TextFieldState(),
            confirmationState = TextFieldState(),
            passwordVisible = false,
            onPasswordVisibleChange = {},
            onConfirmationFocusLost = {},
            onSubmit = {},
            onAction = {},
            onBack = {},
            openedDocument = null,
            onOpenLegalDocument = {},
            onDismissLegalDocument = {},
        )
    }

    @Test
    fun verificarCorreo() = audit("verificar-correo", str(R.string.verify_submit)) {
        VerifyEmailScreen(state = VerifyEmailUiState(email = "ana.lopez@correo.com", resendSecondsLeft = 45), onAction = {}, onBack = {})
    }

    @Test
    fun segundoFactorDelAcceso() = audit("2fa-acceso", str(R.string.two_factor_submit)) {
        TwoFactorScreen(state = TwoFactorUiState(), onAction = {}, onBack = {}, onUseBackupCode = {})
    }

    @Test
    fun codigoDeRespaldo() = audit("codigo-respaldo", str(R.string.backup_code_submit)) {
        BackupCodeScreen(state = BackupCodeUiState(), codeState = TextFieldState(), onSubmit = {}, onBack = {})
    }

    @Test
    fun desbloquear() = audit("desbloquear", str(R.string.unlock_submit)) {
        UnlockScreen(
            state = UnlockUiState(biometricAvailable = true, displayName = "Ana"),
            passwordState = TextFieldState(),
            passwordVisible = false,
            onPasswordVisibleChange = {},
            onSubmit = {},
            onAction = {},
        )
    }


    // ---------------------------------------------------------------- Desbloquear: la fila «Acciones» (tarea 24)

    private val dp get() = context.resources.displayMetrics.density

    private fun boundsOf(node: androidx.compose.ui.test.SemanticsNodeInteraction) = node.fetchSemanticsNode().boundsInWindow

    private fun renderUnlock(biometric: Boolean) {
        composeRule.runOnUiThread { composeRule.activity.enableEdgeToEdge() }
        composeRule.setContent {
            EndSecurityTheme {
                UnlockScreen(
                    state = UnlockUiState(biometricAvailable = biometric, displayName = "Ana"),
                    passwordState = TextFieldState(),
                    passwordVisible = false,
                    onPasswordVisibleChange = {},
                    onSubmit = {},
                    onAction = {},
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun openKeyboard(fieldIndex: Int = 0): Int {
        composeRule.onAllNodes(hasSetTextAction())[fieldIndex].performClick()
        composeRule.runOnUiThread {
            val window = composeRule.activity.window
            WindowCompat.getInsetsController(window, window.decorView).show(WindowInsetsCompat.Type.ime())
        }
        val deadline = System.currentTimeMillis() + 10_000
        while (!imeVisible() && System.currentTimeMillis() < deadline) Thread.sleep(100)
        assertTrue("el teclado no salió (¿hw.keyboard=no en el AVD?)", imeVisible())
        Thread.sleep(1_500)
        composeRule.waitForIdle()
        return imeTopPx()
    }

    private fun closeKeyboard() {
        composeRule.runOnUiThread {
            val window = composeRule.activity.window
            WindowCompat.getInsetsController(window, window.decorView).hide(WindowInsetsCompat.Type.ime())
        }
        val deadline = System.currentTimeMillis() + 10_000
        while (imeVisible() && System.currentTimeMillis() < deadline) Thread.sleep(100)
        Thread.sleep(1_000)
        composeRule.waitForIdle()
    }

    /** La fila «Acciones»: huella (si hay) y «Desbloquear», con sus medidas y su posición respecto del campo y del teclado. */
    private fun unlockRowChecks(biometric: Boolean, withKeyboard: Boolean, naturalBottom: Float?, imeTop: Int) {
        val unlock = boundsOf(composeRule.onNodeWithText(str(R.string.unlock_submit)))
        val field = boundsOf(composeRule.onAllNodes(hasSetTextAction())[0])
        val fullWidth = composeRule.activity.window.decorView.width.toFloat()
        // El botón mide 56 dp de alto y la fila empieza 28 dp bajo el campo (Figma 36:553).
        assertEquals("alto de «Desbloquear»", 56f * dp, unlock.height, 1.5f)
        if (!withKeyboard) assertEquals("la fila va 28 dp bajo el campo", 28f * dp, unlock.top - field.bottom, 2f * dp)
        if (biometric) {
            val finger = boundsOf(composeRule.onNodeWithContentDescription(str(R.string.unlock_biometric)))
            assertEquals("la huella es de 56 × 56", 56f * dp, finger.width, 1.5f)
            assertEquals("la huella es de 56 × 56", 56f * dp, finger.height, 1.5f)
            assertEquals("12 dp entre la huella y «Desbloquear»", 12f * dp, unlock.left - finger.right, 1.5f)
            assertEquals("la huella a la misma altura que «Desbloquear»", unlock.top, finger.top, 1.5f)
            assertTrue("la huella va a la izquierda de «Desbloquear»", finger.right <= unlock.left)
            assertEquals("la huella empieza en el margen de 32 dp", 32f * dp, finger.left, 1.5f)
        } else {
            // Sin huella, «Desbloquear» ocupa todo el ancho (32 dp de margen a cada lado).
            assertEquals("«Desbloquear» a todo el ancho", fullWidth - 64f * dp, unlock.width, 2f)
        }
        if (withKeyboard) {
            val distance = (imeTop - unlock.bottom) / dp
            val natural = naturalBottom!!
            if (natural + 16f * dp <= imeTop + 1f) {
                // Ya cabía sin moverse (pantalla alta): no se mueve nada.
                assertEquals("la fila no debía moverse", natural, unlock.bottom, 1.5f)
                assertTrue("la fila queda a $distance dp del teclado", distance >= 14f)
            } else {
                assertTrue("la fila debe quedar a 16 dp ± 2 del teclado y quedó a $distance dp", distance in 14f..18f)
            }
            // Los dos botones, completos y a la vista.
            composeRule.onNodeWithText(str(R.string.unlock_submit)).assertIsDisplayed()
            if (biometric) composeRule.onNodeWithContentDescription(str(R.string.unlock_biometric)).assertIsDisplayed()
            // El título, el texto y el campo se ven siempre.
            composeRule.onNodeWithText(str(R.string.unlock_body)).assertIsDisplayed()
            composeRule.onAllNodes(hasSetTextAction())[0].assertIsDisplayed()
            assertTrue("el campo queda bajo el teclado", field.bottom <= imeTop)
        }
    }

    private fun unlockScenario(biometric: Boolean, name: String) {
        renderUnlock(biometric)
        // Que el teclado de una prueba anterior no falsee la posición «natural» (sin teclado).
        if (imeVisible()) closeKeyboard()
        assertTrue("el teclado debía estar cerrado al empezar", !imeVisible())
        screenshot("$name-sin-teclado")
        unlockRowChecks(biometric, withKeyboard = false, naturalBottom = null, imeTop = 0)
        val natural = boundsOf(composeRule.onNodeWithText(str(R.string.unlock_submit))).bottom
        val imeTop = openKeyboard()
        screenshot("$name-con-teclado")
        unlockRowChecks(biometric, withKeyboard = true, naturalBottom = natural, imeTop = imeTop)
        // Al cerrar el teclado todo vuelve a su lugar.
        closeKeyboard()
        assertEquals("la fila no volvió a su lugar", natural, boundsOf(composeRule.onNodeWithText(str(R.string.unlock_submit))).bottom, 1.5f)
        screenshot("$name-cerrado")
    }

    @Test
    fun desbloquearFilaAccionesConHuella() = unlockScenario(biometric = true, name = "desbloquear-acciones-huella")

    @Test
    fun desbloquearFilaAccionesSinHuella() = unlockScenario(biometric = false, name = "desbloquear-acciones-sin-huella")


    // ---------------------------------------------------------------- Iniciar sesión: la misma regla (tarea 24)

    @Test
    fun iniciarSesionElBotonQuedaA16DpDelTeclado() {
        composeRule.runOnUiThread { composeRule.activity.enableEdgeToEdge() }
        composeRule.setContent {
            EndSecurityTheme {
                LoginScreen(
                    state = LoginUiState(),
                    passwordState = TextFieldState(),
                    passwordVisible = false,
                    onPasswordVisibleChange = {},
                    onAction = {},
                    onSubmit = {},
                    onCreateAccount = {},
                )
            }
        }
        composeRule.waitForIdle()
        if (imeVisible()) closeKeyboard()
        screenshot("login-regla-sin-teclado")
        val button = composeRule.onNodeWithText(str(R.string.login_submit))
        val natural = boundsOf(button).bottom
        // Se enfoca la contraseña: con el campo de correo Gboard a veces muestra su barra flotante en vez del teclado acoplado.
        val imeTop = openKeyboard(fieldIndex = 1)
        screenshot("login-regla-con-teclado")
        // Con el teclado, el botón principal queda completo y a 16 dp ± 2 sobre él; si ya cabía, no se mueve.
        button.assertIsDisplayed()
        val now = boundsOf(button)
        val distance = (imeTop - now.bottom) / dp
        println("LOGIN-REGLA natural=${natural / dp} dp, con teclado: bottom=${now.bottom / dp} dp, distancia=$distance dp")
        if (natural + 16f * dp <= imeTop + 1f) {
            assertEquals("el botón no debía moverse", natural, now.bottom, 1.5f)
        } else {
            assertTrue("el botón de Iniciar sesión debe quedar a 16 dp ± 2 del teclado y quedó a $distance dp", distance in 14f..18f)
        }
    }

    // ---------------------------------------------------------------- Bóveda

    @Test
    fun nuevaContrasena() = audit("nueva-contrasena", str(R.string.item_form_save)) {
        ItemEditScreen(
            state = ItemEditUiState(isNew = true, status = EditStatus.Ready),
            fields = androidx.compose.runtime.remember { FormFields() },
            passwordVisible = false,
            onPasswordVisibleChange = {},
            formReady = true,
            onBack = {},
            onSave = {},
            onTabSelected = {},
            onDiscardConfirm = {},
            onDiscardDismiss = {},
        )
    }

    @Test
    fun buscarEnLaBoveda() = audit("busqueda-boveda", null) {
        val rows = (1..12).map { VaultRow.Item(UUID.randomUUID(), "Sitio $it", "usuario$it@correo.com", "S") }
        VaultListScreen(
            state = VaultListUiState(isLoading = false, rows = rows, totalCount = rows.size),
            onTabSelected = {},
            onQueryChange = {},
            onLockNow = {},
            onItemClick = {},
            onAddClick = {},
            onUndoDelete = {},
        )
    }

    // ---------------------------------------------------------------- Ajustes y cuenta

    @Test
    fun contrasenaMaestra() = audit("contrasena-maestra", str(R.string.cp_submit)) {
        ChangePasswordScreen(
            state = ChangePasswordUiState(),
            currentState = TextFieldState(),
            newState = TextFieldState(),
            confirmationState = TextFieldState(),
            currentVisible = false,
            onCurrentVisibleChange = {},
            newVisible = false,
            onNewVisibleChange = {},
            onConfirmationFocusLost = {},
            onSubmit = {},
            onAction = {},
            onBack = {},
            onTabSelected = {},
        )
    }

    @Test
    fun correoNuevo() = audit("correo-nuevo", str(R.string.email_continue)) {
        ChangeEmailScreen(
            state = ChangeEmailUiState(currentEmail = "luis.ruiz@upchiapas.edu.mx", twoFactorRequired = false),
            onAction = {},
            onTabSelected = {},
        )
    }

    @Test
    fun correoCodigo() = audit("correo-codigo", str(R.string.email_code_submit)) {
        ChangeEmailScreen(
            state = ChangeEmailUiState(
                currentEmail = "luis.ruiz@upchiapas.edu.mx", newEmail = "luis.ruiz@gmail.com", twoFactorRequired = false, step = EmailStep.Code,
            ),
            onAction = {},
            onTabSelected = {},
        )
    }

    @Test
    fun segundoFactorPaso2Codigo() = audit("2fa-paso2", str(R.string.tf_confirm)) {
        TwoFactorSetupScreen(state = TwoFactorSetupUiState(step = SetupStep.Code, secondsLeft = 18), onAction = {}, onTabSelected = {})
    }

    @Test
    fun eliminarCuenta() = audit("eliminar-cuenta", str(R.string.delete_submit)) {
        DeleteAccountScreen(
            state = DeleteAccountUiState(twoFactorRequired = true),
            passwordState = TextFieldState(),
            passwordVisible = false,
            onPasswordVisibleChange = {},
            onSubmit = {},
            onConfirm = {},
            onAction = {},
            onBack = {},
            onTabSelected = {},
        )
    }

    @Test
    fun hojaDeReautenticacion() = audit("hoja-reautenticacion", str(R.string.tf_reauth_generate)) {
        // La hoja real (con su `imePadding`), no solo su contenido.
        ReauthSheet(
            body = str(R.string.tf_reauth_body_regenerate),
            confirmLabel = str(R.string.tf_reauth_generate),
            danger = false,
            state = ReauthSheetState(),
            onCodeChange = {},
            onPasswordEmptinessChanged = {},
            onConfirm = {},
            onDismiss = {},
        )
    }
}
