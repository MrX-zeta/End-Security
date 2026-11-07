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
    const val Push = 260

    /** …y la que sale. */
    const val PushExit = 220

    /** Bloquear, entrar y salir de sesión: un cambio de estado, no de jerarquía. Fundido sin desplazamiento. */
    const val StateFade = 200

    /** Cuánto se desplazan las pantallas al avanzar y regresar: se nota la dirección sin alargar la duración (tarea 25: 30 → 60 dp). */
    val Slide = 60.dp

    /** La barra inferior persistente aparece y se oculta (tarea 26): desliza hacia abajo y se desvanece. */
    const val BarVisibility = 200

    /** Entrada de tarjetas y filas (tarea 26): suben [EnterRise] y aparecen, escalonadas [Stagger] ms (máximo [MaxStaggered] elementos). */
    val EnterRise = 8.dp
    const val EnterMillis = 200
    const val Stagger = 30
    const val MaxStaggered = 6

    /** Cambio de contenido entre las vistas de un selector (Resumen/Progreso): fundido cruzado con este desplazamiento. */
    const val ContentSwap = 150
    val ContentSwapSlide = 16.dp

    /** Los datos de Salud se dibujan una vez («momento» de la app): la línea y el arco, el número y las barras. */
    const val ChartDraw = 600
    const val CountUp = 600

    /** Las barras de «Por fuerza» crecen más rápido y, si los datos cambian después, todo va del valor anterior al nuevo. */
    const val BarGrow = 300
    const val ValueChange = 300

    /** Avisos (`MessageBar`): entran subiendo [MessageRiseIn] y salen bajando [MessageDropOut]; la salida es más rápida. */
    const val MessageIn = 200
    const val MessageOut = 150
    val MessageRiseIn = 16.dp
    val MessageDropOut = 8.dp

    // Topes que las pruebas de tiempo exigen (duración + un cuadro de margen).
    const val TabSwitchLimitMillis = 200L
    const val PushLimitMillis = 280L
}
