package com.luis.end_security.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.luis.end_security.core.designsystem.theme.PreviewSurface

/**
 * «Botón/Secundario» de Figma (nodo 35:374): alto 56, radio 16, borde de 1.5 px y texto Bold
 * centrado, todo en `on-surface`. Para la acción que no es la principal, como «Cancelar».
 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BorderedButton(text = text, color = MaterialTheme.colorScheme.onSurface, onClick = onClick, modifier = modifier)
}

/**
 * «Botón/Peligro» de Figma (nodo 35:376): como [SecondaryButton] pero en `danger`
 * (`colorScheme.error`). Para acciones que no se deshacen, como «Cerrar sesión».
 */
@Composable
fun DangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BorderedButton(text = text, color = MaterialTheme.colorScheme.error, onClick = onClick, modifier = modifier)
}

@Composable
private fun BorderedButton(
    text: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = Color.Transparent,
        contentColor = color,
        border = BorderStroke(1.5.dp, color),
    ) {
        Box(
            modifier = Modifier
                .height(56.dp)
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Preview(name = "Botones secundario y de peligro")
@Composable
private fun BorderedButtonsPreview() {
    PreviewSurface {
        Column(modifier = Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            DangerButton(text = "Cerrar sesión", onClick = {}, modifier = Modifier.fillMaxWidth())
            SecondaryButton(text = "Cancelar", onClick = {}, modifier = Modifier.fillMaxWidth())
        }
    }
}
