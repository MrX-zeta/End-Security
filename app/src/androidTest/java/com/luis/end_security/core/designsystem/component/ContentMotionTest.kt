package com.luis.end_security.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.luis.end_security.core.designsystem.theme.EndSecurityTheme
import com.luis.end_security.core.designsystem.theme.Motion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * El movimiento dentro de las pantallas (tarea 27, Paso 2) con el reloj de Compose controlado: se afirman posiciones, tamaños,
 * existencia y tiempos. Sin capturas. Un cuadro = 16 ms.
 */
@RunWith(AndroidJUnit4::class)
class ContentMotionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun frames(count: Int) = repeat(count) { composeRule.mainClock.advanceTimeByFrame() }

    private fun top(tag: String) = composeRule.onNodeWithTag(tag).getBoundsInRoot().top.value
    private fun left(tag: String) = composeRule.onNodeWithTag(tag).getBoundsInRoot().left.value
    private fun height(tag: String) = composeRule.onNodeWithTag(tag).getBoundsInRoot().let { it.bottom.value - it.top.value }

    // ---- Pasos de un flujo: la misma transición de avanzar y regresar (60 dp y fundido, 260/220 ms), mismo camino al volver

    private var step by mutableIntStateOf(0)

    private fun setSteps(reduced: Boolean = false) {
        composeRule.mainClock.autoAdvance = false
        step = 0
        composeRule.setContent {
            EndSecurityTheme {
                StepSwap(step = step, reducedMotion = reduced) { index ->
                    Box(Modifier.size(100.dp).testTag("paso$index").background(Color.Gray))
                }
            }
        }
        frames(3)
    }

    @Test
    fun avanzar_entra_desde_la_derecha_60_dp_y_regresar_desde_la_izquierda() {
        setSteps()
        val rest = left("paso0")
        step = 1
        frames(2)
        val entering = left("paso1")
        assertTrue("avanza: entra desde la derecha ($entering > $rest)", entering > rest + 1f)
        assertTrue("el desplazamiento no pasa de 60 dp (${entering - rest})", entering - rest <= 60.5f)
        composeRule.mainClock.advanceTimeBy(300)
        assertEquals("termina en su lugar", rest, left("paso1"), 0.5f)

        step = 0
        frames(2)
        val back = left("paso0")
        assertTrue("regresa: entra desde la izquierda ($back < $rest)", back < rest - 1f)
        assertTrue("mismo camino: también hasta 60 dp (${rest - back})", rest - back <= 60.5f)
    }

    @Test
    fun el_cambio_de_paso_dura_menos_de_300_ms() {
        setSteps()
        step = 1
        composeRule.mainClock.advanceTimeBy(Motion.PushLimitMillis)
        composeRule.onNodeWithTag("paso1").assertExists()
        composeRule.onNodeWithTag("paso0").assertDoesNotExist()
    }

    @Test
    fun con_quitar_animaciones_el_paso_cambia_directo() {
        setSteps(reduced = true)
        step = 1
        frames(2)
        composeRule.onNodeWithTag("paso1").assertExists()
        composeRule.onNodeWithTag("paso0").assertDoesNotExist()
        assertEquals(0f, left("paso1"), 0.5f)
    }

    // ---- De «cargando» al contenido: fundido cruzado de 200 ms

    private var loading by mutableStateOf(true)

    private fun showLoading(reduced: Boolean = false) {
        composeRule.mainClock.autoAdvance = false
        loading = true
        composeRule.setContent {
            EndSecurityTheme {
                LoadingCrossfade(loading, reducedMotion = reduced) { isLoading ->
                    Box(Modifier.size(100.dp).testTag(if (isLoading) "esqueleto" else "contenido").background(Color.Gray))
                }
            }
        }
        frames(3)
    }

    @Test
    fun el_esqueleto_y_el_contenido_conviven_un_momento_y_luego_queda_el_contenido() {
        showLoading()
        composeRule.onNodeWithTag("esqueleto").assertExists()
        loading = false
        frames(4)
        composeRule.onNodeWithTag("esqueleto").assertExists()
        composeRule.onNodeWithTag("contenido").assertExists()
        composeRule.mainClock.advanceTimeBy(300)
        composeRule.onNodeWithTag("esqueleto").assertDoesNotExist()
        composeRule.onNodeWithTag("contenido").assertExists()
    }

    @Test
    fun el_fundido_del_esqueleto_no_desplaza_nada() {
        showLoading()
        val rest = left("esqueleto")
        loading = false
        frames(4)
        assertEquals("sin movimiento, solo fundido", rest, left("contenido"), 0.5f)
    }

    @Test
    fun con_quitar_animaciones_el_contenido_reemplaza_al_esqueleto_de_inmediato() {
        showLoading(reduced = true)
        loading = false
        frames(2)
        composeRule.onNodeWithTag("esqueleto").assertDoesNotExist()
        composeRule.onNodeWithTag("contenido").assertExists()
    }

    // ---- Lo que aparece o desaparece dentro de una pantalla: se despliega (200 ms) y se pliega (150 ms)

    private var shown by mutableStateOf(false)

    private fun setReveal(reduced: Boolean = false) {
        composeRule.mainClock.autoAdvance = false
        shown = false
        composeRule.setContent {
            EndSecurityTheme {
                Column {
                    Reveal(visible = shown, reducedMotion = reduced) { Box(Modifier.fillMaxWidth().height(60.dp).testTag("error").background(Color.Red)) }
                    Box(Modifier.size(100.dp, 20.dp).testTag("debajo").background(Color.Gray))
                }
            }
        }
        frames(3)
    }

    @Test
    fun al_aparecer_se_despliega_y_lo_de_abajo_se_desplaza_suave() {
        setReveal()
        val rest = top("debajo")
        shown = true
        frames(3)
        val middle = top("debajo")
        assertTrue("lo de abajo ya empezó a bajar ($middle > $rest)", middle > rest + 1f)
        assertTrue("pero no ha llegado (${middle - rest} < 60)", middle - rest < 59f)
        composeRule.mainClock.advanceTimeBy(260)
        assertEquals("termina 60 dp más abajo", rest + 60f, top("debajo"), 0.5f)
        assertEquals(60f, height("error"), 0.5f)
    }

    @Test
    fun al_desaparecer_se_pliega_en_150_ms_y_lo_de_abajo_sube_suave() {
        setReveal()
        shown = true
        composeRule.mainClock.advanceTimeBy(400)
        val open = top("debajo")
        shown = false
        frames(3)
        val middle = top("debajo")
        assertTrue("sube suave, sin saltar ($middle < $open)", middle < open - 0.5f && middle > open - 59f)
        composeRule.mainClock.advanceTimeBy(Motion.RevealOut.toLong() + 64)
        assertEquals("a los ~150 ms ya terminó de plegarse", open - 60f, top("debajo"), 0.5f)
    }

    @Test
    fun con_quitar_animaciones_aparece_y_desaparece_de_golpe() {
        setReveal(reduced = true)
        val rest = top("debajo")
        shown = true
        frames(2)
        assertEquals(rest + 60f, top("debajo"), 0.5f)
    }

    @Test
    fun el_error_se_pliega_mostrando_todavia_su_ultimo_texto() {
        composeRule.mainClock.autoAdvance = false
        var text by mutableStateOf<String?>("Falta algo")
        composeRule.setContent {
            EndSecurityTheme { RevealValue(text) { Box(Modifier.size(100.dp, 40.dp).testTag("mensaje-$it").background(Color.Red)) } }
        }
        frames(3)
        composeRule.onNodeWithTag("mensaje-Falta algo").assertExists()
        text = null
        frames(3)
        // Mientras se pliega, el último valor sigue ahí (no desaparece de golpe).
        composeRule.onNodeWithTag("mensaje-Falta algo").assertExists()
        composeRule.mainClock.advanceTimeBy(300)
        composeRule.onNodeWithTag("mensaje-Falta algo").assertDoesNotExist()
    }

    // ---- Un valor que cambia en su lugar: fundido de 150 ms

    private var value by mutableStateOf("Revisado hoy a las 10:30")

    @Test
    fun el_texto_que_cambia_se_funde_en_150_ms_sin_moverse() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            EndSecurityTheme { FadeOnChange(value) { Box(Modifier.size(100.dp, 20.dp).testTag("v-$it")) } }
        }
        frames(3)
        val rest = left("v-Revisado hoy a las 10:30")
        value = "Revisado hoy a las 10:45"
        frames(3)
        composeRule.onNodeWithTag("v-Revisado hoy a las 10:30").assertExists()
        composeRule.onNodeWithTag("v-Revisado hoy a las 10:45").assertExists()
        assertEquals("solo fundido, sin desplazamiento", rest, left("v-Revisado hoy a las 10:45"), 0.5f)
        composeRule.mainClock.advanceTimeBy(Motion.ValueFade.toLong() + 100)
        composeRule.onNodeWithTag("v-Revisado hoy a las 10:30").assertDoesNotExist()
    }

    // ---- Filas escalonadas solo al llegar los datos: máximo 8; las que se componen después no se animan

    private var dataKey by mutableIntStateOf(0)
    private var rowCount by mutableIntStateOf(10)

    @Test
    fun las_filas_entran_escalonadas_con_maximo_8_y_las_de_despues_no_se_animan() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            EndSecurityTheme {
                val arriving = rememberArrivalWindow(dataKey)
                Column {
                    repeat(rowCount) { i ->
                        key(i) {
                            Box(Modifier.size(100.dp, 20.dp).staggeredEnter(i, arriving, maxStaggered = Motion.MaxStaggeredRows).testTag("fila$i").background(Color.Gray))
                        }
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeByFrame()
        frames(3) // ~48 ms
        val early = (0 until 10).map { top("fila$it") - it * 20f }
        assertTrue("la primera ya sube (${early[0]})", early[0] < 8f - 0.1f)
        assertTrue("la fila 5 empieza más tarde que la 0", early[5] > early[0] + 3f)
        // De la octava en adelante entran con la octava (índices 7, 8 y 9 se mueven igual).
        assertEquals("la 8.ª y la 9.ª entran con la 7.ª", early[7], early[8], 0.01f)
        assertEquals(early[7], early[9], 0.01f)
        composeRule.mainClock.advanceTimeBy(1_500)
        (0 until 10).forEach { assertEquals(it * 20f, top("fila$it"), 0.5f) }

        // Pasada la ventana de llegada, una fila que se compone después (al desplazar la lista) aparece en su lugar, sin animar.
        rowCount = 12
        frames(2)
        assertEquals("la fila nueva ya está en su lugar", 11 * 20f, top("fila11"), 0.5f)
    }

    // ---- Una lista con claves estables: al eliminar, la fila sale y las demás se reacomodan en 200 ms (sin saltar)

    private val ids = mutableStateOf((0 until 5).toList())

    @Test
    fun al_eliminar_una_fila_las_de_abajo_se_reacomodan_suave() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            EndSecurityTheme {
                LazyColumn(Modifier.height(400.dp)) {
                    itemsIndexed(ids.value, key = { _, id -> id }) { _, id ->
                        Box(
                            Modifier
                                .animateItem(fadeInSpec = tween(Motion.RowMove), placementSpec = tween(Motion.RowMove, easing = Motion.EaseOut), fadeOutSpec = tween(Motion.RowMove))
                                .size(100.dp, 40.dp)
                                .testTag("item$id"),
                        )
                    }
                }
            }
        }
        frames(3)
        val before = top("item3")
        ids.value = listOf(0, 1, 3, 4) // se elimina la 2
        frames(3)
        val middle = top("item3")
        assertTrue("la de abajo ya empezó a subir ($middle < $before)", middle < before - 0.5f)
        assertTrue("pero no salta de golpe ($middle > ${before - 40f})", middle > before - 39.5f)
        composeRule.mainClock.advanceTimeBy(400)
        assertEquals("termina 40 dp más arriba", before - 40f, top("item3"), 0.5f)
        composeRule.onNodeWithTag("item2").assertDoesNotExist()
    }

    // ---- Filas que salen (Dispositivos): se encogen y se desvanecen antes de quitarse

    private val deviceIds = mutableStateOf(listOf("a", "b", "c"))

    @Test
    fun una_fila_que_sale_se_encoge_en_200_ms_y_luego_se_quita() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            EndSecurityTheme {
                val rows = rememberLeavingList(deviceIds.value, key = { it })
                Column {
                    rows.forEach { entry ->
                        key(entry.item) {
                            Reveal(visible = entry.present) { Box(Modifier.size(100.dp, 50.dp).testTag("d-${entry.item}")) }
                        }
                    }
                }
            }
        }
        frames(3)
        val below = top("d-c")
        deviceIds.value = listOf("a", "c") // se cierra la sesión de «b»
        frames(4)
        composeRule.onNodeWithTag("d-b").assertExists() // todavía sale
        val middle = top("d-c")
        assertTrue("la de abajo sube suave ($middle < $below)", middle < below - 0.5f && middle > below - 49.5f)
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.onNodeWithTag("d-b").assertDoesNotExist()
        assertEquals(below - 50f, top("d-c"), 0.5f)
    }
}
