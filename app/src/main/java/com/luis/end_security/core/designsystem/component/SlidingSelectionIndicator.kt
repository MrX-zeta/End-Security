package com.luis.end_security.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.util.lerp
import com.luis.end_security.core.designsystem.theme.rememberReducedMotion
import kotlin.math.abs

/**
 * Resorte sin rebote que mueve el fondo seleccionado (Apple: amortiguamiento 1,0, respuesta ~0,3 s); llega en unos 250 ms.
 * Es el mismo en la barra inferior y en los selectores de Salud, así que se sienten como una sola cosa.
 */
internal val SelectionSpring = spring<Float>(dampingRatio = 1f, stiffness = 440f)

/**
 * Estado compartido de un fondo seleccionado que **se desliza** entre opciones (tarea 26; sale de la píldora de la barra de la tarea 25).
 *
 * - La posición vive en «índices de opción» (0 a n−1), no en píxeles: sobrevive a un cambio de ancho y el resorte dura lo mismo cerca
 *   o lejos.
 * - Se lee **solo en la fase de dibujo** (`graphicsLayer`, `drawBehind`): ni el selector ni sus opciones se recomponen cuadro a cuadro.
 * - Si se elige otra opción a mitad del camino, el resorte sigue desde donde va (sin saltos).
 * - Con «Quitar animaciones» la posición salta.
 */
@Stable
class SlidingSelectionState internal constructor(initialIndex: Int, count: Int) {
    internal val position = Animatable(initialIndex.toFloat())

    /** Centro horizontal de cada opción dentro del contenedor, medido (no calculado). */
    internal val centers = mutableStateListOf<Float>().apply { repeat(count) { add(0f) } }

    /** Qué tan cerca está el fondo de la opción [index]: 1 justo debajo, 0 a una opción o más. Se lee al dibujar. */
    fun reach(index: Int): Float = (1f - abs(position.value - index)).coerceIn(0f, 1f)

    internal fun centerAt(position: Float): Float {
        if (centers.size < 2) return centers.firstOrNull() ?: 0f
        val clamped = position.coerceIn(0f, (centers.size - 1).toFloat())
        val low = clamped.toInt().coerceAtMost(centers.size - 2)
        return lerp(centers[low], centers[low + 1], clamped - low)
    }

    internal suspend fun moveTo(index: Int, animate: Boolean) {
        if (animate) position.animateTo(index.toFloat(), SelectionSpring) else position.snapTo(index.toFloat())
    }

    /** Valor de la posición ahora, para pruebas. */
    val currentPosition: Float get() = position.value
}

/**
 * Crea el estado y lo mueve a [selectedIndex] cada vez que cambia. Arranca ya en su lugar (sin animar al primer dibujo).
 */
@Composable
fun rememberSlidingSelectionState(
    selectedIndex: Int,
    count: Int,
    reducedMotion: Boolean = rememberReducedMotion(),
): SlidingSelectionState {
    val state = remember(count) { SlidingSelectionState(selectedIndex, count) }
    LaunchedEffect(selectedIndex, reducedMotion) { state.moveTo(selectedIndex, animate = !reducedMotion) }
    return state
}

/** Anota el centro de esta opción para que el fondo sepa a dónde ir. Va en cada opción. */
fun Modifier.trackSelectionCenter(state: SlidingSelectionState, index: Int): Modifier = onGloballyPositioned { coordinates ->
    val center = coordinates.positionInParent().x + coordinates.size.width / 2f
    if (state.centers[index] != center) state.centers[index] = center
}

/**
 * El fondo que se desliza: va **detrás** de las opciones, en el mismo contenedor. Su tamaño lo da [modifier]. Decorativo: el estado
 * lo dicen las opciones (rol y seleccionado para TalkBack).
 */
@Composable
fun SlidingSelectionIndicator(
    state: SlidingSelectionState,
    color: Color,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    Box(
        // El desplazamiento va primero: lo que [modifier] ponga después (tamaño, etiqueta de prueba) ya lo incluye.
        modifier = Modifier
            .graphicsLayer { translationX = state.centerAt(state.position.value) - size.width / 2f }
            .then(modifier)
            .background(color, shape),
    )
}
