package com.luis.end_security.feature.health

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.luis.end_security.core.designsystem.theme.EndSecurityTheme
import com.luis.end_security.feature.health.ui.SegmentedLabelTag
import com.luis.end_security.feature.health.ui.SegmentedSelector
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * El selector de Salud (tarea 28): cada opción mide lo mismo, su texto queda en **una sola línea** y **centrado** en su celda, también
 * con la letra al 130 % (si no cabe se achica hasta 12 sp en vez de partirse). Sin capturas: se afirman límites y líneas del texto.
 */
@RunWith(AndroidJUnit4::class)
class SegmentedSelectorLayoutTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun check(options: List<String>, fontScale: Float) {
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
                EndSecurityTheme { Box(Modifier.width(360.dp)) { SegmentedSelector(options, selectedIndex = 0, onSelect = {}) } }
            }
        }
        composeRule.waitForIdle()
        val nodes = composeRule.onAllNodes(hasTestTag(SegmentedLabelTag), useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals(options.size, nodes.size)
        // El selector mide 360 dp con 4 dp de relleno: cada celda mide (360 − 8) / n y empieza en 4 + i × celda.
        val density = nodes.first().layoutInfo.density.density
        val cell = (360f - 8f) / options.size * density
        nodes.forEachIndexed { index, node ->
            val results = mutableListOf<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
            val layout = results.single()
            assertEquals("«${options[index]}» queda en una sola línea (letra x$fontScale)", 1, layout.lineCount)
            // El texto está centrado dentro de su celda: el centro de la línea coincide con el centro de la celda (±1 dp).
            val cellCenter = 4f * density + cell * index + cell / 2f
            val lineCenter = node.boundsInRoot.left + (layout.getLineLeft(0) + layout.getLineRight(0)) / 2f
            assertEquals("«${options[index]}» centrado en su celda (letra x$fontScale)", cellCenter, lineCenter, density)
            // Todas las opciones miden lo mismo.
            assertEquals(cell - 12f * density, node.boundsInRoot.width, density) // celda menos 6 dp de relleno a cada lado
        }
    }

    @Test fun resumen_progreso_con_letra_normal() = check(listOf("Resumen", "Progreso"), 1.0f)

    @Test fun resumen_progreso_con_letra_al_130() = check(listOf("Resumen", "Progreso"), 1.3f)

    @Test fun mas_debiles_mejoradas_con_letra_normal() = check(listOf("Más débiles", "Mejoradas"), 1.0f)

    @Test fun mas_debiles_mejoradas_con_letra_al_130() = check(listOf("Más débiles", "Mejoradas"), 1.3f)

    @Test fun una_etiqueta_larga_no_se_parte_ni_se_va_a_la_izquierda() = check(listOf("Más débiles", "Mejoradas recientemente"), 1.3f)
}
