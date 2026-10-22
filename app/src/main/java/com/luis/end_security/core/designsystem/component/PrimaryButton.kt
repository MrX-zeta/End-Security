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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
 * Con [showArrow] en `false` no lleva la flecha («Guardar», «Copiar contraseña» en Figma).
 * Con [loading] muestra un indicador en lugar de la flecha y no responde a toques, sin atenuarse:
 * la acción sigue en curso (p. ej. Argon2 y la llamada al servidor).
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    showArrow: Boolean = true,
) {
    val shape = MaterialTheme.shapes.medium
    val loadingDescription = stringResource(R.string.button_loading)
    Surface(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier
            .alpha(if (enabled || loading) 1f else DisabledAlpha)
            .semantics { if (loading) stateDescription = loadingDescription }
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
            if (!showArrow && !loading) return@Row
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
            } else {
                // Decorativa: el texto del botón ya dice qué hace.
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_right_20),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }
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
            PrimaryButton(text = "Crear cuenta", onClick = {}, loading = true, modifier = Modifier.fillMaxWidth())
        }
    }
}
