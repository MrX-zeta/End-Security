package com.luis.end_security.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry
import com.luis.end_security.core.designsystem.component.BottomNavTab
import com.luis.end_security.core.designsystem.theme.Motion

/** Qué transición va entre dos pantallas (tarea 24). */
internal enum class NavMotionKind {
    /** Entre pestañas hermanas: fundido cruzado corto, sin desplazamiento. */
    TabSwitch,

    /** Avanzar y regresar dentro de una pestaña (o de un flujo): eje horizontal compartido de 30 dp. */
    Push,

    /** Bloquear, entrar o salir de sesión: fundido, sin desplazamiento. */
    StateChange,
}

private val TabRoots = BottomNavTab.entries.map { it.route() }.toSet()

/** Pantallas que son un cambio de estado de la sesión: siempre un fundido. */
private val StateRoutes = setOf(Routes.UNLOCK, Routes.ACCOUNT_DELETED, Routes.VAULT_LOADING)

/** La zona de la app sin sesión (acceso). Pasar de una zona a otra (Iniciar sesión ↔ Bóveda) es un cambio de estado. */
private val SignedOutRoutes = setOf(
    Routes.ONBOARDING, Routes.LOGIN, Routes.SIGN_UP, Routes.VERIFY_EMAIL, Routes.TWO_FACTOR, Routes.BACKUP_CODE,
)

private fun isSignedOut(route: String?): Boolean? = when (route) {
    null -> null
    in SignedOutRoutes -> true
    // Los documentos legales se abren desde las dos zonas: no cambian de zona.
    Routes.LEGAL_DOCUMENT -> null
    else -> false
}

internal fun navMotionKind(from: String?, to: String?): NavMotionKind {
    if (from in TabRoots && to in TabRoots) return NavMotionKind.TabSwitch
    if (from in StateRoutes || to in StateRoutes) return NavMotionKind.StateChange
    val fromZone = isSignedOut(from)
    val toZone = isSignedOut(to)
    if (fromZone != null && toZone != null && fromZone != toZone) return NavMotionKind.StateChange
    return NavMotionKind.Push
}

/** `true` si lo que sale es la zona con sesión y va a una pantalla de acceso o a Desbloquear: ahí no se deja ver la bóveda desvanecerse. */
private fun leavesSecureZoneToAccess(from: String?, to: String?): Boolean =
    isSignedOut(from) == false && (to == Routes.UNLOCK || to == Routes.LOGIN || to == Routes.ACCOUNT_DELETED)

/**
 * Las cuatro transiciones del `NavHost` (tarea 24). [slidePx] son los 30 dp en píxeles; [reduced] es «Quitar animaciones».
 * Se aplican a todas las rutas: ninguna pantalla debe poner las suyas (las hojas inferiores y los diálogos traen su animación).
 */
internal class NavTransitions(private val slidePx: Int, private val reduced: Boolean) {

    private fun kind(scope: AnimatedContentTransitionScope<NavBackStackEntry>) =
        navMotionKind(scope.initialState.destination.route, scope.targetState.destination.route)

    private fun fade(millis: Int) = tween<Float>(millis, easing = Motion.EaseOut)

    fun enter(scope: AnimatedContentTransitionScope<NavBackStackEntry>): EnterTransition {
        if (reduced) return EnterTransition.None
        return when (kind(scope)) {
            NavMotionKind.TabSwitch -> fadeIn(fade(Motion.TabFade))
            NavMotionKind.StateChange -> fadeIn(fade(Motion.StateFade))
            // Entra desde +30 dp hasta 0, con fundido.
            NavMotionKind.Push -> slideInHorizontally(tween(Motion.Push, easing = Motion.EaseOut)) { slidePx } +
                fadeIn(fade(Motion.Push))
        }
    }

    fun exit(scope: AnimatedContentTransitionScope<NavBackStackEntry>): ExitTransition {
        if (reduced) return ExitTransition.None
        return when (kind(scope)) {
            NavMotionKind.TabSwitch -> fadeOut(fade(Motion.TabFadeOut))
            NavMotionKind.StateChange ->
                if (leavesSecureZoneToAccess(scope.initialState.destination.route, scope.targetState.destination.route)) {
                    // Se bloquea o se cierra la sesión: lo que había no se deja ver desvanecerse (hallazgo 3 de la tarea 09). Con
                    // `ExitTransition.None` la anterior seguiría a la vista hasta que termine la entrada; `snap` la apaga al instante.
                    fadeOut(snap())
                } else {
                    fadeOut(fade(Motion.StateFade))
                }
            // Sale de 0 a −30 dp, con fundido.
            NavMotionKind.Push -> slideOutHorizontally(tween(Motion.PushExit, easing = Motion.EaseOut)) { -slidePx } +
                fadeOut(fade(Motion.PushExit))
        }
    }

    /** Regresar: el mismo camino al revés. La anterior vuelve desde −30 dp. */
    fun popEnter(scope: AnimatedContentTransitionScope<NavBackStackEntry>): EnterTransition {
        if (reduced) return EnterTransition.None
        return when (kind(scope)) {
            NavMotionKind.TabSwitch -> fadeIn(fade(Motion.TabFade))
            NavMotionKind.StateChange -> fadeIn(fade(Motion.StateFade))
            NavMotionKind.Push -> slideInHorizontally(tween(Motion.Push, easing = Motion.EaseOut)) { -slidePx } +
                fadeIn(fade(Motion.Push))
        }
    }

    /** Regresar: la actual sale hacia +30 dp. */
    fun popExit(scope: AnimatedContentTransitionScope<NavBackStackEntry>): ExitTransition {
        if (reduced) return ExitTransition.None
        return when (kind(scope)) {
            NavMotionKind.TabSwitch -> fadeOut(fade(Motion.TabFadeOut))
            NavMotionKind.StateChange ->
                if (leavesSecureZoneToAccess(scope.initialState.destination.route, scope.targetState.destination.route)) {
                    fadeOut(snap())
                } else {
                    fadeOut(fade(Motion.StateFade))
                }
            NavMotionKind.Push -> slideOutHorizontally(tween(Motion.PushExit, easing = Motion.EaseOut)) { slidePx } +
                fadeOut(fade(Motion.PushExit))
        }
    }
}
