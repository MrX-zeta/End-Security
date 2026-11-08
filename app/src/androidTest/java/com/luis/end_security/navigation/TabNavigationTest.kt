package com.luis.end_security.navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.luis.end_security.core.designsystem.component.BottomNavTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Pila de las pestañas (tarea 23, paso 5.3): cambiar de pestaña no suma historial, Atrás desde Salud, Generador o Ajustes vuelve a
 * la Bóveda, Atrás desde la Bóveda sale de la app, dentro de una pestaña Atrás recorre su propia pila, las pestañas conservan su
 * estado y lo que tiene secretos se vacía al salir. Usa las rutas reales con pantallas de mentira.
 */
@RunWith(AndroidJUnit4::class)
class TabNavigationTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** Un ViewModel por pantalla: si dos visitas devuelven la misma instancia, el estado se conservó. */
    class ScreenViewModel : ViewModel()

    private lateinit var navController: NavHostController
    private val viewModels = mutableMapOf<String, ScreenViewModel>()

    @Composable
    private fun Screen(name: String) {
        viewModels[name] = viewModel<ScreenViewModel>()
        Text(name)
    }

    @Before
    fun setUp() {
        composeRule.setContent {
            navController = rememberNavController()
            NavHost(navController = navController, startDestination = Routes.VAULT) {
                composable(Routes.VAULT) { Screen(Routes.VAULT) }
                composable(Routes.HEALTH) { Screen(Routes.HEALTH) }
                composable(Routes.GENERATOR) { Screen(Routes.GENERATOR) }
                composable(Routes.SETTINGS) { Screen(Routes.SETTINGS) }
                composable(Routes.DEVICES) { Screen(Routes.DEVICES) }
                composable(Routes.TWO_FACTOR_SETUP) { Screen(Routes.TWO_FACTOR_SETUP) }
                composable(Routes.TWO_FACTOR_ACTIVE) { Screen(Routes.TWO_FACTOR_ACTIVE) }
                composable(Routes.HEALTH_BREACHED) { Screen(Routes.HEALTH_BREACHED) }
                composable(Routes.CONFLICTS) { Screen(Routes.CONFLICTS) }
                composable(
                    Routes.VAULT_EDIT,
                    arguments = listOf(
                        navArgument(Routes.ITEM_ID_ARG) { type = NavType.StringType; nullable = true; defaultValue = null },
                        navArgument(Routes.GENERATED_ARG) { type = NavType.BoolType; defaultValue = false },
                    ),
                ) { Screen(Routes.VAULT_EDIT) }
                composable(
                    Routes.VAULT_ITEM,
                    arguments = listOf(navArgument(Routes.ITEM_ID_ARG) { type = NavType.StringType }),
                ) { Screen(Routes.VAULT_ITEM) }
            }
        }
        composeRule.waitForIdle()
    }

    private fun onUi(block: NavHostController.() -> Unit) {
        composeRule.runOnUiThread { navController.block() }
        composeRule.waitForIdle()
    }

    private fun tab(tab: BottomNavTab) = onUi { navigateToTab(tab) }

    private fun open(route: String) = onUi { navigate(route) { launchSingleTop = true } }

    private fun back() = onUi { popBackStack() }

    /** La pila de abajo hacia arriba, sin el grafo. */
    private fun stack(): List<String> = navController.currentBackStack.value.mapNotNull { it.destination.route }

    @Test
    fun cambiarDePestanaNoSumaHistorialYAtrasVuelveALaBoveda() {
        tab(BottomNavTab.Generator)
        tab(BottomNavTab.Health)
        tab(BottomNavTab.Settings)
        tab(BottomNavTab.Generator)
        // Solo la Bóveda y la pestaña actual, sin importar cuántas se visitaron.
        assertEquals(listOf(Routes.VAULT, Routes.GENERATOR), stack())
        back()
        assertEquals(listOf(Routes.VAULT), stack())
    }

    @Test
    fun atrasDesdeSaludGeneradorOAjustesVuelveALaBoveda() {
        for (target in listOf(BottomNavTab.Health, BottomNavTab.Generator, BottomNavTab.Settings)) {
            tab(target)
            assertEquals(listOf(Routes.VAULT, target.route()), stack())
            back()
            assertEquals(listOf(Routes.VAULT), stack())
        }
    }

    @Test
    fun atrasDesdeLaBovedaSaleDeLaApp() {
        tab(BottomNavTab.Settings)
        tab(BottomNavTab.Vault)
        // Nada debajo de la Bóveda: el sistema cierra la actividad (no hay a dónde volver dentro de la app).
        assertEquals(listOf(Routes.VAULT), stack())
        assertNull(navController.previousBackStackEntry)
    }

    @Test
    fun dentroDeUnaPestanaAtrasRecorreSuPropiaPila() {
        tab(BottomNavTab.Settings)
        open(Routes.DEVICES)
        assertEquals(listOf(Routes.VAULT, Routes.SETTINGS, Routes.DEVICES), stack())
        back()
        assertEquals(listOf(Routes.VAULT, Routes.SETTINGS), stack())
        back()
        assertEquals(listOf(Routes.VAULT), stack())
    }

    @Test
    fun laBovedaConservaSuEstadoAlVolverDeOtraPestana() {
        val before = viewModels.getValue(Routes.VAULT)
        tab(BottomNavTab.Generator)
        tab(BottomNavTab.Vault)
        assertSame("la Bóveda se volvió a crear", before, viewModels.getValue(Routes.VAULT))
        assertEquals(listOf(Routes.VAULT), stack())
    }

    @Test
    fun saludYAjustesRecuperanSuPilaYSuEstado() {
        tab(BottomNavTab.Settings)
        open(Routes.DEVICES)
        val settings = viewModels.getValue(Routes.SETTINGS)
        val devices = viewModels.getValue(Routes.DEVICES)
        tab(BottomNavTab.Health)
        tab(BottomNavTab.Settings)
        // Ajustes → Dispositivos sigue como se dejó y con los mismos ViewModels.
        assertEquals(listOf(Routes.VAULT, Routes.SETTINGS, Routes.DEVICES), stack())
        assertSame(settings, viewModels.getValue(Routes.SETTINGS))
        assertSame(devices, viewModels.getValue(Routes.DEVICES))
    }

    @Test
    fun lasPantallasConSecretosSeVacianAlCambiarDePestana() {
        tab(BottomNavTab.Settings)
        open(Routes.TWO_FACTOR_SETUP)
        val setup = viewModels.getValue(Routes.TWO_FACTOR_SETUP)
        tab(BottomNavTab.Health)
        assertEquals(listOf(Routes.VAULT, Routes.HEALTH), stack())
        tab(BottomNavTab.Settings)
        // Al volver a Ajustes ya no está la 2FA con su clave y sus códigos (M3).
        assertEquals(listOf(Routes.VAULT, Routes.SETTINGS), stack())
        open(Routes.TWO_FACTOR_SETUP)
        assertNotSame("la clave de la 2FA se conservó", setup, viewModels.getValue(Routes.TWO_FACTOR_SETUP))
    }

    @Test
    fun laVerificacionActivaTampocoSeGuardaYLoAnteriorSi() {
        tab(BottomNavTab.Settings)
        open(Routes.DEVICES)
        open(Routes.TWO_FACTOR_ACTIVE)
        tab(BottomNavTab.Generator)
        tab(BottomNavTab.Settings)
        // Sale el destino con secretos y todo lo que estaba encima; lo anterior se conserva.
        assertEquals(listOf(Routes.VAULT, Routes.SETTINGS, Routes.DEVICES), stack())
    }

    @Test
    fun elGeneradorYElEditorDeUnaContrasenaNoSeGuardan() {
        tab(BottomNavTab.Generator)
        val generated = viewModels.getValue(Routes.GENERATOR)
        open(Routes.VAULT_NEW)
        // «Guardar en la bóveda» desde el Generador y luego otra pestaña: nada de lo escrito se queda.
        tab(BottomNavTab.Vault)
        assertEquals(listOf(Routes.VAULT), stack())
        tab(BottomNavTab.Generator)
        assertNotSame("lo generado se conservó", generated, viewModels.getValue(Routes.GENERATOR))
        assertEquals(listOf(Routes.VAULT, Routes.GENERATOR), stack())
    }

    @Test
    fun elDetalleDeUnElementoNoSeGuardaAlCambiarDePestana() {
        open(Routes.vaultItem(UUID.randomUUID()))
        tab(BottomNavTab.Health)
        tab(BottomNavTab.Vault)
        assertEquals(listOf(Routes.VAULT), stack())
    }

    @Test
    fun unSubflujoDeSaludConUnaEdicionConservaLaListaPeroNoLaEdicion() {
        tab(BottomNavTab.Health)
        open(Routes.HEALTH_BREACHED)
        open(Routes.VAULT_NEW)
        tab(BottomNavTab.Vault)
        tab(BottomNavTab.Health)
        // La edición (con la contraseña) se fue; el subflujo de Salud sigue.
        assertEquals(listOf(Routes.VAULT, Routes.HEALTH, Routes.HEALTH_BREACHED), stack())
    }

    @Test
    fun alBloquearSeOlvidaLoQueLasPestanasGuardaron() {
        tab(BottomNavTab.Health)
        val health = viewModels.getValue(Routes.HEALTH)
        tab(BottomNavTab.Vault)
        // Bloqueo: la función real que usan LockGuard, el cierre de sesión y el desbloqueo (pila limpia y sin estados guardados;
        // si no, `restoreState` resucitaría lo de antes del bloqueo).
        onUi { navigateClearingStack(Routes.VAULT) }
        tab(BottomNavTab.Health)
        assertNotSame("Salud volvió con lo de antes del bloqueo", health, viewModels.getValue(Routes.HEALTH))
        assertEquals(listOf(Routes.VAULT, Routes.HEALTH), stack())
    }

    @Test
    fun alBloquearTambienSeOlvidaLoGuardadoBajoLaBovedaYLosHijosNoSecretos() {
        // Un hijo no secreto de la Bóveda (la lista de conflictos) y Ajustes → Dispositivos, ambos guardados al cambiar de pestaña.
        open(Routes.CONFLICTS)
        val conflicts = viewModels.getValue(Routes.CONFLICTS)
        tab(BottomNavTab.Settings)
        open(Routes.DEVICES)
        val devices = viewModels.getValue(Routes.DEVICES)
        tab(BottomNavTab.Health)
        onUi { navigateClearingStack(Routes.VAULT) }
        // Tras el bloqueo nada de eso vuelve.
        tab(BottomNavTab.Settings)
        assertEquals(listOf(Routes.VAULT, Routes.SETTINGS), stack())
        open(Routes.DEVICES)
        assertNotSame("Dispositivos volvió con lo de antes del bloqueo", devices, viewModels.getValue(Routes.DEVICES))
        tab(BottomNavTab.Vault)
        // El hijo de la Bóveda que se guardó antes del bloqueo tampoco vuelve: al abrirlo es una pantalla nueva.
        open(Routes.CONFLICTS)
        assertNotSame("la Bóveda conservó un hijo de antes del bloqueo", conflicts, viewModels.getValue(Routes.CONFLICTS))
        assertEquals(listOf(Routes.VAULT, Routes.CONFLICTS), stack())
    }
}
