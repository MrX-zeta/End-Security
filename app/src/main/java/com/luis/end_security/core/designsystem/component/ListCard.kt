package com.luis.end_security.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/** «Grupo» de filas de Figma (47:1900): tarjeta blanca de radio 20 con 12 de margen lateral y 4 arriba y abajo. */
@Composable
fun ListCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) { content() }
}

/** «Divisor» de Figma: `outline` al 25 %, alineado con el texto (pasa por debajo del ícono de 40 + 14 de separación). */
@Composable
fun ListRowDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), modifier = Modifier.padding(start = 56.dp))
}

/** Ícono de fila: tesela de 40 con radio 12 y el ícono de 20 (en `error` si [danger]). */
@Composable
fun ListRowIcon(@DrawableRes icon: Int, danger: Boolean) {
    Box(
        modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.background, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(20.dp),
        )
    }
}
