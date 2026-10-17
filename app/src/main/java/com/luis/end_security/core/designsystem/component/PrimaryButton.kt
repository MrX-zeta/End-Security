package com.luis.end_security.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.luis.end_security.R
import com.luis.end_security.core.designsystem.theme.EndSecurityTheme
import com.luis.end_security.core.designsystem.theme.PreviewSurface

private const val DisabledAlpha = 0.4f

/**
 * Botón primario de Figma («Botón/Primario»): alto 56, radio 16, texto Bold a la izquierda
 * y flecha a la derecha, fondo amarillo de acción con texto carbón (DEC-50).
 * Sin ancho fijo: quien lo usa decide (`widthIn(min = 168.dp)` o `fillMaxWidth()`).
 * Deshabilitado baja su opacidad al 40 %: Figma no tiene ese estado (convención de Material).
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val shape = MaterialTheme.shapes.medium
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .alpha(if (enabled) 1f else DisabledAlpha)
            .dropShadow(
            shape = shape,
            shadow = Shadow(
                radius = 22.dp,
                color = EndSecurityTheme.extendedColors.shadowDark,
                offset = DpOffset(x = 0.dp, y = 10.dp),
            ),
        ),
        shape = shape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Row(
            modifier = Modifier
                .height(56.dp)
                .padding(start = 24.dp, end = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(16.dp))
            // Decorativa: el texto del botón ya dice qué hace.
            Icon(
                painter = painterResource(R.drawable.ic_arrow_right_20),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Preview(name = "Botón primario")
@Composable
private fun PrimaryButtonPreview() {
    PreviewSurface(darkTheme = true) {
        Column(
            modifier = Modifier.padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            PrimaryButton(text = "Siguiente", onClick = {}, modifier = Modifier.widthIn(min = 168.dp))
            PrimaryButton(text = "Comenzar", onClick = {}, modifier = Modifier.fillMaxWidth())
        }
    }
}
