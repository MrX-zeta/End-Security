package com.luis.end_security.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.luis.end_security.R
import com.luis.end_security.core.designsystem.theme.Motion
import com.luis.end_security.core.designsystem.theme.rememberReducedMotion
import kotlinx.coroutines.delay

/**
 * La confirmación **en el propio botón de copiar** (tarea 29): además del aviso «Copiada · se borra del portapapeles en 30 s», el ícono
 * cambia a una palomita (fundido cruzado de [Motion.CopyIconFade] ms), se queda así [Motion.CopyConfirmHoldMillis] ms y vuelve al ícono
 * de copiar. Tocar otra vez mientras tanto vuelve a copiar y **reinicia** el tiempo.
 */
@Stable
class CopyConfirmation internal constructor() {
    internal var token by mutableIntStateOf(0)

    /** `true` mientras se muestra la palomita. */
    var active by mutableStateOf(false)
        internal set

    /** Llamar al copiar: muestra la palomita (o reinicia su tiempo si ya estaba). */
    fun mark() {
        token++
    }
}

@Composable
fun rememberCopyConfirmation(holdMillis: Long = Motion.CopyConfirmHoldMillis): CopyConfirmation {
    val confirmation = remember { CopyConfirmation() }
    LaunchedEffect(confirmation.token) {
        if (confirmation.token > 0) {
            confirmation.active = true
            delay(holdMillis)
            confirmation.active = false
        }
    }
    return confirmation
}

/**
 * El ícono de un botón de copiar: el de copiar, o la palomita mientras [confirmation] está activa, con fundido cruzado. Con «Quitar
 * animaciones» el cambio es instantáneo. [contentDescription] no cambia (el botón sigue llamándose igual); el estado «Copiado» se anuncia
 * aparte con [copiedSemantics].
 */
@Composable
fun CopyIcon(
    confirmation: CopyConfirmation,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    @DrawableRes copyIcon: Int = R.drawable.ic_copy_20,
    size: Dp = 20.dp,
    // Los íconos de la app son negros sobre el amarillo (`onPrimary`); los vectores son blancos, así que sin tinte se verían blancos.
    tint: Color = MaterialTheme.colorScheme.onPrimary,
    reducedMotion: Boolean = rememberReducedMotion(),
) {
    @Composable
    fun IconFor(done: Boolean) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(if (done) R.drawable.ic_check_16 else copyIcon),
                contentDescription = contentDescription,
                tint = tint,
                // La palomita es de 16 dp dentro del cuadro del ícono de copiar.
                modifier = if (done) Modifier.size(size * 0.8f) else Modifier.size(size),
            )
        }
    }
    if (reducedMotion) {
        // Sin animaciones: el cambio es instantáneo, un solo ícono a la vez.
        Box(modifier) { IconFor(confirmation.active) }
    } else {
        Crossfade(
            targetState = confirmation.active,
            modifier = modifier,
            animationSpec = tween(Motion.CopyIconFade, easing = Motion.EaseOut),
            label = "copyIcon",
        ) { done -> IconFor(done) }
    }
}

/** Anuncia «Copiado» (región viva) mientras la palomita está a la vista, sin cambiar el nombre del botón. */
@Composable
fun Modifier.copiedSemantics(confirmation: CopyConfirmation): Modifier {
    val copied = stringResource(R.string.copy_done)
    return this.semantics {
        liveRegion = LiveRegionMode.Polite
        if (confirmation.active) stateDescription = copied
    }
}
