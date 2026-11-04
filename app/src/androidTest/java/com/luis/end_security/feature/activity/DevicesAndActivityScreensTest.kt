package com.luis.end_security.feature.activity

import android.graphics.Bitmap
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
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
import com.luis.end_security.feature.activity.domain.ActivityDay
import com.luis.end_security.feature.activity.domain.ActivityEvent
import com.luis.end_security.feature.activity.domain.LastUsed
import com.luis.end_security.feature.activity.ui.ActivityAction
import com.luis.end_security.feature.activity.ui.ActivityPhase
import com.luis.end_security.feature.activity.ui.ActivityScreen
import com.luis.end_security.feature.activity.ui.ActivityUiState
import com.luis.end_security.feature.activity.ui.CloseSessionSheetContent
import com.luis.end_security.feature.activity.ui.DeviceRow
import com.luis.end_security.feature.activity.ui.DevicesAction
import com.luis.end_security.feature.activity.ui.DevicesNotice
import com.luis.end_security.feature.activity.ui.DevicesPhase
import com.luis.end_security.feature.activity.ui.DevicesScreen
import com.luis.end_security.feature.activity.ui.DevicesSheet
import com.luis.end_security.feature.activity.ui.DevicesUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.UUID

/**
 * Dispositivos (Figma 47:1817, 69:3411, 69:3850) y Actividad reciente (47:1938, 69:3513) dibujados en el emulador, con la
 * hoja de «Cerrar sesión». Las capturas van a `cache/captures`. Las pantallas no llevan `SecureScreen`: no muestran secretos.
 */
