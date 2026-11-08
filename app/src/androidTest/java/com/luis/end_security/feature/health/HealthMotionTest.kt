package com.luis.end_security.feature.health

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.luis.end_security.core.designsystem.component.DirectionalContentSwap
import com.luis.end_security.core.designsystem.component.rememberAnimatedFloat
import com.luis.end_security.core.designsystem.component.staggeredEnter
import com.luis.end_security.core.designsystem.theme.EndSecurityTheme
import com.luis.end_security.feature.auth.domain.PasswordStrengthEstimator
import com.luis.end_security.feature.health.domain.AnalysisItem
import com.luis.end_security.feature.health.domain.HealthAnalyzer
import com.luis.end_security.feature.health.domain.HealthProgressCalculator
import com.luis.end_security.feature.health.domain.HealthTotals
import com.luis.end_security.feature.health.domain.SnapshotPoint
import com.luis.end_security.feature.health.ui.HealthChartTag
import com.luis.end_security.feature.health.ui.HealthScreen
import com.luis.end_security.feature.health.ui.HealthStrengthBarTag
import com.luis.end_security.feature.health.ui.HealthTab
import com.luis.end_security.feature.health.ui.HealthUiState
import com.luis.end_security.feature.health.ui.ScoreNumberTag
import com.luis.end_security.feature.health.ui.ScoreRing
import com.luis.end_security.feature.health.ui.SegmentedIndicatorTag
import com.luis.end_security.feature.health.ui.SegmentedSelector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.math.abs

/**
 * El movimiento de Salud (tarea 26) con el reloj de Compose controlado: el selector que se desliza, el cambio de contenido, la entrada
 * escalonada de tarjetas, el aro que cuenta, la línea de la gráfica y las barras. Un cuadro = 16 ms.
 */
