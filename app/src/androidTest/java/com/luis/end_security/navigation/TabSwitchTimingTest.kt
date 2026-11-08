package com.luis.end_security.navigation

import android.util.Log
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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

    private var reduced by androidx.compose.runtime.mutableStateOf(false)

    @Composable
    private fun Screen(name: String) = Text(name)

    private fun setContent(withoutTransitions: Boolean) {
        composeRule.setContent {
            navController = rememberNavController()
            val slidePx = androidx.compose.ui.platform.LocalDensity.current.run { com.luis.end_security.core.designsystem.theme.Motion.Slide.roundToPx() }
            val transitions = androidx.compose.runtime.remember(slidePx, reduced) { NavTransitions(slidePx, reduced = reduced) }
            NavHost(
                navController = navController,
                startDestination = Routes.VAULT,
                // Sin política (como antes de la tarea 23: los valores por defecto del NavHost) o con la política real.
                enterTransition = if (withoutTransitions) ({ transitions.enter(this) }) else ({ androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(700)) }),
                exitTransition = if (withoutTransitions) ({ transitions.exit(this) }) else ({ androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(700)) }),
                popEnterTransition = if (withoutTransitions) ({ transitions.popEnter(this) }) else ({ androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(700)) }),
                popExitTransition = if (withoutTransitions) ({ transitions.popExit(this) }) else ({ androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(700)) }),
            ) {
                for (route in listOf(Routes.VAULT, Routes.HEALTH, Routes.GENERATOR, Routes.SETTINGS)) {
                    if (withoutTransitions) {
                        // Como están ahora (tarea 24): la política real, fundido cruzado de 150 ms entre pestañas.
                        composable(route) { Screen(route) }
                    } else {
                        // Como estaban en la tarea 22: el fundido de entrada de 700 ms por defecto del `NavHost`.
                        composable(route, exitTransition = { androidx.compose.animation.ExitTransition.None }) { Screen(route) }
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    /** Lo que tarda el regreso Bóveda → Generador → Bóveda (hasta que la entrada llega a RESUMED): la mediana de [runs] vueltas, en ms. */
    private fun medianReturnToVaultMillis(runs: Int = 5): Long {
        val samples = (1..runs).map {
            composeRule.runOnUiThread { navController.navigateToTab(BottomNavTab.Generator) }
            composeRule.waitForIdle()
            composeRule.settleMillis(navController, { composeRule.runOnUiThread { navController.navigateToTab(BottomNavTab.Vault) } })
        }
        return samples.sorted()[samples.size / 2]
    }

    @Test
    fun midelElRegresoALaBovedaConYSinTransicion() {
        setContent(withoutTransitions = false)
        val before = medianReturnToVaultMillis()
        Log.i("TabSwitchTiming", "antes (fundido por defecto de 700 ms): $before ms")
        assertTrue("el fundido por defecto tardaba $before ms", before >= 600)
    }

    @Test
    fun conLaPoliticaDeTransicionesElRegresoTerminaEn200msOMenos() {
        // El piso de Navigation (≈ 64 ms de trámite interno sin animación) se mide con el mismo grafo y las animaciones quitadas.
        setContent(withoutTransitions = true)
        val raw = medianReturnToVaultMillis()
        val floor = measureFloor()
        val animation = raw - floor
        Log.i("TabSwitchTiming", "ahora (fundido de 150 ms): crudo $raw ms, piso $floor ms, animación $animation ms")
        assertTrue("el regreso tarda $animation ms de animación", animation <= com.luis.end_security.core.designsystem.theme.Motion.TabSwitchLimitMillis)
        // Y no es más corto que su fundido: se está midiendo la animación y no el reposo.
        assertTrue("el regreso tarda solo $animation ms", animation >= com.luis.end_security.core.designsystem.theme.Motion.TabFade - 32)
    }

    /** Lo mismo con las transiciones quitadas (la política con `reduced = true`). */
    private fun measureFloor(): Long {
        reduced = true
        composeRule.waitForIdle()
        val floor = medianReturnToVaultMillis()
        reduced = false
        composeRule.waitForIdle()
        return floor
    }
}