@RunWith(AndroidJUnit4::class)
class DevicesAndActivityScreensTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun str(id: Int) = context.getString(id)

    private fun capture(name: String) {
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File(context.cacheDir, "captures").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun uuid(n: Int) = UUID.fromString("00000000-0000-0000-0000-0000000000%02d".format(n))

    // ------------------------------------------------------------------ Dispositivos

    private val three = DevicesUiState(
        phase = DevicesPhase.Loaded,
        devices = listOf(
            DeviceRow(uuid(1), "Galaxy A54", current = true, lastUsed = LastUsed.Now),
            DeviceRow(uuid(2), "Pixel 7", current = false, lastUsed = LastUsed.Days(2)),
            DeviceRow(uuid(3), "Moto g84", current = false, lastUsed = LastUsed.Weeks(3)),
        ),
    )

    private fun renderDevices(
        state: DevicesUiState,
        actions: MutableList<DevicesAction> = mutableListOf(),
        onChangePassword: () -> Unit = {},
    ) {
        composeRule.setContent {
            EndSecurityTheme {
                Box(Modifier.size(width = 412.dp, height = 917.dp)) {
                    DevicesScreen(
                        state = state,
                        onAction = { actions += it },
                        onBack = {},
                        onChangePassword = onChangePassword,
                        onTabSelected = {},
                    )
                }
            }
        }
    }

    @Test
    fun dispositivosMuestraLosTextosDeFigma() {
        renderDevices(three)
        composeRule.onNodeWithText(str(R.string.devices_title)).assertIsDisplayed()
        // «3 dispositivos» en negrita y «con tu sesión abierta» en la segunda línea: un solo título de dos pesos.
        composeRule.onNodeWithText("3 dispositivos\ncon tu sesión abierta").assertIsDisplayed()
        composeRule.onNodeWithText("Galaxy A54").assertIsDisplayed()
        composeRule.onNodeWithText("Pixel 7").assertIsDisplayed()
        composeRule.onNodeWithText("Moto g84").assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.devices_active_now)).assertIsDisplayed()
        composeRule.onNodeWithText("Hace 2 días").assertIsDisplayed()
        composeRule.onNodeWithText("Hace 3 semanas").assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.devices_this)).assertIsDisplayed()
        composeRule.onNodeWithText(
            str(R.string.devices_hint_before) + str(R.string.devices_hint_link) + str(R.string.devices_hint_after),
            substring = true,
        ).assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.devices_close_others)).assertIsDisplayed()
        capture("tarea19-dispositivos")
    }

    @Test
    fun dispositivosUsaSingularConUnoYNoOfreceCerrarLosDemas() {
        renderDevices(three.copy(devices = three.devices.take(1)))
        composeRule.onNodeWithText("1 dispositivo\ncon tu sesión abierta").assertIsDisplayed()
        composeRule.onAllNodesWithText(str(R.string.devices_close_others)).assertCountEquals(0)
    }

    @Test
    fun dispositivosUsaPluralCon2() {
        renderDevices(three.copy(devices = three.devices.take(2)))
        composeRule.onNodeWithText("2 dispositivos\ncon tu sesión abierta").assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.devices_close_others)).assertIsDisplayed()
    }

    @Test
    fun laFilaActualNoTieneAccionYLasDemasSi() {
        val actions = mutableListOf<DevicesAction>()
        renderDevices(three, actions)
        composeRule.onNode(hasContentDescription("Galaxy A54", substring = true)).assertHasNoClickAction()
        composeRule.onNodeWithText("Pixel 7").assertHasClickAction()
        composeRule.onNodeWithText("Pixel 7").performClick()
        assertEquals(listOf<DevicesAction>(DevicesAction.RowClicked(uuid(2))), actions)
    }

    @Test
    fun cerrarLosDemasPideConfirmacion() {
        val actions = mutableListOf<DevicesAction>()
        renderDevices(three, actions)
        composeRule.onNodeWithText(str(R.string.devices_close_others)).performClick()
        assertEquals(listOf<DevicesAction>(DevicesAction.CloseOthersClicked), actions)
    }

    @Test
    fun laHojaDeCerrarUnaSesionUsaElDisenoDeCerrarSesionConSusTextos() {
        val actions = mutableListOf<DevicesAction>()
        renderDevices(three.copy(sheet = DevicesSheet.CloseOne(uuid(2), "Pixel 7")), actions)
        composeRule.onNodeWithText("¿Cerrar sesión en Pixel 7?").assertIsDisplayed()
        composeRule.onNodeWithText("Tendrá que volver a entrar con tu correo y tu contraseña maestra.").assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.devices_close_confirm)).assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.settings_sign_out_cancel)).assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.devices_close_confirm)).performClick()
        composeRule.onNodeWithText(str(R.string.settings_sign_out_cancel)).performClick()
        assertEquals(listOf(DevicesAction.SheetConfirmed, DevicesAction.SheetDismissed), actions)
    }

    @Test
    fun laHojaDeCerrarLosDemasTieneSusTextos() {
        renderDevices(three.copy(sheet = DevicesSheet.CloseOthers))
        composeRule.onNodeWithText("¿Cerrar sesión en los demás dispositivos?").assertIsDisplayed()
        composeRule.onNodeWithText("Solo este teléfono seguirá con tu sesión abierta.").assertIsDisplayed()
    }

    @Test
    fun elContenidoDeLaHojaSeVeEnUnaCapturaSinElMarcoDelSistema() {
        composeRule.setContent {
            EndSecurityTheme {
                Box(Modifier.size(width = 412.dp, height = 380.dp)) {
                    CloseSessionSheetContent(
                        title = "¿Cerrar sesión en Pixel 7?",
                        body = str(R.string.devices_close_one_body),
                        onConfirm = {},
                        onCancel = {},
                    )
                }
            }
        }
        composeRule.onNodeWithText("¿Cerrar sesión en Pixel 7?").assertIsDisplayed()
        capture("tarea19-hoja-cerrar-uno")
    }

    @Test
    fun mientrasSeCierraElBotonDeLaHojaNoSeVuelveATocar() {
        val actions = mutableListOf<DevicesAction>()
        renderDevices(three.copy(sheet = DevicesSheet.CloseOne(uuid(2), "Pixel 7"), isClosing = true), actions)
        composeRule.onNodeWithText(str(R.string.devices_close_confirm)).performClick()
        composeRule.onNodeWithText(str(R.string.settings_sign_out_cancel)).performClick()
        assertTrue("con el cierre en curso nada se confirma ni se cancela", actions.isEmpty())
    }

    @Test
    fun cargandoMuestraElEsqueletoYElTexto() {
        renderDevices(DevicesUiState(phase = DevicesPhase.Loading))
        composeRule.onNodeWithText(str(R.string.devices_loading_description)).assertIsDisplayed()
        composeRule.onAllNodesWithText(str(R.string.devices_close_others)).assertCountEquals(0)
        capture("tarea19-dispositivos-cargando")
    }

    @Test
    fun sinConexionMuestraElMensajeYReintentar() {
        val actions = mutableListOf<DevicesAction>()
        renderDevices(DevicesUiState(phase = DevicesPhase.Offline), actions)
        composeRule.onNodeWithText(str(R.string.devices_offline_title)).assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.devices_offline_body)).assertIsDisplayed()
        capture("tarea19-dispositivos-sin-conexion")
        composeRule.onNodeWithText(str(R.string.devices_retry)).performClick()
        assertEquals(listOf<DevicesAction>(DevicesAction.Retry), actions)
    }

    @Test
    fun losAvisosSeMuestran() {
        renderDevices(three.copy(notice = DevicesNotice.Closed("Pixel 7", 1)))
        composeRule.onNodeWithText("Cerraste la sesión en Pixel 7").assertIsDisplayed()
    }

    @Test
    fun elAvisoDeFalloParcialUsaElPlural() {
        renderDevices(three.copy(notice = DevicesNotice.SomeFailed(2, 1)))
        composeRule.onNodeWithText("No pudimos cerrar la sesión en 2 dispositivos. Intenta de nuevo.").assertIsDisplayed()
    }

    // ------------------------------------------------------------------ Actividad

    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.of(2026, 11, 4)

    private fun at(date: LocalDate, hour: Int, minute: Int): Instant = date.atTime(hour, minute).atZone(zone).toInstant()

    private fun time(instant: Instant) = DateFormat.getTimeFormat(context).format(Date(instant.toEpochMilli()))

    private fun e(id: Long, instant: Instant, type: String, ok: Boolean = true, device: String? = "Galaxy A54") =
        ActivityEvent(id, instant, type, ok, device)

    private val enabled = e(5, at(today, 10, 30), "two_factor_enabled")
    private val login = e(4, at(today, 10, 24), "login")
    private val failed = e(3, at(today.minusDays(1), 23, 12), "login", ok = false, device = null)
    private val changed = e(2, at(today.minusDays(1), 18, 5), "master_password_changed", device = "Pixel 7")
    private val newDevice = e(1, at(today.minusDays(1), 17, 40), "session_revoked", device = "Pixel 7")

    private val activity = ActivityUiState(
        phase = ActivityPhase.Loaded,
        events = listOf(enabled, login, failed, changed, newDevice),
        days = listOf(ActivityDay(today, listOf(enabled, login)), ActivityDay(today.minusDays(1), listOf(failed, changed, newDevice))),
        today = today,
    )

    private fun renderActivity(state: ActivityUiState, actions: MutableList<ActivityAction> = mutableListOf()) {
        composeRule.setContent {
            EndSecurityTheme {
                Box(Modifier.size(width = 412.dp, height = 917.dp)) {
                    ActivityScreen(
                        state = state,
                        onAction = { actions += it },
                        onBack = {},
                        onChangePassword = {},
                        onTabSelected = {},
                    )
                }
            }
        }
    }

    @Test
    fun actividadMuestraLosTextosDeFigmaAgrupadosPorDia() {
        renderActivity(activity)
        composeRule.onNodeWithText(str(R.string.activity_title)).assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.activity_today)).assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.activity_yesterday)).assertIsDisplayed()
        composeRule.onNodeWithText("Verificación activada", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Inicio de sesión", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Acceso fallido", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.activity_unknown_device), substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Contraseña maestra cambiada", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Sesión cerrada en un dispositivo", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText(time(enabled.occurredAt), substring = true).assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.activity_footer_before), substring = true).assertIsDisplayed()
        capture("tarea19-actividad")
    }

    @Test
    fun laFechaDeUnDiaViejoSeEscribeCompleta() {
        val older = today.minusDays(2) // lunes 2 de noviembre
        val event = e(9, at(older, 9, 0), "logout")
        renderActivity(activity.copy(events = listOf(event), days = listOf(ActivityDay(older, listOf(event)))))
        composeRule.onNodeWithText("lunes 2 de noviembre").assertIsDisplayed()
    }

    @Test
    fun lasFilasDeActividadNoTienenAccionDeClic() {
        renderActivity(activity)
        listOf("Verificación activada", "Inicio de sesión", "Acceso fallido", "Contraseña maestra cambiada").forEach {
            composeRule.onNode(hasText(it, substring = true)).assertHasNoClickAction()
        }
    }

    @Test
    fun unTituloLargoSeCortaYLaHoraNoSeMueve() {
        val longName = "Un teléfono con un nombre larguísimo que no cabe en la fila de la actividad reciente"
        val long = e(7, at(today, 11, 0), "recovery_codes_regenerated", device = longName)
        renderActivity(activity.copy(events = listOf(long), days = listOf(ActivityDay(today, listOf(long)))))
        composeRule.onNodeWithText(time(long.occurredAt), substring = true).assertIsDisplayed()
    }

    @Test
    fun unTipoDesconocidoDiceActividadEnTuCuenta() {
        val unknown = e(8, at(today, 12, 0), "evento_del_futuro")
        renderActivity(activity.copy(events = listOf(unknown), days = listOf(ActivityDay(today, listOf(unknown)))))
        composeRule.onNodeWithText(str(R.string.activity_kind_unknown), substring = true).assertIsDisplayed()
    }

    @Test
    fun alLlegarAlFinalConMasPaginasPideLaSiguiente() {
        val actions = mutableListOf<ActivityAction>()
        renderActivity(activity.copy(hasMore = true), actions)
        composeRule.waitForIdle()
        assertTrue(actions.contains(ActivityAction.LoadMore))
    }

    @Test
    fun sinMasPaginasNoPideNada() {
        val actions = mutableListOf<ActivityAction>()
        renderActivity(activity.copy(hasMore = false), actions)
        composeRule.waitForIdle()
        assertTrue(actions.isEmpty())
    }

    @Test
    fun cargandoMasMuestraUnIndicadorPequeno() {
        renderActivity(activity.copy(hasMore = true, loadingMore = true))
        composeRule.onNode(hasContentDescription(str(R.string.activity_loading_more_description))).assertIsDisplayed()
    }

    @Test
    fun siLaPaginaSiguienteFallaOfreceReintentar() {
        val actions = mutableListOf<ActivityAction>()
        renderActivity(activity.copy(hasMore = true, loadMoreFailed = true), actions)
        composeRule.onNodeWithText(str(R.string.activity_load_more_retry)).performClick()
        assertEquals(listOf<ActivityAction>(ActivityAction.Retry), actions)
    }

    @Test
    fun actividadCargandoMuestraElEsqueleto() {
        renderActivity(ActivityUiState(phase = ActivityPhase.Loading))
        composeRule.onNodeWithText(str(R.string.activity_loading_description)).assertIsDisplayed()
        capture("tarea19-actividad-cargando")
    }

    @Test
    fun actividadSinConexionMuestraElMismoPatronQueDispositivos() {
        val actions = mutableListOf<ActivityAction>()
        renderActivity(ActivityUiState(phase = ActivityPhase.Offline), actions)
        composeRule.onNodeWithText(str(R.string.activity_offline_title)).assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.devices_retry)).performClick()
        assertEquals(listOf<ActivityAction>(ActivityAction.Retry), actions)
    }

    @Test
    fun losFallidosSeMarcanComoErrorParaTalkBack() {
        renderActivity(activity)
        val node = composeRule.onNode(hasText("Acceso fallido", substring = true)).fetchSemanticsNode()
        val description = node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
        assertTrue("TalkBack anuncia «fallido»: $description", description?.contains("fallido") == true)
    }
}
