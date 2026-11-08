package com.luis.end_security.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.luis.end_security.core.designsystem.component.BottomNavTab
import com.luis.end_security.core.designsystem.component.InterceptTabSelection
import com.luis.end_security.core.designsystem.component.LocalTabGuard
import com.luis.end_security.core.designsystem.component.PillTag
import com.luis.end_security.core.designsystem.component.TabGuard
import com.luis.end_security.core.designsystem.theme.EndSecurityTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** La barra persistente (tarea 26): aparece y se oculta donde corresponde, no se vuelve a crear y la pantalla puede interceptar un toque. */
@RunWith(AndroidJUnit4::class)
class PersistentTabBarTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var tab by mutableStateOf<BottomNavTab?>(null)
    private var tapped: BottomNavTab? = null
    private var blocked by mutableStateOf(false)

    private fun frames(count: Int) = repeat(count) { composeRule.mainClock.advanceTimeByFrame() }

    private fun setBar() {
        composeRule.mainClock.autoAdvance = false
        val guard = TabGuard()
        composeRule.setContent {
            EndSecurityTheme {
                CompositionLocalProvider(LocalTabGuard provides guard) {
                    Box(Modifier.fillMaxSize()) {
                        InterceptTabSelection { blocked }
                        PersistentTabBar(
                            tab = tab,
                            onTabSelected = { if (!guard.intercepts(it)) tapped = it },
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }
                }
            }
        }
        frames(2)
    }

    @Test
    fun sin_pestana_no_hay_barra_y_con_pestana_aparece() {
        tab = null
        setBar()
        composeRule.onNodeWithTag(BottomNavTab.Vault.name).assertDoesNotExist()
        tab = BottomNavTab.Health
        frames(2)
        composeRule.mainClock.advanceTimeBy(400)
        composeRule.onNodeWithTag(BottomNavTab.Health.name).assertIsDisplayed()
        composeRule.onNodeWithTag(BottomNavTab.Health.name).assertIsSelected()
    }

    @Test
    fun aparece_deslizando_desde_abajo_en_unos_200_ms() {
        tab = null
        setBar()
        tab = BottomNavTab.Vault
        frames(2)
        val entering = composeRule.onNodeWithTag(BottomNavTab.Vault.name).getBoundsInRoot().top.value
        composeRule.mainClock.advanceTimeBy(300)
        val rest = composeRule.onNodeWithTag(BottomNavTab.Vault.name).getBoundsInRoot().top.value
        assertTrue("entra desde abajo ($entering > $rest)", entering > rest + 1f)
    }

    @Test
    fun se_oculta_deslizando_hacia_abajo_y_desaparece() {
        tab = BottomNavTab.Vault
        setBar()
        composeRule.mainClock.advanceTimeBy(400)
        val rest = composeRule.onNodeWithTag(BottomNavTab.Vault.name).getBoundsInRoot().top.value
        tab = null
        frames(4)
        val leaving = composeRule.onNodeWithTag(BottomNavTab.Vault.name).getBoundsInRoot().top.value
        assertTrue("sale hacia abajo ($leaving > $rest)", leaving > rest + 1f)
        composeRule.mainClock.advanceTimeBy(400)
        composeRule.onNodeWithTag(BottomNavTab.Vault.name).assertDoesNotExist()
    }

    @Test
    fun al_cambiar_de_pestana_la_barra_sigue_ahi_y_solo_se_desliza_la_pildora() {
        tab = BottomNavTab.Vault
        setBar()
        composeRule.mainClock.advanceTimeBy(400)
        val pillStart = composeRule.onNodeWithTag(PillTag, useUnmergedTree = true).getBoundsInRoot().left.value
        val barTop = composeRule.onNodeWithTag(BottomNavTab.Vault.name).getBoundsInRoot().top.value
        tab = BottomNavTab.Settings
        repeat(10) {
            composeRule.mainClock.advanceTimeByFrame()
            // La barra no sale ni se mueve: sigue en el mismo sitio mientras la píldora viaja.
            composeRule.onNodeWithTag(BottomNavTab.Vault.name).assertIsDisplayed()
            assertEquals(barTop, composeRule.onNodeWithTag(BottomNavTab.Vault.name).getBoundsInRoot().top.value, 0.5f)
        }
        val pillMiddle = composeRule.onNodeWithTag(PillTag, useUnmergedTree = true).getBoundsInRoot().left.value
        assertTrue("la píldora ya se movió ($pillStart -> $pillMiddle)", pillMiddle > pillStart + 1f)
    }

    @Test
    fun la_pantalla_puede_interceptar_el_toque_y_si_no_lo_hace_cambia_de_pestana() {
        tab = BottomNavTab.Vault
        setBar()
        composeRule.mainClock.advanceTimeBy(400)
        blocked = true
        composeRule.onNodeWithTag(BottomNavTab.Settings.name).performClick()
        assertEquals("ocupada: el toque no navega", null, tapped)
        blocked = false
        composeRule.onNodeWithTag(BottomNavTab.Settings.name).performClick()
        assertEquals(BottomNavTab.Settings, tapped)
    }
}
