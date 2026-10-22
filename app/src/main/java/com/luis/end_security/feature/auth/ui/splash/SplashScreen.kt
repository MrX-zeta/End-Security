package com.luis.end_security.feature.auth.ui.splash

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luis.end_security.R
import com.luis.end_security.core.designsystem.theme.EndSecurityTheme
import com.luis.end_security.core.designsystem.theme.Montserrat
import com.luis.end_security.core.designsystem.theme.PreviewSurface
import com.luis.end_security.core.designsystem.theme.SystemBarsAppearance
import com.luis.end_security.core.designsystem.theme.rememberReducedMotion
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

// Medidas de Figma (tablero «Splash · Final», nodo 165:3711), en dp sobre un marco de 412 × 917.
/** Lienzo del ícono del sistema (capa frontal de 108 dp ampliada a 240 dp). */
private val ShieldCanvas = 240.dp

/** El escudo termina a 104,27 / 132,4 de su tamaño inicial («0,79×»). */
private const val ShieldEndScale = 0.7875f

/** El escudo sube 86 dp respecto a su posición inicial (centro de la pantalla). */
private val ShieldRise = 86.dp

/** Centro de la marca (y = 487,5) y del lema (y = 525) respecto al centro de la pantalla (458,5). */
private val BrandOffsetY = 29.dp
private val TaglineOffsetY = 66.5.dp

/** La marca y el lema suben 12 dp mientras aparecen. */
private val TextRise = 12.dp

/** Los puntos terminan a 97 dp del borde inferior del marco, que incluye 24 dp de barra de gestos. */
private val DotsBottom = 73.dp
private val DotSize = 8.dp
private val DotGap = 8.dp

/** `ease-out` fuerte de la skill `animate`: arranca rápido y asienta suave. */
private val EaseOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

/**
 * Splash propio (SCR-46, B). Se dibuja encima de la navegación en cada arranque en frío: el escudo
 * empieza exactamente donde el sistema dibujó el ícono (A) y sube a su lugar final; la marca y el
 * lema aparecen; si el destino tarda, salen los puntos. Cuando hay destino y terminó la entrada,
 * se funde hacia la ruta (que ya está debajo) y [onFinished] lo quita.
 *
 * Es de tema oscuro (DEC-51), como el onboarding. Atrás sale de la app.
 */
@Composable
fun SplashRoute(
    destinationReady: Boolean,
    onFinished: () -> Unit,
) {
    val reducedMotion = rememberReducedMotion()
    val shield = remember { Animatable(if (reducedMotion) 1f else 0f) }
    val brand = remember { Animatable(if (reducedMotion) 1f else 0f) }
    val tagline = remember { Animatable(if (reducedMotion) 1f else 0f) }
    val exitAlpha = remember { Animatable(1f) }
    val ready = remember { MutableStateFlow(destinationReady) }
    val sequencer = remember(reducedMotion) { SplashSequencer(reducedMotion) }
    val showDots by sequencer.showDots.collectAsStateWithLifecycle()
    val currentOnFinished by rememberUpdatedState(onFinished)

    LaunchedEffect(destinationReady) { ready.value = destinationReady }
    LaunchedEffect(sequencer) {
        sequencer.run(
            destinationReady = ready,
            playEntry = {
                coroutineScope {
                    // Resorte crítico (amortiguamiento 1, respuesta ≈ 0,4 s): sin rebote visible.
                    launch { shield.animateTo(1f, spring(dampingRatio = 1f, stiffness = SHIELD_STIFFNESS)) }
                    launch {
                        delay(SplashTiming.BRAND_DELAY_MS)
                        brand.animateTo(1f, tween(SplashTiming.BRAND_MS, easing = EaseOut))
                    }
                    launch {
                        delay(SplashTiming.TAGLINE_DELAY_MS)
                        tagline.animateTo(1f, tween(SplashTiming.TAGLINE_MS, easing = EaseOut))
                    }
                }
            },
            playExit = {
                if (!reducedMotion) exitAlpha.animateTo(0f, tween(SplashTiming.EXIT_MS, easing = EaseOut))
                currentOnFinished()
            },
        )
    }

    // Atrás durante el splash sale de la app.
    val activity = LocalActivity.current
    BackHandler { activity?.finish() }
    SystemBarsAppearance(darkBackground = true)

    EndSecurityTheme(darkTheme = true) {
        SplashContent(
            shieldProgress = { shield.value },
            brandProgress = { brand.value },
            taglineProgress = { tagline.value },
            showDots = showDots,
            animateDots = !reducedMotion,
            modifier = Modifier
                .graphicsLayer { alpha = exitAlpha.value }
                // Mientras el splash está encima, nada de lo de abajo recibe toques.
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) awaitPointerEvent().changes.forEach { it.consume() }
                    }
                },
        )
    }
}

