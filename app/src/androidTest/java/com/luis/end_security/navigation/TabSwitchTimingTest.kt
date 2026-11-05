package com.luis.end_security.navigation

import android.util.Log
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.luis.end_security.core.designsystem.component.BottomNavTab
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Medición del retraso al volver a la Bóveda desde otra pestaña (tarea 23, paso 5.2): cuánto tarda la pantalla en quedar estable
 * tras el cambio de pestaña, con las transiciones por defecto del `NavHost` (antes) y con [TabEnterNone] / [TabExitNone] (ahora).
 * Es tiempo del reloj de Compose (el de las animaciones), no del reloj de pared: lo que dura la transición.
 */
@RunWith(AndroidJUnit4::class)
class TabSwitchTimingTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var navController: NavHostController

    @Composable
    private fun Screen(name: String) = Text(name)

    private fun setContent(withoutTransitions: Boolean) {
        composeRule.setContent {
            navController = rememberNavController()
            NavHost(navController = navController, startDestination = Routes.VAULT) {
                for (route in listOf(Routes.VAULT, Routes.HEALTH, Routes.GENERATOR, Routes.SETTINGS)) {
                    if (withoutTransitions) {
                        composable(
                            route,
                            enterTransition = TabEnterNone,
                            exitTransition = TabExitNone,
                            popEnterTransition = TabEnterNone,
                            popExitTransition = TabExitNone,
                        ) { Screen(route) }
                    } else {
                        // Como estaban: solo la salida sin animar; la entrada, el fundido de 700 ms por defecto.
                        composable(route, exitTransition = TabExitNone, popExitTransition = TabExitNone) { Screen(route) }
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    /** Lo que tarda en quedar estable el regreso Bóveda → Generador → Bóveda: la mediana de [runs] vueltas, en ms. */
    private fun medianReturnToVaultMillis(runs: Int = 5): Long {
        val samples = (1..runs).map {
            composeRule.runOnUiThread { navController.navigateToTab(BottomNavTab.Generator) }
            composeRule.waitForIdle()
            val start = composeRule.mainClock.currentTime
            composeRule.runOnUiThread { navController.navigateToTab(BottomNavTab.Vault) }
            composeRule.waitForIdle()
            composeRule.mainClock.currentTime - start
        }
        return samples.sorted()[samples.size / 2]
    }

    @Test
    fun midelElRegresoALaBovedaConYSinTransicion() {
        setContent(withoutTransitions = false)
        val before = medianReturnToVaultMillis()
        Log.i("TabSwitchTiming", "antes (transición por defecto): $before ms")
    }

    @Test
    fun conLasPestanasSinTransicionElRegresoNoEspera() {
        setContent(withoutTransitions = true)
        val after = medianReturnToVaultMillis()
        Log.i("TabSwitchTiming", "ahora (sin transición): $after ms")
        assertTrue("el regreso tarda $after ms", after < 100)
    }
}
