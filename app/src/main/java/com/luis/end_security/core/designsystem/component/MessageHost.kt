package com.luis.end_security.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.luis.end_security.core.designsystem.theme.Motion
import com.luis.end_security.core.designsystem.theme.rememberReducedMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Cuánto dura un aviso que se pide con [MessageHostState.post] (los de [HostedMessage] duran lo que dure quien los pidió). */
const val MessageDefaultMillis = 4_000L

/** Un aviso del anfitrión. Su texto y su acción cambian en su lugar (p. ej. la cuenta regresiva de «Copiada · 30 s») sin repetir la entrada. */
@Stable
class MessageEntry internal constructor() {
    var text by mutableStateOf("")
    var icon by mutableIntStateOf(0)
    var actionLabel by mutableStateOf<String?>(null)
    var onAction by mutableStateOf<(() -> Unit)?>(null)
}

/**
 * Los avisos de la app, en un solo lugar (tarea 26). El anfitrión ([MessageHost]) los dibuja **encima de la barra inferior** (o del
 * borde, si no hay barra) y **nunca encima del teclado**; las pantallas piden el aviso aquí en lugar de dibujar el suyo.
 *
 * Se muestra el más reciente. Si llega otro mientras hay uno, el actual sale y el nuevo entra después, sin amontonarse; al irse el nuevo
 * vuelve el anterior si sigue pedido.
 */
@Stable
class MessageHostState internal constructor(private val scope: CoroutineScope) {
    internal val entries = mutableStateListOf<MessageEntry>()
    private val timers = mutableMapOf<MessageEntry, Job>()

    /** El aviso que se ve ahora: el más reciente. */
    val current: MessageEntry? get() = entries.lastOrNull()

    /** Cuántos avisos hay pedidos; para pruebas. */
    val count: Int get() = entries.size

    /**
     * Muestra [text] durante [durationMillis] (un resultado: «Se guardó», «Cambios guardados»). Con [actionLabel] y [onAction] lleva un
     * botón de texto («Deshacer»).
     */
    fun post(
        text: String,
        @DrawableRes icon: Int,
        durationMillis: Long = MessageDefaultMillis,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null,
    ) {
        val entry = MessageEntry().apply {
            this.text = text
            this.icon = icon
            this.actionLabel = actionLabel
            this.onAction = onAction
        }
        entries += entry
        timers[entry] = scope.launch {
            delay(durationMillis)
            entries -= entry
            timers -= entry
        }
    }

    /**
     * Quita todos los avisos y cancela sus relojes. Se llama al bloquear la bóveda o al eliminar la cuenta: un aviso con el nombre de
     * un elemento («Gmail se guardó») no debe verse sobre Desbloquear ni sobre Iniciar sesión.
     */
    fun clear() {
        timers.values.forEach { it.cancel() }
        timers.clear()
        entries.clear()
    }
}

@Composable
fun rememberMessageHostState(): MessageHostState {
    val scope = rememberCoroutineScope()
    return remember { MessageHostState(scope) }
}

/** La da `AppNavHost`; sin ella (vistas previas y pruebas de una pantalla sola) [HostedMessage] dibuja el aviso donde se le pide. */
val LocalMessageHost = staticCompositionLocalOf<MessageHostState?> { null }

/**
 * Pide un aviso al anfitrión **mientras esta función esté en la composición** (un aviso que depende del estado: «Copiada · 30 s»,
 * «Gmail se eliminó · Deshacer»). Al dejar de llamarla el aviso sale. Cambiar [text] o [actionLabel] lo actualiza en su lugar.
 *
 * Sin anfitrión (pruebas y vistas previas de una pantalla sola) se dibuja un [MessageBar] aquí mismo, con [fallbackModifier].
 */
@Composable
fun HostedMessage(
    text: String,
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val host = LocalMessageHost.current
    if (host == null) {
        MessageBar(text = text, icon = icon, modifier = modifier, actionLabel = actionLabel, onAction = onAction)
        return
    }
    val entry = remember { MessageEntry() }
    SideEffect {
        entry.text = text
        entry.icon = icon
        entry.actionLabel = actionLabel
        entry.onAction = onAction
    }
    DisposableEffect(host) {
        host.entries += entry
        onDispose { host.entries -= entry }
    }
}

/**
 * Dibuja el aviso actual con su movimiento: **entra subiendo [Motion.MessageRiseIn] y apareciendo** en [Motion.MessageIn] ms y **sale
 * bajando [Motion.MessageDropOut] y desvaneciéndose** en [Motion.MessageOut] ms (la salida es más rápida). Si cambia de aviso, el
 * nuevo espera a que salga el anterior. Con «Quitar animaciones», cambio directo. TalkBack lo anuncia (región viva) como siempre.
 */
@Composable
fun MessageHost(
    state: MessageHostState,
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = rememberReducedMotion(),
    /** Separación bajo el aviso (solo mientras hay uno): 12 dp entre el aviso y el botón de una hoja. */
    bottomPadding: Dp = 0.dp,
    /** Relleno a los lados: 32 dp en la ventana principal; 0 dentro de una hoja cuyo contenido ya lo tiene. */
    horizontalPadding: Dp = 32.dp,
) {
    val density = LocalDensity.current
    val risePx = with(density) { Motion.MessageRiseIn.roundToPx() }
    val dropPx = with(density) { Motion.MessageDropOut.roundToPx() }
    Box(modifier = modifier.fillMaxWidth().padding(horizontal = horizontalPadding)) {
        AnimatedContent(
            targetState = state.current,
            modifier = Modifier.fillMaxWidth(),
            // Pegado al borde de abajo: aunque el contenedor cambie de tamaño al entrar o salir, el aviso no salta.
            contentAlignment = Alignment.BottomCenter,
            transitionSpec = {
                if (reducedMotion) {
                    (EnterTransition.None togetherWith ExitTransition.None) using SizeTransform(clip = false) { _, _ -> snap() }
                } else {
                    // Sale el anterior y después entra el nuevo: no se amontonan.
                    val wait = if (initialState != null && targetState != null) Motion.MessageOut else 0
                    (
                        fadeIn(tween(Motion.MessageIn, delayMillis = wait, easing = Motion.EaseOut)) +
                            slideInVertically(tween(Motion.MessageIn, delayMillis = wait, easing = Motion.EaseOut)) { risePx }
                        ) togetherWith (
                        fadeOut(tween(Motion.MessageOut, easing = Motion.EaseOut)) +
                            slideOutVertically(tween(Motion.MessageOut, easing = Motion.EaseOut)) { dropPx }
                        ) using SizeTransform(clip = false) { _, _ -> snap() }
                }
            },
            label = "messageHost",
        ) { entry ->
            if (entry != null) {
                MessageBar(
                    text = entry.text,
                    icon = entry.icon,
                    actionLabel = entry.actionLabel,
                    onAction = entry.onAction,
                    modifier = Modifier.padding(bottom = bottomPadding),
                )
            }
        }
    }
}
