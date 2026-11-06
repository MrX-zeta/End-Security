package com.luis.end_security.core.designsystem.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.ui.unit.dp

/**
 * Movimiento de la app (tarea 24). Un solo criterio para todas las transiciones, tomado de *Designing Fluid Interfaces* (Apple) y de
 * la guía de movimiento de Emil Kowalski:
 * - duraciones **menores de 300 ms**: la interfaz no debe sentirse lenta;
 * - curva **ease-out** (entra rápido y frena);
 * - **mismo camino al entrar y al salir**, e **interrumpible** (se parte del estado actual);
 * - en lo que se usa muchas veces al día (cambiar de pestaña), lo más discreto.
 *
 * Solo se animan `alpha` y `translationX` (no cambia el layout durante la transición). Con «Quitar animaciones» del teléfono no hay
 * transición (ver `rememberReducedMotion`).
 */
object Motion {
    /** `EaseOutQuint`: la curva única de la app. */
    val EaseOut: Easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

    /** Cambio de pestaña: fundido cruzado. Entra la nueva… */
    const val TabFade = 150

    /** …y sale la anterior, más rápido. */
    const val TabFadeOut = 90

    /** Avanzar o regresar dentro de una pestaña: la pantalla que entra… */
    const val Push = 240

    /** …y la que sale. */
    const val PushExit = 200

    /** Bloquear, entrar y salir de sesión: un cambio de estado, no de jerarquía. Fundido sin desplazamiento. */
    const val StateFade = 200

    /** Cuánto se desplazan las pantallas al avanzar y regresar: lo justo para indicar dirección. */
    val Slide = 30.dp

    // Topes que las pruebas de tiempo exigen (duración + un cuadro de margen).
    const val TabSwitchLimitMillis = 200L
    const val PushLimitMillis = 260L
}
