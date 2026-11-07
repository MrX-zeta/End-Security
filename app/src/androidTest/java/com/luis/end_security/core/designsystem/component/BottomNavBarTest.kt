package com.luis.end_security.core.designsystem.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.SemanticsNodeInteraction
import kotlin.math.abs
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.luis.end_security.core.designsystem.theme.EndSecurityTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BottomNavBarTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun la_pestana_activa_se_reporta_como_seleccionada() {
        var selected by mutableStateOf(BottomNavTab.Vault)
        composeRule.setContent {
            EndSecurityTheme { BottomNavBar(selected = selected, onTabSelected = {}) }
        }
        BottomNavTab.entries.forEach { active ->
            selected = active
            composeRule.waitForIdle()
            BottomNavTab.entries.forEach { tab ->
                val node = composeRule.onNodeWithTag(tab.name)
                    .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
                if (tab == active) node.assertIsSelected() else node.assertIsNotSelected()
            }
        }
    }

    @Test
    fun las_pestanas_miden_mas_de_48dp_al_tacto() {
        composeRule.setContent {
            EndSecurityTheme { BottomNavBar(selected = BottomNavTab.Vault, onTabSelected = {}) }
        }
        BottomNavTab.entries.forEach { tab ->
            composeRule.onNodeWithTag(tab.name).assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        }
    }

    @Test
    fun tocar_una_pestana_no_dibuja_onda() {
        composeRule.setContent {
            EndSecurityTheme { BottomNavBar(selected = BottomNavTab.Vault, onTabSelected = {}) }
        }
        val tab = composeRule.onNodeWithTag(BottomNavTab.Generator.name)
        fun pixels(): IntArray {
            val image = tab.captureToImage()
            return IntArray(image.width * image.height).also { image.readPixels(it) }
        }
        val before = pixels()
        // Con el dedo apoyado la pestaña se ve igual: sin onda ni resaltado (la píldora de la activa ya marca la selección).
        composeRule.runOnIdle { }
        tab.performTouchInput { down(center) }
        composeRule.mainClock.advanceTimeBy(300)
        composeRule.waitForIdle()
        val pressed = pixels()
        tab.performTouchInput { up() }
        assertTrue("la pestaña cambió al tocarla (onda o resaltado)", before.contentEquals(pressed))
    }

    @Test
    fun tocar_una_pestana_avisa_cual() {
        var tapped: BottomNavTab? = null
        composeRule.setContent {
            EndSecurityTheme { BottomNavBar(selected = BottomNavTab.Vault, onTabSelected = { tapped = it }) }
        }
        composeRule.onNodeWithTag(BottomNavTab.Generator.name).performClick()
        assertEquals(BottomNavTab.Generator, tapped)
    }

    // ------------------------------------------------------------------ Movimiento de la barra (tarea 25)

    private var selectedTab by mutableStateOf(BottomNavTab.Vault)

    private fun setBar(start: BottomNavTab = BottomNavTab.Vault, reduced: Boolean = false) {
        composeRule.mainClock.autoAdvance = false
        selectedTab = start
        composeRule.setContent {
            EndSecurityTheme { BottomNavBar(selected = selectedTab, onTabSelected = {}, reducedMotion = reduced) }
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeByFrame()
    }

    private fun centerX(node: SemanticsNodeInteraction): Float {
        val bounds = node.getBoundsInRoot()
        return (bounds.left.value + bounds.right.value) / 2f
    }

    private fun pillX() = centerX(composeRule.onNodeWithTag(PillTag, useUnmergedTree = true))
    private fun tabX(tab: BottomNavTab) = centerX(composeRule.onNodeWithTag(tab.name))
    private fun frames(count: Int) = repeat(count) { composeRule.mainClock.advanceTimeByFrame() }

    @Test
    fun la_pildora_ya_esta_en_su_lugar_en_el_primer_dibujo() {
        composeRule.mainClock.autoAdvance = false
        var tab by mutableStateOf(BottomNavTab.Vault)
        composeRule.setContent {
            // `key` crea una barra nueva por pestaña, como al llegar a una pantalla: sin relevo, la píldora nace en su lugar.
            EndSecurityTheme { androidx.compose.runtime.key(tab) { BottomNavBar(selected = tab, onTabSelected = {}, reducedMotion = false) } }
        }
        composeRule.mainClock.advanceTimeByFrame()
        BottomNavTab.entries.forEach { start ->
            tab = start
            composeRule.mainClock.advanceTimeByFrame()
            assertEquals("la píldora debe aparecer sobre ${start.name} en el primer cuadro", tabX(start), pillX(), 1f)
        }
    }

    @Test
    fun la_pildora_se_desliza_y_termina_centrada_en_la_pestana_nueva() {
        setBar()
        val from = tabX(BottomNavTab.Vault)
        val to = tabX(BottomNavTab.Settings)
        selectedTab = BottomNavTab.Settings
        frames(6) // unos 100 ms
        val middle = pillX()
        assertTrue("a la mitad debe estar entre las dos ($from < $middle < $to)", middle > from + 1f && middle < to - 1f)
        composeRule.mainClock.advanceTimeBy(800)
        assertEquals("debe terminar sobre Ajustes", to, pillX(), 1f)
    }

    @Test
    fun al_tocar_otra_a_la_mitad_la_pildora_sigue_sin_saltar() {
        setBar()
        val span = tabX(BottomNavTab.Settings) - tabX(BottomNavTab.Vault)
        selectedTab = BottomNavTab.Settings
        frames(5)
        var previous = pillX()
        selectedTab = BottomNavTab.Health // cambia de rumbo a mitad del camino
        repeat(40) {
            composeRule.mainClock.advanceTimeByFrame()
            val now = pillX()
            assertTrue("la píldora saltó ${abs(now - previous)} dp en un cuadro (tramo $span dp)", abs(now - previous) < span * 0.3f)
            previous = now
        }
        composeRule.mainClock.advanceTimeBy(800)
        assertEquals(tabX(BottomNavTab.Health), pillX(), 1f)
    }

    @Test
    fun con_quitar_animaciones_la_pildora_salta_a_su_lugar() {
        setBar(reduced = true)
        selectedTab = BottomNavTab.Generator
        frames(2)
        assertEquals("sin animación la píldora debe estar ya en Generador", tabX(BottomNavTab.Generator), pillX(), 1f)
    }

    private fun iconWidth(tab: BottomNavTab) =
        composeRule.onNodeWithTag(tab.name + "Icon", useUnmergedTree = true).getBoundsInRoot().let { it.right.value - it.left.value }

    @Test
    fun el_icono_pulsa_al_ser_seleccionado_y_no_en_el_primer_dibujo() {
        setBar()
        val normal = iconWidth(BottomNavTab.Vault)
        assertEquals("sin pulso en el primer dibujo", normal, iconWidth(BottomNavTab.Health), 0.1f)
        selectedTab = BottomNavTab.Health
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeByFrame()
        val smallest = (0 until 8).minOf { composeRule.mainClock.advanceTimeByFrame(); iconWidth(BottomNavTab.Health) }
        assertTrue("el ícono debía encogerse a ~92 % ($smallest de $normal)", smallest < normal * 0.97f)
        composeRule.mainClock.advanceTimeBy(600)
        assertEquals("el pulso termina en tamaño normal", normal, iconWidth(BottomNavTab.Health), 0.1f)
        // Volver a tocar la que ya está seleccionada no pulsa.
        selectedTab = BottomNavTab.Health
        frames(3)
        assertEquals(normal, iconWidth(BottomNavTab.Health), 0.1f)
    }

    @Test
    fun con_quitar_animaciones_no_hay_pulso() {
        setBar(reduced = true)
        val normal = iconWidth(BottomNavTab.Vault)
        selectedTab = BottomNavTab.Health
        repeat(8) {
            composeRule.mainClock.advanceTimeByFrame()
            assertEquals("no debe haber pulso", normal, iconWidth(BottomNavTab.Health), 0.1f)
        }
    }

    @Test
    fun la_barra_no_se_vuelve_a_crear_al_cambiar_de_pestana_y_no_se_desvanece() {
        // La barra es única (tarea 26): al cambiar de pestaña sigue siendo la misma y solo se desliza la píldora.
        var disposed = 0
        composeRule.mainClock.autoAdvance = false
        selectedTab = BottomNavTab.Vault
        composeRule.setContent {
            EndSecurityTheme {
                androidx.compose.runtime.DisposableEffect(Unit) { onDispose { disposed++ } }
                BottomNavBar(selected = selectedTab, onTabSelected = {}, reducedMotion = false)
            }
        }
        frames(2)
        val vaultNode = composeRule.onNodeWithTag(BottomNavTab.Vault.name).fetchSemanticsNode().id
        selectedTab = BottomNavTab.Settings
        repeat(20) {
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.onNodeWithTag(BottomNavTab.Vault.name).assertExists()
            assertEquals("la pestaña sigue siendo el mismo nodo", vaultNode, composeRule.onNodeWithTag(BottomNavTab.Vault.name).fetchSemanticsNode().id)
        }
        assertEquals("la barra no se destruyó al cambiar de pestaña", 0, disposed)
    }
}
