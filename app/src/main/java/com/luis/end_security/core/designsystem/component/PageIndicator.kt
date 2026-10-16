package com.luis.end_security.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.luis.end_security.R
import com.luis.end_security.core.designsystem.theme.PreviewSurface
import com.luis.end_security.core.designsystem.theme.rememberReducedMotion

private val DotSize = 8.dp
private val ActiveDotWidth = 24.dp
private val DotShape = RoundedCornerShape(4.dp)

/**
 * Indicador de páginas de Figma: la página activa es una píldora amarilla de 24 × 8
 * (elemento activo, DEC-50) y las demás son puntos de 8 × 8. TalkBack lo lee como
 * «Página X de Y». Con «Quitar animaciones» cambia sin transición.
 */
@Composable
fun PageIndicator(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.page_indicator_description, currentPage + 1, pageCount)
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { page ->
            PageDot(active = page == currentPage)
        }
    }
}

@Composable
private fun PageDot(active: Boolean) {
    val reducedMotion = rememberReducedMotion()
    val width: Dp by animateDpAsState(
        targetValue = if (active) ActiveDotWidth else DotSize,
        animationSpec = if (reducedMotion) snap() else spring(),
        label = "anchoPunto",
    )
    val color by animateColorAsState(
        targetValue = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        animationSpec = if (reducedMotion) snap() else spring(),
        label = "colorPunto",
    )
    Box(
        modifier = Modifier
            .height(DotSize)
            .width(width)
            .background(color, DotShape),
    )
}

@Preview(name = "Indicador de páginas")
@Composable
private fun PageIndicatorPreview() {
    PreviewSurface(darkTheme = true, modifier = Modifier.padding(24.dp)) {
        PageIndicator(pageCount = 3, currentPage = 0)
    }
}
