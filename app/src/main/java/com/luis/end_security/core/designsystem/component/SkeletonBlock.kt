package com.luis.end_security.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.luis.end_security.core.designsystem.theme.PreviewSurface
import com.luis.end_security.core.designsystem.theme.rememberReducedMotion

// Figma («Hueso», nodo 69:3207): `outline` al 20 %.
private const val SkeletonAlpha = 0.2f

// El pulso va del 100 % al 50 % de esa opacidad, lento para que no distraiga.
private const val PulseMinFraction = 0.5f
private const val PulseHalfPeriodMillis = 900

/**
 * Pulso común: todos los bloques de una pantalla laten juntos. Sin [SkeletonPulse] cada bloque
 * usa el suyo.
 */
private val LocalSkeletonPulse = compositionLocalOf<State<Float>?> { null }

/**
 * Comparte un solo pulso entre los [SkeletonBlock] de [content]. Con «Quitar animaciones» el
 * pulso queda fijo al 100 %.
 */
@Composable
fun SkeletonPulse(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalSkeletonPulse provides rememberPulse(), content = content)
}

/**
 * Bloque gris de un estado de carga («Hueso» en Figma): `outline` al 20 % con un pulso suave
 * de opacidad. No es contenido: se oculta a TalkBack y la pantalla anuncia la carga con texto.
 */
@Composable
fun SkeletonBlock(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
) {
    val pulse = LocalSkeletonPulse.current ?: rememberPulse()
    val color = MaterialTheme.colorScheme.outline
    Box(
        modifier = modifier
            .clearAndSetSemantics {}
            .graphicsLayer { alpha = SkeletonAlpha * pulse.value }
            .background(color, shape),
    )
}

@Composable
private fun rememberPulse(): State<Float> {
    if (rememberReducedMotion()) return remember { mutableFloatStateOf(1f) }
    return rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 1f,
        targetValue = PulseMinFraction,
        animationSpec = infiniteRepeatable(
            animation = tween(PulseHalfPeriodMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeletonAlpha",
    )
}

@Preview(name = "Bloque de carga")
@Composable
private fun SkeletonBlockPreview() {
    PreviewSurface(modifier = Modifier.padding(24.dp)) {
        SkeletonPulse {
            Column {
                SkeletonBlock(modifier = Modifier.size(44.dp), shape = RoundedCornerShape(13.dp))
                Spacer(Modifier.height(12.dp))
                SkeletonBlock(modifier = Modifier.width(110.dp).height(14.dp), shape = RoundedCornerShape(7.dp))
            }
        }
    }
}
