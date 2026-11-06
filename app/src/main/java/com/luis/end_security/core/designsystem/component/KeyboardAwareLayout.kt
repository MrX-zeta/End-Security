package com.luis.end_security.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp

/** Distancia entre el borde inferior del bloque principal (su botón principal) y el teclado cuando hay que subirlo (tarea 24). */
val KeyboardMargin = 16.dp

/**
 * Coloca una cabecera decorativa, un bloque principal (cuyo último elemento es el botón principal) y un pie **a la par del teclado**.
 * Lee `WindowInsets.ime` en la fase de colocación, que Android 11+ actualiza cuadro por cuadro durante la animación del teclado
 * (con `enableEdgeToEdge` / `setDecorFitsSystemWindows(false)`): no hay animación propia ni saltos al final.
 *
 * - **Sin teclado** todo queda en su lugar natural: cabecera arriba, bloque debajo y pie al fondo ([pinFooterToBottom]) o justo
 *   debajo del bloque.
 * - **Con teclado** el borde inferior del bloque queda a [KeyboardMargin] del teclado **solo si hace falta**; si ya queda por encima,
 *   no se mueve nada.
 * - **Lo que no cabe se va en orden:** primero la cabecera (sube y sale de la pantalla, con una franja del color de fondo sobre la
 *   barra de estado) y, si aún falta, el bloque sube lo justo. El pie queda detrás del teclado y vuelve al cerrarlo.
 */
@Composable
fun KeyboardAwareLayout(
    header: @Composable () -> Unit,
    main: @Composable () -> Unit,
    footer: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    /** `true`: el pie queda al fondo de la pantalla; `false`: justo debajo del bloque principal (con [FooterGap] de separación). */
    pinFooterToBottom: Boolean = true,
) {
    val ime = WindowInsets.ime
    val statusBars = WindowInsets.statusBars
    val navigationBars = WindowInsets.navigationBars
    val background = MaterialTheme.colorScheme.background
    Layout(
        content = { header(); main(); footer(); Box(Modifier.background(background)) },
        modifier = modifier.fillMaxSize(),
    ) { measurables, constraints ->
        val child = Constraints(minWidth = constraints.maxWidth, maxWidth = constraints.maxWidth)
        val headerPlaceable = measurables[0].measure(child)
        val mainPlaceable = measurables[1].measure(child)
        val footerPlaceable = measurables[2].measure(child)
        // Franja del color de fondo sobre la barra de estado: aparece cuando la cabecera se va, para que las manchas no queden
        // debajo de los íconos de la barra.
        val statusHeight = statusBars.getTop(this)
        val maskPlaceable = measurables[3].measure(Constraints.fixed(constraints.maxWidth, statusHeight))
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        layout(width, height) {
            val imeBottom = ime.getBottom(this@Layout)
            val margin = KeyboardMargin.roundToPx()
            val footerGap = FooterGap.roundToPx()
            // Hasta dónde puede llegar el borde inferior del bloque: sobre el teclado, o sobre el pie (que sigue al bloque o va al
            // fondo) cuando no hay teclado.
            val limit = when {
                imeBottom > 0 -> height - imeBottom - margin
                pinFooterToBottom -> height - footerPlaceable.height
                else -> height - navigationBars.getBottom(this@Layout) - footerPlaceable.height - footerGap - FooterBottomSpace.roundToPx()
            }
            val naturalBottom = headerPlaceable.height + mainPlaceable.height
            val overflow = (naturalBottom - limit).coerceAtLeast(0)
            val collapse = minOf(overflow, headerPlaceable.height)
            val mainY = headerPlaceable.height - overflow
            headerPlaceable.placeRelative(0, -collapse)
            mainPlaceable.placeRelative(0, mainY)
            val footerY = when {
                pinFooterToBottom -> height - footerPlaceable.height
                // Con teclado el pie (que va debajo del botón) queda detrás de él y vuelve al cerrarlo.
                imeBottom > 0 -> height
                else -> mainY + mainPlaceable.height + footerGap
            }
            footerPlaceable.placeRelative(0, footerY)
            maskPlaceable.placeRelative(0, if (overflow > 0) 0 else -statusHeight)
        }
    }
}

/** Separación entre el bloque principal y su pie cuando el pie lo sigue. */
private val FooterGap = 8.dp

/** Espacio que se deja debajo del pie cuando lo sigue. */
private val FooterBottomSpace = 24.dp