/** Respuesta de 0,4 s (2π / 0,4)², la de «mover» en Apple: asienta en unos 450 ms. */
private const val SHIELD_STIFFNESS = 247f

/**
 * La composición, sin estado: cada progreso va de 0 (A, el ícono del sistema) a 1 (B, final).
 * Los progresos se leen dentro de `graphicsLayer`, así que animarlos no recompone.
 */
@Composable
fun SplashContent(
    shieldProgress: () -> Float,
    brandProgress: () -> Float,
    taglineProgress: () -> Float,
    showDots: Boolean,
    animateDots: Boolean,
    modifier: Modifier = Modifier,
) {
    val appName = stringResource(R.string.app_name)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // El lector de pantalla anuncia «End Security» una sola vez, al aparecer.
            .semantics { paneTitle = appName },
    ) {
        // Decorativo: la marca ya está en el texto.
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.Center)
                .size(ShieldCanvas)
                .graphicsLayer {
                    val progress = shieldProgress()
                    val scale = 1f + (ShieldEndScale - 1f) * progress
                    scaleX = scale
                    scaleY = scale
                    translationY = -ShieldRise.toPx() * progress
                },
        )
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("End") }
                append(" Security")
            },
            style = MaterialTheme.typography.headlineLarge.copy(
                fontFamily = Montserrat,
                fontWeight = FontWeight.Normal,
                fontSize = 32.sp,
                lineHeight = 39.sp,
                letterSpacing = (-0.02).em,
            ),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = BrandOffsetY)
                .graphicsLayer {
                    val progress = brandProgress()
                    alpha = progress
                    translationY = TextRise.toPx() * (1f - progress)
                },
        )
        Text(
            text = stringResource(R.string.splash_tagline),
            style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = Montserrat,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.04.em,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = TaglineOffsetY)
                .graphicsLayer {
                    val progress = taglineProgress()
                    alpha = progress
                    translationY = TextRise.toPx() * (1f - progress)
                },
        )
        LoadingDots(
            visible = showDots,
            animate = animateDots,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = DotsBottom),
        )
    }
}

/** Tres puntos `primary` con un pulso suave, desfasados. No se anuncian al lector de pantalla. */
@Composable
private fun LoadingDots(visible: Boolean, animate: Boolean, modifier: Modifier = Modifier) {
    val appear = remember { Animatable(0f) }
    LaunchedEffect(visible, animate) {
        val target = if (visible) 1f else 0f
        if (animate) appear.animateTo(target, tween(200, easing = EaseOut)) else appear.snapTo(target)
    }
    // Sin puntos a la vista no se compone el pulso: no mantiene fotogramas encendidos de balde.
    if (!visible && appear.value == 0f) return
    val transition = rememberInfiniteTransition(label = "dots")
    Row(
        modifier = modifier
            .alpha(appear.value)
            .clearAndSetSemantics { },
        horizontalArrangement = Arrangement.spacedBy(DotGap),
    ) {
        repeat(3) { index ->
            val pulse by transition.animateFloat(
                initialValue = 0.35f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(index * 150),
                ),
                label = "dot$index",
            )
            Box(
                modifier = Modifier
                    .size(DotSize)
                    .alpha(if (animate) pulse else 1f)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

@Preview(name = "Splash · inicial (A)", device = "spec:width=412dp,height=917dp")
@Composable
private fun SplashInitialPreview() {
    PreviewSurface(darkTheme = true) {
        SplashContent({ 0f }, { 0f }, { 0f }, showDots = false, animateDots = false)
    }
}

@Preview(name = "Splash · final (B)", device = "spec:width=412dp,height=917dp")
@Composable
private fun SplashFinalPreview() {
    PreviewSurface(darkTheme = true) {
        SplashContent({ 1f }, { 1f }, { 1f }, showDots = false, animateDots = false)
    }
}

@Preview(name = "Splash · con puntos", device = "spec:width=412dp,height=917dp")
@Composable
private fun SplashDotsPreview() {
    PreviewSurface(darkTheme = true) {
        SplashContent({ 1f }, { 1f }, { 1f }, showDots = true, animateDots = false)
    }
}
