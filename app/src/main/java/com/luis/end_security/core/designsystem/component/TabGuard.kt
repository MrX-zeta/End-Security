package com.luis.end_security.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Deja que la pantalla visible **intercepte** un toque en la barra inferior persistente (tarea 26).
 *
 * Antes cada pantalla dibujaba su barra y decidía qué hacer al tocar una pestaña: ignorarla mientras trabaja
 * (cambiar la contraseña, eliminar la cuenta) o pedir confirmación (códigos de respaldo del 2FA). Ahora la barra es una sola y vive
 * fuera de las pantallas, así que la pantalla activa se registra aquí y la barra le pregunta primero.
 */
@Stable
class TabGuard {
    private var interceptor: ((BottomNavTab) -> Boolean)? = null

    internal fun register(interceptor: (BottomNavTab) -> Boolean) {
        this.interceptor = interceptor
    }

    /** Quita [interceptor] solo si sigue siendo el registrado: una pantalla que sale tarde no debe borrar el de la que ya entró. */
    internal fun unregister(interceptor: (BottomNavTab) -> Boolean) {
        if (this.interceptor === interceptor) this.interceptor = null
    }

    /** `true` si la pantalla se hizo cargo del toque (no hay que cambiar de pestaña). */
    fun intercepts(tab: BottomNavTab): Boolean = interceptor?.invoke(tab) == true
}

/** La da `AppNavHost`; sin ella (vistas previas y pruebas de una pantalla sola) nada intercepta. */
val LocalTabGuard = staticCompositionLocalOf<TabGuard?> { null }

/**
 * La pantalla que lo llama intercepta los toques de la barra mientras esté en pantalla: [interceptor] devuelve `true` si los atiende
 * ella (y entonces no se cambia de pestaña). Se lee en el momento del toque, así que siempre ve el estado actual.
 */
@Composable
fun InterceptTabSelection(interceptor: (BottomNavTab) -> Boolean) {
    val guard = LocalTabGuard.current ?: return
    val current by rememberUpdatedState(interceptor)
    DisposableEffect(guard) {
        val mine: (BottomNavTab) -> Boolean = { tab -> current(tab) }
        guard.register(mine)
        onDispose { guard.unregister(mine) }
    }
}
