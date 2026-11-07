package com.luis.end_security.core.designsystem.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import com.luis.end_security.core.designsystem.theme.Motion
import com.luis.end_security.core.designsystem.theme.rememberReducedMotion
import kotlinx.coroutines.delay

/**
 * Entrada de tarjetas y filas (tarea 26): suben [Motion.EnterRise] y aparecen en [Motion.EnterMillis] con `EaseOut`, escalonadas
 * [Motion.Stagger] ms según su [index]. **Máximo [Motion.MaxStaggered] elementos escalonados**: del sexto en adelante entran con el
 * último (no se alarga la espera).
 *
 * Solo `alpha` y `translationY` en `graphicsLayer`: no recompone ni cambia el layout. Con [play] en `false` (ya se mostró antes en la
 * sesión) o con «Quitar animaciones», el elemento está en su lugar desde el primer cuadro.
 */
@Composable
fun Modifier.staggeredEnter(
    index: Int,
    play: Boolean = true,
    reducedMotion: Boolean = rememberReducedMotion(),
): Modifier {
    val animate = play && !reducedMotion
    val progress = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (animate) {
            delay(index.coerceIn(0, Motion.MaxStaggered - 1) * Motion.Stagger.toLong())
            progress.animateTo(1f, tween(Motion.EnterMillis, easing = Motion.EaseOut))
        }
    }
    val risePx = with(LocalDensity.current) { Motion.EnterRise.toPx() }
    return this.graphicsLayer {
        val p = progress.value
        alpha = p
        translationY = (1f - p) * risePx
    }
}

/**
 * Cambia entre las vistas de un selector (Resumen | Progreso, Más fuertes | Más débiles): fundido cruzado de [Motion.ContentSwap] ms
 * con un desplazamiento de [Motion.ContentSwapSlide] **en el sentido del selector** (al ir a la opción de la derecha, el contenido
 * entra desde la derecha; al volver, al revés: mismo camino). Con «Quitar animaciones», cambio directo.
 */
@Composable
fun DirectionalContentSwap(
    targetIndex: Int,
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = rememberReducedMotion(),
    content: @Composable (Int) -> Unit,
) {
    val slidePx = with(LocalDensity.current) { Motion.ContentSwapSlide.roundToPx() }
    AnimatedContent(
        targetState = targetIndex,
        modifier = modifier,
        transitionSpec = {
            if (reducedMotion) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                val forward = targetState > initialState
                val direction = if (forward) 1 else -1
                (
                    fadeIn(tween(Motion.ContentSwap, easing = Motion.EaseOut)) +
                        slideInHorizontally(tween(Motion.ContentSwap, easing = Motion.EaseOut)) { direction * slidePx }
                    ) togetherWith (
                    fadeOut(tween(Motion.ContentSwap, easing = Motion.EaseOut)) +
                        slideOutHorizontally(tween(Motion.ContentSwap, easing = Motion.EaseOut)) { -direction * slidePx }
                    ) using SizeTransform(clip = false) { _, _ -> snap() }
            }
        },
        label = "directionalContentSwap",
    ) { index -> content(index) }
}

/**
 * Un número que **sube a su valor** (tarea 26). Solo si [fromZeroFirst] es `true` (primer dibujo con datos de esa vista en la sesión),
 * va de 0 a [target] en [firstMillis], tras [firstDelayMillis]. Si no, nace ya en [target]. Cuando [target] cambia después (por
 * ejemplo tras «Revisar ahora»), va **del valor anterior al nuevo** en [changeMillis], sin volver a cero. Con «Quitar animaciones»
 * salta directo. No bloquea nada: la interfaz sigue respondiendo mientras tanto.
 */
@Composable
fun rememberAnimatedFloat(
    target: Float,
    fromZeroFirst: Boolean = false,
    firstMillis: Int = Motion.CountUp,
    changeMillis: Int = Motion.ValueChange,
    firstDelayMillis: Int = 0,
    reducedMotion: Boolean = rememberReducedMotion(),
): State<Float> {
    val animatable = remember { Animatable(if (fromZeroFirst && !reducedMotion) 0f else target) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(target, reducedMotion) {
        val isFirst = first
        first = false
        if (reducedMotion) {
            animatable.snapTo(target)
        } else if (isFirst && fromZeroFirst) {
            if (firstDelayMillis > 0) delay(firstDelayMillis.toLong())
            animatable.animateTo(target, tween(firstMillis, easing = Motion.EaseOut))
        } else {
            animatable.animateTo(target, tween(changeMillis, easing = Motion.EaseOut))
        }
    }
    return animatable.asState()
}
