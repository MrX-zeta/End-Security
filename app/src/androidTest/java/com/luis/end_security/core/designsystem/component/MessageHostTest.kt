package com.luis.end_security.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.luis.end_security.R
import com.luis.end_security.core.designsystem.theme.EndSecurityTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** El anfitrión de avisos (tarea 26): posición encima de la barra, entrada, salida, reemplazo y los avisos pedidos por las pantallas. */
@RunWith(AndroidJUnit4::class)
class MessageHostTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var host: MessageHostState
    private var showHostedA by mutableStateOf(false)
    private var showHostedB by mutableStateOf(false)
    private var barVisible by mutableStateOf(true)

    private fun frames(count: Int) = repeat(count) { composeRule.mainClock.advanceTimeByFrame() }

    private fun setHost(reduced: Boolean = false) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            EndSecurityTheme {
                host = rememberMessageHostState()
                CompositionLocalProvider(LocalMessageHost provides host) {
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f)) {
                            MessageHost(host, Modifier.align(Alignment.BottomCenter), reducedMotion = reduced)
                            if (showHostedA) HostedMessage(text = "Aviso A", icon = R.drawable.ic_check_16)
                            if (showHostedB) HostedMessage(text = "Aviso B", icon = R.drawable.ic_check_16)
                        }
                        if (barVisible) Box(Modifier.fillMaxWidth().height(80.dp).testTag("barra"))
                    }
                }
            }
        }
        frames(2)
    }

    @Test
    fun el_aviso_sale_encima_de_la_barra_y_no_la_cubre() {
        setHost()
        host.post("Gmail se guardó", R.drawable.ic_check_16)
        frames(2)
        composeRule.mainClock.advanceTimeBy(400)
        val message = composeRule.onNodeWithText("Gmail se guardó").getBoundsInRoot()
        val bar = composeRule.onNodeWithTag("barra").getBoundsInRoot()
        assertTrue("el aviso termina antes de la barra (${message.bottom} <= ${bar.top})", message.bottom.value <= bar.top.value + 0.5f)
    }

    @Test
    fun el_aviso_entra_subiendo_16_dp_en_200_ms_y_sale_mas_rapido() {
        setHost()
        host.post("Se guardó", R.drawable.ic_check_16, durationMillis = 60_000)
        frames(3) // el contenedor ya tiene su tamaño
        val start = composeRule.onNodeWithText("Se guardó").getBoundsInRoot().top.value
        composeRule.mainClock.advanceTimeBy(260)
        val rest = composeRule.onNodeWithText("Se guardó").getBoundsInRoot().top.value
        composeRule.mainClock.advanceTimeBy(800)
        val final = composeRule.onNodeWithText("Se guardó").getBoundsInRoot().top.value
        assertTrue("entra desde abajo ($start > $final)", start > final + 0.5f)
        assertTrue("sube a lo sumo 16 dp (${start - final})", start - final <= 16.5f)
        assertEquals("a los ~260 ms ya está en su lugar (entra en 200 ms)", final, rest, 0.5f)
    }

    @Test
    fun el_aviso_sale_bajando_y_desvaneciendose_en_150_ms() {
        setHost()
        host.post("Adiós", R.drawable.ic_check_16, durationMillis = 1_000)
        composeRule.mainClock.advanceTimeBy(600)
        val rest = composeRule.onNodeWithText("Adiós").getBoundsInRoot().top.value
        composeRule.mainClock.advanceTimeBy(500) // llega a su hora y empieza a salir
        frames(3)
        val leaving = composeRule.onNodeWithText("Adiós").getBoundsInRoot().top.value
        assertTrue("baja al salir ($leaving > $rest)", leaving > rest + 0.5f)
        composeRule.mainClock.advanceTimeBy(250)
        composeRule.onNodeWithText("Adiós").assertDoesNotExist()
    }

    @Test
    fun un_aviso_pedido_desaparece_solo_tras_su_tiempo() {
        setHost()
        host.post("Cambios guardados", R.drawable.ic_check_16, durationMillis = 1_000)
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.onNodeWithText("Cambios guardados").assertIsDisplayed()
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.onNodeWithText("Cambios guardados").assertDoesNotExist()
    }

    @Test
    fun si_llega_otro_el_actual_sale_y_el_nuevo_espera_a_que_salga_sin_amontonarse() {
        setHost()
        host.post("Primero", R.drawable.ic_check_16, durationMillis = 60_000)
        composeRule.mainClock.advanceTimeBy(400)
        val firstRest = composeRule.onNodeWithText("Primero").getBoundsInRoot().top.value
        host.post("Segundo", R.drawable.ic_check_16, durationMillis = 60_000)
        frames(1)
        // Mientras sale el primero (150 ms) el segundo se queda abajo, sin haber empezado a entrar.
        val secondWhileFirstLeaves = (0 until 6).map {
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.onNodeWithText("Segundo").getBoundsInRoot().top.value
        }
        val firstDropped = composeRule.onNodeWithText("Primero").getBoundsInRoot().top.value
        composeRule.mainClock.advanceTimeBy(800)
        composeRule.onNodeWithText("Primero").assertDoesNotExist()
        val secondRest = composeRule.onNodeWithText("Segundo").getBoundsInRoot().top.value
        composeRule.onNodeWithText("Segundo").assertIsDisplayed()
        assertTrue("el primero ya bajó al salir ($firstDropped > $firstRest)", firstDropped > firstRest + 0.5f)
        secondWhileFirstLeaves.forEach { top ->
            assertTrue("el segundo espera abajo mientras sale el primero ($top vs reposo $secondRest)", top >= secondRest + 15f)
        }
    }

    @Test
    fun un_aviso_pedido_por_la_pantalla_dura_lo_que_dure_y_su_texto_se_actualiza_en_su_lugar() {
        setHost()
        showHostedA = true
        frames(2)
        composeRule.mainClock.advanceTimeBy(400)
        composeRule.onNodeWithText("Aviso A").assertIsDisplayed()
        assertEquals(1, host.count)
        showHostedA = false
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.onNodeWithText("Aviso A").assertDoesNotExist()
        assertEquals(0, host.count)
    }

    @Test
    fun al_irse_el_aviso_reciente_vuelve_el_anterior_si_sigue_pedido() {
        setHost()
        showHostedA = true
        frames(2)
        composeRule.mainClock.advanceTimeBy(400)
        host.post("Resultado", R.drawable.ic_check_16, durationMillis = 800)
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.onNodeWithText("Resultado").assertIsDisplayed()
        composeRule.mainClock.advanceTimeBy(1_500)
        composeRule.onNodeWithText("Aviso A").assertIsDisplayed()
    }

    @Test
    fun con_quitar_animaciones_el_aviso_aparece_en_su_lugar_de_inmediato() {
        setHost(reduced = true)
        host.post("Directo", R.drawable.ic_check_16, durationMillis = 60_000)
        frames(3)
        val first = composeRule.onNodeWithText("Directo").getBoundsInRoot().top.value
        composeRule.mainClock.advanceTimeBy(400)
        assertEquals("sin desplazamiento", first, composeRule.onNodeWithText("Directo").getBoundsInRoot().top.value, 0.5f)
    }

    @Test
    fun sin_anfitrion_el_aviso_se_dibuja_donde_se_pide() {
        composeRule.setContent { EndSecurityTheme { HostedMessage(text = "Aquí mismo", icon = R.drawable.ic_check_16) } }
        composeRule.onNodeWithText("Aquí mismo").assertIsDisplayed()
    }

    @Test
    fun al_bloquear_clear_quita_los_avisos_y_cancela_sus_relojes() {
        setHost()
        host.post("Gmail se guardó", R.drawable.ic_check_16, durationMillis = 60_000)
        composeRule.mainClock.advanceTimeBy(400)
        composeRule.onNodeWithText("Gmail se guardó").assertIsDisplayed()
        host.clear()
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.onNodeWithText("Gmail se guardó").assertDoesNotExist()
        assertEquals(0, host.count)
        // Un aviso nuevo después sí se muestra: el reloj cancelado no lo afecta.
        host.post("Otro", R.drawable.ic_check_16, durationMillis = 60_000)
        composeRule.mainClock.advanceTimeBy(400)
        composeRule.onNodeWithText("Otro").assertIsDisplayed()
    }
}
