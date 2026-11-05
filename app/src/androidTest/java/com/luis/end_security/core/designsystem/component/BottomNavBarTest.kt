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
}
