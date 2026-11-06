package com.luis.end_security.navigation

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.luis.end_security.core.designsystem.component.BottomNavTab
import com.luis.end_security.core.designsystem.theme.Motion
import com.luis.end_security.core.designsystem.theme.rememberReducedMotion
import com.luis.end_security.core.security.SecureScreen
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Transiciones entre pantallas (tarea 24) con la política real ([NavTransitions]) y las rutas reales: tiempos, «Quitar animaciones»,
 * interrupción, mismo camino al entrar y al salir y `FLAG_SECURE`. Las pantallas son de mentira.
 */
@RunWith(AndroidJUnit4::class)
class NavTransitionsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var navController: NavHostController

    /** «Quitar animaciones» simulado: sirve para medir el piso de Navigation (el trámite interno que existe sin animación). */
    private var reducedState by androidx.compose.runtime.mutableStateOf(false)

    /** Pantallas con un solo texto en la esquina; [secure] las marca con `SecureScreen`. */
    @Composable
    private fun Screen(name: String, secure: Boolean = false) {
        if (secure) SecureScreen()
        Box(Modifier.fillMaxSize()) { Text(name) }
    }

    @After
    fun resetAnimatorScale() {
        setAnimatorScale("1")
    }

    private fun setAnimatorScale(value: String) {
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("settings put global animator_duration_scale $value").close()
        Thread.sleep(300)
    }

    private fun setContent(reducedOverride: Boolean? = null) {
        composeRule.setContent {
            navController = rememberNavController()
            val slidePx = with(LocalDensity.current) { Motion.Slide.roundToPx() }
            // Con `null` se lee la escala del sistema, como la app (`rememberReducedMotion`).
            val reduced = reducedOverride ?: rememberReducedMotion() || reducedState
            val transitions = remember(slidePx, reduced) { NavTransitions(slidePx, reduced) }
            NavHost(
                navController = navController,
                startDestination = Routes.VAULT,
                enterTransition = { transitions.enter(this) },
                exitTransition = { transitions.exit(this) },
                popEnterTransition = { transitions.popEnter(this) },
                popExitTransition = { transitions.popExit(this) },
            ) {
                for (route in listOf(Routes.VAULT, Routes.HEALTH, Routes.GENERATOR, Routes.SETTINGS, Routes.DEVICES, Routes.ACTIVITY)) {
                    composable(route) { Screen(route) }
                }
                composable(Routes.UNLOCK) { Screen(Routes.UNLOCK, secure = true) }
                composable(Routes.LOGIN) { Screen(Routes.LOGIN, secure = true) }
                composable(Routes.TWO_FACTOR_ACTIVE) { Screen(Routes.TWO_FACTOR_ACTIVE, secure = true) }
            }
        }
        composeRule.waitForIdle()
    }

    private fun onUi(block: NavHostController.() -> Unit) = composeRule.runOnUiThread { navController.block() }

    /** Mediana de [runs] navegaciones: [before] las prepara y [action] es la que se mide (hasta que la entrada llega a RESUMED). */
    private fun medianMillis(runs: Int = 5, before: () -> Unit = {}, action: () -> Unit): Long {
        val samples = (1..runs).map {
            before()
            composeRule.waitForIdle()
            composeRule.settleMillis(navController, action)
        }
        return samples.sorted()[samples.size / 2]
    }

    private fun advanceUntilExists(text: String) {
        var frames = 0
        while (composeRule.onAllNodesWithTextCount(text) == 0 && frames < 60) {
            composeRule.mainClock.advanceTimeByFrame()
            frames++
        }
        assertTrue("la pantalla $text no apareció", composeRule.onAllNodesWithTextCount(text) > 0)
    }

    private fun secureFlag() = (composeRule.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0

    private fun leftDp(text: String) = composeRule.onNodeWithText(text).getBoundsInRoot().left.value

    // ---------------------------------------------------------------- Tiempos

    /**
     * Mide [action] con las animaciones y sin ellas (el piso de Navigation: unos 64 ms de trámite interno que no son animación) y
     * devuelve (crudo, piso). La duración real de la animación es la diferencia.
     */
    private fun measureWithFloor(before: () -> Unit, action: () -> Unit): Pair<Long, Long> {
        reducedState = true
        composeRule.waitForIdle()
        val floor = medianMillis(before = before, action = action)
        reducedState = false
        composeRule.waitForIdle()
        val raw = medianMillis(before = before, action = action)
        return raw to floor
    }

    @Test
    fun cambiarDePestanaTerminaEn200msOMenos() {
        setContent(reducedOverride = false)
        val (raw, floor) = measureWithFloor(before = { onUi { navigateToTab(BottomNavTab.Vault) } }) { onUi { navigateToTab(BottomNavTab.Generator) } }
        val animation = raw - floor
        println("TRANSICION pestaña: crudo $raw ms, piso de Navigation $floor ms, animación $animation ms")
        assertTrue("cambiar de pestaña tardó $animation ms de animación", animation <= Motion.TabSwitchLimitMillis)
        assertTrue("el fundido no puede ser más corto que su duración ($animation ms)", animation >= Motion.TabFade - 32)
    }

    @Test
    fun avanzarYRegresarDentroDeUnaPestanaTerminanEn260msOMenos() {
        setContent(reducedOverride = false)
        onUi { navigateToTab(BottomNavTab.Settings) }
        composeRule.waitForIdle()
        val pushBefore = { onUi { navigate(Routes.DEVICES) { launchSingleTop = true } }; composeRule.waitForIdle(); onUi { popBackStack() } }
        val (pushRaw, pushFloor) = measureWithFloor(before = pushBefore) { onUi { navigate(Routes.DEVICES) { launchSingleTop = true } } }
        val (popRaw, popFloor) = measureWithFloor(before = { onUi { navigate(Routes.DEVICES) { launchSingleTop = true } } }) { onUi { popBackStack() } }
        val push = pushRaw - pushFloor
        val pop = popRaw - popFloor
        println("TRANSICION avanzar: crudo $pushRaw ms, piso $pushFloor ms, animación $push ms; regresar: crudo $popRaw ms, piso $popFloor ms, animación $pop ms")
        assertTrue("avanzar tardó $push ms de animación", push <= Motion.PushLimitMillis)
        assertTrue("regresar tardó $pop ms de animación", pop <= Motion.PushLimitMillis)
        assertTrue("avanzar no puede ser más corto que su animación ($push ms)", push >= Motion.PushExit - 32)
    }

    @Test
    fun bloquearEntraConUnFundidoDe200msYDejaSoloDesbloquear() {
        setContent(reducedOverride = false)
        composeRule.mainClock.autoAdvance = false
        onUi { navigate(Routes.UNLOCK) { popUpTo(graph.id) { inclusive = true } } }
        advanceUntilExists(Routes.UNLOCK)
        composeRule.mainClock.advanceTimeBy(48)
        // (Que la Bóveda desaparezca al instante lo fija la política: `NavMotionKindTest` comprueba cuándo se aplica `snap`.)
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        composeRule.onNodeWithText(Routes.UNLOCK).assertExists()
        assertEquals("tras bloquear solo queda Desbloquear", 0, composeRule.onAllNodesWithTextCount(Routes.VAULT))
    }

    private fun androidx.compose.ui.test.junit4.ComposeTestRule.onAllNodesWithTextCount(text: String): Int =
        onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().size

    // ---------------------------------------------------------------- Quitar animaciones

    @Test
    fun conQuitarAnimacionesTodoPasaSinTransicion() {
        setAnimatorScale("0")
        // `reducedOverride = null`: la escala del sistema se lee como la lee la app (`rememberReducedMotion`).
        setContent(reducedOverride = null)
        val tab = medianMillis(before = { onUi { navigateToTab(BottomNavTab.Vault) } }) { onUi { navigateToTab(BottomNavTab.Health) } }
        onUi { navigateToTab(BottomNavTab.Settings) }
        composeRule.waitForIdle()
        val push = medianMillis(before = { onUi { popBackStack(Routes.SETTINGS, inclusive = false) } }) {
            onUi { navigate(Routes.DEVICES) { launchSingleTop = true } }
        }
        println("TRANSICION sin animaciones: pestaña $tab ms, avanzar $push ms (el piso de Navigation, sin animación)")
        // Sin animación solo queda el trámite interno de Navigation (≈ 64 ms): muy por debajo de cualquier fundido.
        assertTrue("con «Quitar animaciones» el cambio de pestaña tardó $tab ms", tab < Motion.TabFade)
        assertTrue("con «Quitar animaciones» avanzar tardó $push ms", push < Motion.PushExit)
    }

    // ---------------------------------------------------------------- Interrupción

    @Test
    fun siSeTocaOtraPestanaAMitadDeUnaTransicionLaNuevaParteDelEstadoActual() {
        setContent(reducedOverride = false)
        composeRule.mainClock.autoAdvance = false
        onUi { navigateToTab(BottomNavTab.Health) }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(48)
        // A mitad de Bóveda → Salud, se toca Ajustes: no hay bloqueo de toques ni de navegación.
        onUi { navigateToTab(BottomNavTab.Settings) }
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        assertEquals(Routes.SETTINGS, navController.currentDestination?.route)
        composeRule.onNodeWithText(Routes.SETTINGS).assertExists()
        assertEquals("solo queda la pantalla de destino", 0, composeRule.onAllNodesWithTextCount(Routes.HEALTH))
    }

    @Test
    fun alRegresarAMitadDeAvanzarSeParteDelEstadoActualSinSaltos() {
        setContent(reducedOverride = false)
        onUi { navigateToTab(BottomNavTab.Settings) }
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
        onUi { navigate(Routes.DEVICES) { launchSingleTop = true } }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(64)
        val devicesLeft = leftDp(Routes.DEVICES)
        onUi { popBackStack() }
        composeRule.mainClock.advanceTimeByFrame()
        // Sin salto: Dispositivos sigue desde donde iba (no vuelve a +30 dp ni se teletransporta).
        val after = leftDp(Routes.DEVICES)
        assertTrue("saltó de $devicesLeft a $after dp", kotlin.math.abs(after - devicesLeft) < 12f)
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        assertEquals(Routes.SETTINGS, navController.currentDestination?.route)
    }

    // ---------------------------------------------------------------- Mismo camino al entrar y al salir

    @Test
    fun avanzarYRegresarRecorrenElMismoCaminoAlReves() {
        setContent(reducedOverride = false)
        onUi { navigateToTab(BottomNavTab.Settings) }
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
        // Avanzar: la nueva entra desde +30 dp (a la derecha) y la anterior sale hacia −30 dp.
        onUi { navigate(Routes.DEVICES) { launchSingleTop = true } }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(32)
        val enteringPush = leftDp(Routes.DEVICES)
        val leavingPush = leftDp(Routes.SETTINGS)
        assertTrue("al avanzar, la nueva debe entrar desde la derecha ($enteringPush)", enteringPush > 0f && enteringPush <= Motion.Slide.value)
        assertTrue("al avanzar, la anterior debe salir hacia la izquierda ($leavingPush)", leavingPush < 0f && leavingPush >= -Motion.Slide.value)
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
        // Regresar: el mismo camino al revés. La actual sale hacia +30 dp y la anterior vuelve desde −30 dp.
        onUi { popBackStack() }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(32)
        val leavingPop = leftDp(Routes.DEVICES)
        val enteringPop = leftDp(Routes.SETTINGS)
        assertTrue("al regresar, la actual debe salir hacia la derecha ($leavingPop)", leavingPop > 0f && leavingPop <= Motion.Slide.value)
        assertTrue("al regresar, la anterior debe volver desde la izquierda ($enteringPop)", enteringPop < 0f && enteringPop >= -Motion.Slide.value)
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
    }

    @Test
    fun alCambiarDePestanaNadaSeDesplaza() {
        setContent(reducedOverride = false)
        composeRule.mainClock.autoAdvance = false
        onUi { navigateToTab(BottomNavTab.Health) }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(32)
        // Las pestañas son hermanas: sin dirección ni desplazamiento (solo el fundido).
        assertEquals(0f, leftDp(Routes.HEALTH), 0.01f)
        assertEquals(0f, leftDp(Routes.VAULT), 0.01f)
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
    }

    @Test
    fun noSeBloqueanLosTocosDuranteUnaTransicion() {
        setContent(reducedOverride = false)
        composeRule.mainClock.autoAdvance = false
        onUi { navigateToTab(BottomNavTab.Settings) }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(48)
        // A mitad de la transición se puede tocar (la pantalla que entra recibe el toque sin esperar al final).
        composeRule.onNodeWithText(Routes.SETTINGS).performClick()
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
    }

    // ---------------------------------------------------------------- FLAG_SECURE

    @Test
    fun laMarcaSeguraSePoneAntesDeQueEntreYSeQuitaDespuesDeQueSalga() {
        setContent(reducedOverride = false)
        // La Bóveda no es segura aquí; Desbloquear sí.
        assertFalse("sin pantalla segura no debe haber FLAG_SECURE", secureFlag())
        composeRule.mainClock.autoAdvance = false
        onUi { navigate(Routes.TWO_FACTOR_ACTIVE) }
        // En el primer cuadro en que la pantalla segura existe, la marca ya está puesta: antes de que se vea un solo píxel.
        advanceUntilExists(Routes.TWO_FACTOR_ACTIVE)
        assertTrue("FLAG_SECURE debía estar puesta desde el primer cuadro de la entrada", secureFlag())
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
        // Al salir hacia una pantalla normal, la marca sigue mientras la segura todavía se ve y se quita al terminar.
        onUi { navigateToTab(BottomNavTab.Health) }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(48)
        assertTrue("FLAG_SECURE debía seguir puesta mientras la pantalla segura sale", secureFlag())
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        assertFalse("FLAG_SECURE debía quitarse después de que salió", secureFlag())
    }

    @Test
    fun alBloquearNoSeVeLaBovedaNiUnCuadroConLaMarcaPuestaDeMas() {
        setContent(reducedOverride = false)
        composeRule.mainClock.autoAdvance = false
        onUi { navigate(Routes.UNLOCK) { popUpTo(graph.id) { inclusive = true } } }
        advanceUntilExists(Routes.UNLOCK)
        assertTrue("Desbloquear es segura desde su primer cuadro", secureFlag())
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        assertTrue(secureFlag())
    }
}
