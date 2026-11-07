package com.luis.end_security.core.designsystem.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.luis.end_security.core.designsystem.theme.Motion
import com.luis.end_security.core.designsystem.theme.rememberReducedMotion
import kotlinx.coroutines.delay

/**
 * Movimiento **dentro** de una pantalla (tarea 27). Las transiciones entre pantallas ya las pone `NavMotion`; esto es lo que
 * cambia sin salir de ella: lo que aparece o desaparece, los pasos de un flujo, de «cargando» al contenido y los valores que cambian.
 * Todo con `Motion.EaseOut`, sin pasar de 300 ms y con «Quitar animaciones» el cambio es directo.
 */

/**
 * Lo que aparece o desaparece dentro de una pantalla (errores de campo, listas de requisitos, un campo que solo existe a veces):
 * **se despliega con fundido en [Motion.RevealIn] ms y se pliega con fundido en [Motion.RevealOut] ms**, así el contenido de abajo se
 * desplaza suave en vez de saltar. Nunca se usa para el texto que se escribe.
 */
@Composable
fun Reveal(
    visible: Boolean,
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = rememberReducedMotion(),
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = if (reducedMotion) {
            EnterTransition.None
        } else {
            expandVertically(tween(Motion.RevealIn, easing = Motion.EaseOut)) + fadeIn(tween(Motion.RevealIn, easing = Motion.EaseOut))
        },
        exit = if (reducedMotion) {
            ExitTransition.None
        } else {
            shrinkVertically(tween(Motion.RevealOut, easing = Motion.EaseOut)) + fadeOut(tween(Motion.RevealOut, easing = Motion.EaseOut))
        },
        content = { content() },
    )
}

/**
 * De «cargando» (esqueleto) al contenido: **fundido cruzado de [Motion.LoadingFade] ms** sin movimiento. [loading] es el estado; cada
 * lado se dibuja en su lugar. Los saltos de altura entre el esqueleto y el contenido no se animan (el fundido ya los suaviza).
 */
@Composable
fun <T> LoadingCrossfade(
    state: T,
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = rememberReducedMotion(),
    content: @Composable (T) -> Unit,
) {
    AnimatedContent(
        targetState = state,
        modifier = modifier,
        transitionSpec = {
            if (reducedMotion) {
                (EnterTransition.None togetherWith ExitTransition.None) using SizeTransform(clip = false) { _, _ -> snap() }
            } else {
                (fadeIn(tween(Motion.LoadingFade, easing = Motion.EaseOut)) togetherWith fadeOut(tween(Motion.LoadingFade, easing = Motion.EaseOut))) using
                    SizeTransform(clip = false) { _, _ -> snap() }
            }
        },
        label = "loadingCrossfade",
    ) { value -> content(value) }
}

/**
 * Un texto o valor que **cambia en su lugar** (el «Revisado hoy a las…» de Salud, el número de dispositivos de Ajustes): fundido de
 * [Motion.ValueFade] ms entre el anterior y el nuevo, sin desplazamiento.
 */
@Composable
fun <T> FadeOnChange(
    value: T,
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = rememberReducedMotion(),
    content: @Composable (T) -> Unit,
) {
    AnimatedContent(
        targetState = value,
        modifier = modifier,
        transitionSpec = {
            if (reducedMotion) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                fadeIn(tween(Motion.ValueFade, easing = Motion.EaseOut)) togetherWith fadeOut(tween(Motion.ValueFade, easing = Motion.EaseOut))
            }
        },
        label = "fadeOnChange",
    ) { current -> content(current) }
}

/**
 * Verdadero durante [windowMillis] desde que llegan los datos ([key] cambia). Las filas de una lista se animan al entrar **solo si se
 * componen dentro de esa ventana** ([staggeredEnter]): las que aparecen después, al desplazar la lista, no.
 */
@Composable
fun rememberArrivalWindow(key: Any?, windowMillis: Long = Motion.ArrivalWindowMillis): Boolean {
    var open by remember(key) { mutableStateOf(true) }
    LaunchedEffect(key) {
        delay(windowMillis)
        open = false
    }
    return open
}

/**
 * Los pasos de un flujo (activar el 2FA, correo nuevo → código): la **misma transición de avanzar y regresar de `NavMotion`** — 60 dp y
 * fundido, [Motion.Push]/[Motion.PushExit] ms—, y **el mismo camino** al volver: avanzar entra desde la derecha y regresar desde la
 * izquierda.
 */
@Composable
fun StepSwap(
    step: Int,
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = rememberReducedMotion(),
    content: @Composable ColumnScope.(Int) -> Unit,
) {
    DirectionalContentSwap(
        targetIndex = step,
        modifier = modifier,
        reducedMotion = reducedMotion,
        slide = Motion.Slide,
        enterMillis = Motion.Push,
        exitMillis = Motion.PushExit,
        content = content,
    )
}

/**
 * Como [Reveal], para un valor que puede faltar (el texto de un error): se despliega al aparecer [value] y, al quitarse, **se pliega
 * mostrando todavía el último valor** en vez de desaparecer de golpe.
 */
@Composable
fun <T : Any> RevealValue(
    value: T?,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    var last by remember { mutableStateOf(value) }
    SideEffect { if (value != null) last = value }
    val shown = value ?: last
    Reveal(visible = value != null, modifier = modifier) {
        if (shown != null) content(shown)
    }
}

/** Una fila de [rememberLeavingList]: [present] es `false` mientras sale. */
class LeavingItem<T>(val item: T, val present: Boolean)

/**
 * Una lista que **conserva un momento las filas que se quitaron** para poder animar su salida (la fila se encoge y se desvanece en
 * [Motion.RowMove] ms). Las filas nuevas entran como siempre. Úsala donde las filas no viven en un `LazyColumn` con `animateItem`
 * (por ejemplo, las filas de una tarjeta): cada fila se dibuja con [Reveal] (`visible = present`). [key] debe ser estable por fila.
 */
@Composable
fun <T> rememberLeavingList(items: List<T>, key: (T) -> Any, holdMillis: Long = Motion.RowMove + 60L): List<LeavingItem<T>> {
    val reducedMotion = rememberReducedMotion()
    var shown by remember { mutableStateOf(items.map { LeavingItem(it, true) }) }
    LaunchedEffect(items, reducedMotion) {
        if (reducedMotion) {
            shown = items.map { LeavingItem(it, true) }
            return@LaunchedEffect
        }
        val newKeys = items.map(key).toSet()
        val merged = items.map { LeavingItem(it, true) }.toMutableList()
        // Las que ya no están se quedan en su sitio, marcadas como «saliendo».
        shown.forEachIndexed { index, old ->
            if (key(old.item) !in newKeys) merged.add(index.coerceAtMost(merged.size), LeavingItem(old.item, false))
        }
        shown = merged
        if (merged.any { !it.present }) {
            delay(holdMillis)
            shown = items.map { LeavingItem(it, true) }
        }
    }
    return shown
}