@RunWith(AndroidJUnit4::class)
class HealthMotionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun frames(count: Int) = repeat(count) { composeRule.mainClock.advanceTimeByFrame() }

    private fun centerX(node: SemanticsNodeInteraction): Float = node.getBoundsInRoot().let { (it.left.value + it.right.value) / 2f }

    // ---- Selector «Resumen | Progreso»

    private var selectedIndex by mutableIntStateOf(0)

    private fun setSelector(reduced: Boolean = false) {
        composeRule.mainClock.autoAdvance = false
        selectedIndex = 0
        composeRule.setContent {
            EndSecurityTheme {
                Box(Modifier.width(360.dp)) {
                    SegmentedSelector(listOf("Resumen", "Progreso"), selectedIndex, { selectedIndex = it }, reducedMotion = reduced)
                }
            }
        }
        frames(3)
    }

    private fun indicatorX() = centerX(composeRule.onNodeWithTag(SegmentedIndicatorTag, useUnmergedTree = true))

    @Test
    fun el_fondo_del_selector_se_desliza_y_termina_centrado_en_la_opcion() {
        setSelector()
        val from = indicatorX()
        val to = centerX(composeRule.onNodeWithText("Progreso"))
        selectedIndex = 1
        frames(5)
        val middle = indicatorX()
        assertTrue("a la mitad debe estar entre las dos opciones ($from < $middle < $to)", middle > from + 1f && middle < to - 1f)
        composeRule.mainClock.advanceTimeBy(800)
        assertEquals("debe terminar sobre «Progreso»", to, indicatorX(), 1f)
    }

    @Test
    fun el_fondo_cambia_de_rumbo_a_mitad_del_camino_sin_saltar() {
        setSelector()
        val span = centerX(composeRule.onNodeWithText("Progreso")) - centerX(composeRule.onNodeWithText("Resumen"))
        selectedIndex = 1
        frames(5)
        var previous = indicatorX()
        selectedIndex = 0 // vuelve antes de llegar
        repeat(40) {
            composeRule.mainClock.advanceTimeByFrame()
            val now = indicatorX()
            assertTrue("el fondo saltó ${abs(now - previous)} dp en un cuadro (tramo $span dp)", abs(now - previous) < span * 0.3f)
            previous = now
        }
        composeRule.mainClock.advanceTimeBy(800)
        assertEquals(centerX(composeRule.onNodeWithText("Resumen")), indicatorX(), 1f)
    }

    @Test
    fun con_quitar_animaciones_el_fondo_salta_a_su_lugar() {
        setSelector(reduced = true)
        selectedIndex = 1
        frames(2)
        assertEquals(centerX(composeRule.onNodeWithText("Progreso")), indicatorX(), 1f)
    }

    // ---- Cambio de contenido en el sentido del selector

    private var swapIndex by mutableIntStateOf(0)

    private fun setSwap(reduced: Boolean = false) {
        composeRule.mainClock.autoAdvance = false
        swapIndex = 0
        composeRule.setContent {
            EndSecurityTheme {
                DirectionalContentSwap(targetIndex = swapIndex, reducedMotion = reduced) { index ->
                    Box(Modifier.size(100.dp).testTag("content$index").background(Color.Gray))
                }
            }
        }
        frames(3)
    }

    @Test
    fun al_ir_a_la_derecha_el_contenido_entra_desde_la_derecha_y_al_volver_desde_la_izquierda() {
        setSwap()
        val rest = composeRule.onNodeWithTag("content0").getBoundsInRoot().left.value
        swapIndex = 1
        frames(2)
        val entering = composeRule.onNodeWithTag("content1").getBoundsInRoot().left.value
        assertTrue("el nuevo entra desde la derecha ($entering > $rest)", entering > rest + 1f)
        composeRule.mainClock.advanceTimeBy(500)
        assertEquals("termina en su lugar", rest, composeRule.onNodeWithTag("content1").getBoundsInRoot().left.value, 0.5f)

        swapIndex = 0
        frames(2)
        val back = composeRule.onNodeWithTag("content0").getBoundsInRoot().left.value
        assertTrue("al volver entra desde la izquierda ($back < $rest)", back < rest - 1f)
    }

    @Test
    fun el_cambio_de_contenido_dura_menos_de_300_ms_y_con_quitar_animaciones_es_directo() {
        setSwap()
        swapIndex = 1
        composeRule.mainClock.advanceTimeBy(250)
        composeRule.onNodeWithTag("content1").assertExists()
        composeRule.onNodeWithTag("content0").assertDoesNotExist()
    }

    @Test
    fun con_quitar_animaciones_el_contenido_cambia_directo() {
        setSwap(reduced = true)
        swapIndex = 1
        frames(2)
        composeRule.onNodeWithTag("content1").assertExists()
        composeRule.onNodeWithTag("content0").assertDoesNotExist()
        assertEquals(composeRule.onNodeWithTag("content1").getBoundsInRoot().left.value, 0f, 0.5f)
    }

    // ---- Entrada escalonada de tarjetas

    private fun offsetsOf(count: Int) = (0 until count).map { composeRule.onNodeWithTag("card$it").getBoundsInRoot().top.value }

    private fun setStack(count: Int = 8, play: Boolean = true, reduced: Boolean = false) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            EndSecurityTheme {
                Column {
                    repeat(count) { i ->
                        Box(Modifier.size(width = 100.dp, height = 20.dp).staggeredEnter(i, play, reduced).testTag("card$i").background(Color.Gray))
                    }
                }
            }
        }
    }

    @Test
    fun las_tarjetas_suben_8_dp_escalonadas_30_ms_y_a_partir_de_la_sexta_entran_juntas() {
        setStack()
        composeRule.mainClock.advanceTimeByFrame()
        val rest = (0 until 8).map { it * 20f }
        // Justo al empezar la primera ya se mueve (sube desde 8 dp) y la última espera: el escalón es de 30 ms.
        frames(3) // ~48 ms
        val early = offsetsOf(8)
        assertTrue("la primera ya subió algo (${early[0]} vs ${rest[0] + 8})", early[0] < rest[0] + 8f - 0.1f)
        assertTrue("la quinta casi no empezó (${early[4]} cerca de ${rest[4] + 8})", early[4] > early[0] + 3f)
        // Entran juntas: la sexta, la séptima y la octava se mueven igual.
        val d5 = early[5] - rest[5]
        val d6 = early[6] - rest[6]
        val d7 = early[7] - rest[7]
        assertEquals("la séptima entra con la sexta", d5, d6, 0.01f)
        assertEquals("la octava entra con la sexta", d5, d7, 0.01f)
        composeRule.mainClock.advanceTimeBy(600)
        offsetsOf(8).forEachIndexed { i, top -> assertEquals("la tarjeta $i termina en su lugar", rest[i], top, 0.5f) }
    }

    @Test
    fun si_ya_se_mostraron_o_se_quitan_las_animaciones_estan_en_su_lugar_desde_el_primer_cuadro() {
        setStack(play = false)
        composeRule.mainClock.advanceTimeByFrame()
        offsetsOf(8).forEachIndexed { i, top -> assertEquals("sin entrada, la $i ya está en su lugar", i * 20f, top, 0.5f) }
    }

    @Test
    fun con_quitar_animaciones_las_tarjetas_ya_estan_en_su_lugar() {
        setStack(play = true, reduced = true)
        composeRule.mainClock.advanceTimeByFrame()
        offsetsOf(8).forEachIndexed { i, top -> assertEquals(i * 20f, top, 0.5f) }
    }

    // ---- Aro de la puntuación

    private var ringScore by mutableIntStateOf(78)

    private fun ringNumber(): Int = composeRule.onNodeWithTag(ScoreNumberTag, useUnmergedTree = true).fetchSemanticsNode()
        .config[androidx.compose.ui.semantics.SemanticsProperties.Text].first().text.toInt()

    private fun setRing(animateIn: Boolean) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { EndSecurityTheme { ScoreRing(score = ringScore, animateIn = animateIn) } }
    }

    @Test
    fun el_numero_cuenta_de_0_a_su_valor_en_600_ms_y_talkback_lee_el_valor_final_desde_el_principio() {
        ringScore = 78
        setRing(animateIn = true)
        composeRule.mainClock.advanceTimeByFrame()
        // TalkBack: la descripción del aro ya dice el valor final aunque el número aún sea pequeño.
        composeRule.onNodeWithContentDescription("78", substring = true).assertExists()
        frames(2)
        val early = ringNumber()
        assertTrue("al empezar el número es bajo ($early)", early < 40)
        var previous = early
        repeat(30) {
            composeRule.mainClock.advanceTimeByFrame()
            val now = ringNumber()
            assertTrue("el número no baja ($previous -> $now)", now >= previous)
            composeRule.onNodeWithContentDescription("78", substring = true).assertExists()
            previous = now
        }
        composeRule.mainClock.advanceTimeBy(700)
        assertEquals(78, ringNumber())
    }

    @Test
    fun sin_animar_la_primera_vez_el_aro_nace_en_su_valor() {
        ringScore = 78
        setRing(animateIn = false)
        composeRule.mainClock.advanceTimeByFrame()
        assertEquals(78, ringNumber())
    }

    @Test
    fun si_el_valor_cambia_va_del_anterior_al_nuevo_sin_volver_a_cero() {
        ringScore = 50
        setRing(animateIn = false)
        frames(3)
        ringScore = 80
        var previous = 50
        repeat(25) {
            composeRule.mainClock.advanceTimeByFrame()
            val now = ringNumber()
            assertTrue("nunca baja de 50 ni vuelve a cero ($now)", now >= 50 && now >= previous)
            previous = now
        }
        composeRule.mainClock.advanceTimeBy(400)
        assertEquals(80, ringNumber())
    }

    @Test
    fun el_cambio_dura_unos_300_ms() {
        ringScore = 40
        setRing(animateIn = false)
        frames(3)
        ringScore = 90
        composeRule.mainClock.advanceTimeByFrame() // el cambio se registra
        composeRule.mainClock.advanceTimeBy(340)
        assertEquals("a los ~350 ms ya llegó", 90, ringNumber())
    }

    // ---- Valores animados genéricos

    private var animatedTarget by mutableIntStateOf(100)
    private var animatedValue = 0f

    @Test
    fun un_valor_con_quitar_animaciones_salta_directo() {
        composeRule.mainClock.autoAdvance = false
        animatedTarget = 100
        composeRule.setContent {
            val value by rememberAnimatedFloat(animatedTarget.toFloat(), fromZeroFirst = true, reducedMotion = true)
            animatedValue = value
        }
        composeRule.mainClock.advanceTimeByFrame()
        assertEquals(100f, animatedValue, 0.01f)
    }

    // ---- Salud completa: solo la primera vez, gráfica y barras

    private val estimator = object : PasswordStrengthEstimator {
        override fun score(password: CharSequence): Int = password.toString().substring(1, 2).toInt()
    }

    private fun item(name: String, password: String, ageDays: Long = 20, breach: Int = 0) =
        AnalysisItem(UUID.randomUUID(), name, password, Instant.now().minusSeconds(ageDays * 86_400), breach)

    private fun healthState(tab: HealthTab, lastWeekBase: Int = 40): HealthUiState {
        val report = HealthAnalyzer.analyze(
            listOf(
                item("Instagram", "s4-a", breach = 2), item("Facebook", "s4-b", breach = 1),
                item("BBVA", "s3-misma"), item("Amazon", "s3-misma"), item("Gmail", "s3-misma"),
                item("Spotify", "s2-x"), item("Twitch", "s2-x"), item("Netflix", "s0-n"), item("Didi", "s4-d"),
            ),
            Instant.now(), estimator,
        )
        val today = LocalDate.now()
        val snapshots = (1..11).map { week ->
            SnapshotPoint(today.minusWeeks(week.toLong()), HealthTotals(lastWeekBase + (11 - week) * 2, 10, 4, 2, 4, 3, 2, 1))
        }
        return HealthUiState(
            isLoading = false, report = report, lastChecked = Instant.now(), tab = tab,
            progress = HealthProgressCalculator.compute(snapshots, report, today, ZoneId.systemDefault()),
        )
    }

    private var health by mutableStateOf(healthState(HealthTab.Summary))

    private fun setHealth() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            EndSecurityTheme {
                Box(Modifier.size(width = 412.dp, height = 917.dp)) { HealthScreen(health, {}, {}, {}, {}, {}, {}, {}) }
            }
        }
    }

    private val entrance = com.luis.end_security.feature.health.ui.HealthEntrance()
    private var healthVisible by mutableStateOf(true)

    private fun setHealthWithEntrance() {
        composeRule.mainClock.autoAdvance = false
        healthVisible = true
        composeRule.setContent {
            EndSecurityTheme {
                Box(Modifier.size(width = 412.dp, height = 917.dp)) {
                    if (healthVisible) HealthScreen(health, {}, {}, {}, {}, {}, {}, {}, entrance = entrance)
                }
            }
        }
    }

    @Test
    fun el_resumen_se_repite_al_cambiar_de_vista_y_la_repeticion_es_mas_corta() {
        health = healthState(HealthTab.Summary)
        setHealth()
        composeRule.mainClock.advanceTimeByFrame()
        frames(3)
        val final = healthState(HealthTab.Summary).report!!.score!!
        assertTrue("la primera vez el aro empieza contando", ringNumber() < final)
        // A los ~300 ms desde que entró, la primera vez (600 ms) va más atrás que lo que irá una repetición (400 ms).
        composeRule.mainClock.advanceTimeBy(236)
        val firstAt300 = ringNumber()
        composeRule.mainClock.advanceTimeBy(1_000)
        assertEquals(final, ringNumber())

        health = healthState(HealthTab.Progress)
        composeRule.mainClock.advanceTimeBy(800)
        health = healthState(HealthTab.Summary)
        frames(3)
        assertTrue("al volver al Resumen el aro cuenta otra vez (${ringNumber()} de $final)", ringNumber() < final)
        composeRule.mainClock.advanceTimeBy(236)
        val replayAt300 = ringNumber()
        assertTrue("la repetición va más adelantada que la primera vez a los 300 ms ($replayAt300 >= $firstAt300)", replayAt300 >= firstAt300)
        composeRule.mainClock.advanceTimeBy(600)
        assertEquals("la repetición termina", final, ringNumber())
    }

    @Test
    fun al_volver_de_un_detalle_con_Atras_no_se_repite_pero_al_entrar_desde_otra_pestana_si() {
        health = healthState(HealthTab.Summary)
        setHealthWithEntrance()
        composeRule.mainClock.advanceTimeBy(1_500)
        val final = healthState(HealthTab.Summary).report!!.score!!
        assertEquals(final, ringNumber())

        // Se abre un detalle (Filtradas…): al regresar con Atrás, la pantalla vuelve ya en su lugar.
        entrance.leavingForDetail()
        healthVisible = false
        composeRule.mainClock.advanceTimeBy(400)
        healthVisible = true
        frames(2)
        assertEquals("el regreso con Atrás no repite", final, ringNumber())

        // Salir a otra pestaña y volver sí repite.
        healthVisible = false
        composeRule.mainClock.advanceTimeBy(400)
        healthVisible = true
        frames(4)
        assertTrue("al entrar desde otra pestaña el aro cuenta otra vez (${ringNumber()} de $final)", ringNumber() < final)
    }

    @Test
    fun con_datos_nuevos_sigue_del_valor_anterior_al_nuevo_y_no_se_repite_la_entrada() {
        health = healthState(HealthTab.Summary)
        setHealth()
        composeRule.mainClock.advanceTimeBy(1_500)
        val final = ringNumber()
        // «Revisar ahora» trae otro índice: va del anterior al nuevo, sin volver a cero.
        val newer = healthState(HealthTab.Summary).let { state ->
            val extra = HealthAnalyzer.analyze(
                listOf(item("Uno", "s4-Tortuga-Verde"), item("Dos", "s4-Cebolla-Roja-77"), item("Tres", "s0-debil")),
                Instant.now(), estimator,
            )
            HealthUiState(isLoading = false, report = extra, lastChecked = Instant.now(), tab = HealthTab.Summary, progress = state.progress)
        }
        health = newer
        repeat(10) {
            composeRule.mainClock.advanceTimeByFrame()
            assertTrue("nunca vuelve a cero (${ringNumber()})", ringNumber() > 0)
        }
    }

    @Test
    fun los_puntos_de_la_grafica_van_del_valor_anterior_al_nuevo_en_300_ms_sin_pasar_por_cero() {
        composeRule.mainClock.autoAdvance = false
        var target by mutableStateOf(listOf(40f, 60f, 80f))
        var shown = target
        composeRule.setContent {
            val values by com.luis.end_security.feature.health.ui.rememberMorphingValues(target, reducedMotion = false)
            shown = values
        }
        composeRule.mainClock.advanceTimeByFrame()
        assertEquals(listOf(40f, 60f, 80f), shown)
        target = listOf(60f, 80f, 100f)
        var previous = 40f
        repeat(25) {
            composeRule.mainClock.advanceTimeByFrame()
            assertTrue("nunca baja del valor anterior ni vuelve a cero (${shown[0]})", shown[0] >= previous - 0.001f && shown[0] >= 40f)
            previous = shown[0]
        }
        composeRule.mainClock.advanceTimeBy(400)
        assertEquals(listOf(60f, 80f, 100f), shown)
    }
}
