package com.luis.end_security.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import com.luis.end_security.core.designsystem.component.BottomNavTab

/**
 * Las pestañas no se animan (tarea 23): con las transiciones por defecto del `NavHost` el regreso a la Bóveda esperaba un fundido de
 * entrada de 700 ms con el fondo vacío. Aparecen al instante, con el último estado que tenían.
 */
internal val TabEnterNone: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = { EnterTransition.None }
internal val TabExitNone: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = { ExitTransition.None }

/** La ruta de cada pestaña de la barra inferior. */
internal fun BottomNavTab.route(): String = when (this) {
    BottomNavTab.Vault -> Routes.VAULT
    BottomNavTab.Health -> Routes.HEALTH
    BottomNavTab.Generator -> Routes.GENERATOR
    BottomNavTab.Settings -> Routes.SETTINGS
}

/**
 * Destinos que muestran o guardan secretos (la clave TOTP y los códigos de respaldo, contraseñas, lo generado, el código del correo
 * o de la cuenta): al cambiar de pestaña **salen de la pila sin guardarse**, así que al volver ya no están (M3 de la tarea 17 y la
 * hoja de la tarea 18). Lo demás —las listas y las pantallas sin secretos— sí se conserva con `saveState`/`restoreState`.
 */
internal val SECRET_ROUTES = setOf(
    Routes.GENERATOR,
    Routes.VAULT_ITEM,
    Routes.VAULT_EDIT,
    Routes.CONFLICT_ITEM,
    Routes.TWO_FACTOR_SETUP,
    Routes.TWO_FACTOR_ACTIVE,
    Routes.CHANGE_PASSWORD,
    Routes.CHANGE_EMAIL,
    Routes.DELETE_ACCOUNT,
)

/**
 * Cambia de pestaña con el patrón de navegación inferior de Material (tarea 23): no suma historial.
 * - Atrás desde Salud, Generador o Ajustes vuelve a la Bóveda; Atrás desde la Bóveda sale de la app.
 * - Cada pestaña guarda su pila (`saveState`) y la recupera al volver (`restoreState`): la Bóveda y su lista ya descifrada siguen
 *   ahí mientras la bóveda esté abierta.
 * - Antes de salir, lo que tiene secretos ([SECRET_ROUTES]) se saca de la pila sin guardarlo.
 */
internal fun NavHostController.navigateToTab(tab: BottomNavTab) {
    removeSecretDestinations()
    navigate(tab.route()) {
        popUpTo(Routes.VAULT) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Saca de la pila, sin guardarlo, el destino con secretos más profundo y todo lo que está encima; repite hasta que no quede ninguno. */
private fun NavHostController.removeSecretDestinations() {
    var removed = true
    while (removed) {
        removed = false
        for (route in SECRET_ROUTES) {
            // `getBackStackEntry` lanza si la ruta no está en la pila: así se sabe si hay algo que sacar (API pública).
            val inStack = runCatching { getBackStackEntry(route) }.isSuccess
            // `inclusive = true` y sin `saveState`: no se guarda nada de lo que se saca (ni lo que estaba encima).
            if (inStack && popBackStack(route, inclusive = true, saveState = false)) removed = true
        }
    }
}

/**
 * Olvida lo que las pestañas dejaron guardado. Hay que llamarlo cuando la bóveda se bloquea o se cierra la sesión: si no, al volver a
 * entrar `restoreState` resucitaría pilas (y sus ViewModels) de antes del bloqueo con datos ya descifrados.
 */
internal fun NavHostController.clearSavedTabStates() {
    for (tab in BottomNavTab.entries) clearBackStack(tab.route())
}
