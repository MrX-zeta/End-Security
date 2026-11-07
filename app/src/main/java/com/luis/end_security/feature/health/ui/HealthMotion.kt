package com.luis.end_security.feature.health.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.util.lerp
import com.luis.end_security.core.designsystem.theme.Motion
import com.luis.end_security.core.designsystem.theme.rememberReducedMotion

/**
 * Una lista de valores (los puntos de la gráfica) que, cuando [target] cambia, va **de los valores que se veían a los nuevos** en
 * [Motion.ValueChange] ms (sin volver a cero). Nace ya en [target]: la primera vez no anima nada. Con «Quitar animaciones» salta.
 */
@Composable
fun rememberMorphingValues(target: List<Float>, reducedMotion: Boolean = rememberReducedMotion()): State<List<Float>> {
    val progress = remember { Animatable(1f) }
    var from by remember { mutableStateOf(target) }
    var to by remember { mutableStateOf(target) }
    LaunchedEffect(target, reducedMotion) {
        if (target != to) {
            from = interpolate(from, to, progress.value)
            to = target
            if (reducedMotion) {
                progress.snapTo(1f)
            } else {
                progress.snapTo(0f)
                progress.animateTo(1f, tween(Motion.ValueChange, easing = Motion.EaseOut))
            }
        }
    }
    return remember { derivedStateOf { interpolate(from, to, progress.value) } }
}

private fun interpolate(from: List<Float>, to: List<Float>, t: Float): List<Float> =
    to.mapIndexed { i, value -> lerp(from.getOrElse(i) { value }, value, t) }
