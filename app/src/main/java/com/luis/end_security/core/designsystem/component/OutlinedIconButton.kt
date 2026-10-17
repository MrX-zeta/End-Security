package com.luis.end_security.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.luis.end_security.R
import com.luis.end_security.core.designsystem.theme.PreviewSurface

private const val DisabledAlpha = 0.4f

/**
 * «Botón de ícono / Estilo=Contorno» de Figma (nodo 35:383): cuadro de 48 con radio 14, borde de
 * 1 px `outline` e ícono de 22 en `on-surface`. Desbloquear lo usa de 56 para la huella.
 * Deshabilitado baja su opacidad al 40 %, como el botón primario.
 */
@Composable
fun OutlinedIconButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    enabled: Boolean = true,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.alpha(if (enabled) 1f else DisabledAlpha),
        shape = MaterialTheme.shapes.small,
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(icon),
                contentDescription = contentDescription,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Preview(name = "Botón de ícono de contorno")
@Composable
private fun OutlinedIconButtonPreview() {
    PreviewSurface(modifier = Modifier.padding(24.dp)) {
        Row {
            OutlinedIconButton(icon = R.drawable.ic_fingerprint_22, contentDescription = "", onClick = {}, size = 56.dp)
            OutlinedIconButton(
                icon = R.drawable.ic_lock_20,
                contentDescription = "",
                onClick = {},
                enabled = false,
                modifier = Modifier.padding(start = 16.dp),
            )
        }
    }
}
