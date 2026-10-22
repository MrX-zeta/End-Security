package com.luis.end_security.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.luis.end_security.R
import com.luis.end_security.core.designsystem.theme.PreviewSurface

/**
 * Aviso oscuro flotante de Figma (nodos 62:2946 «Copiada…» y 62:3009 «… se eliminó · Deshacer»):
 * fondo `on-surface`, radio 16, ícono y acción en `primary`. Se anuncia solo (región viva) cuando
 * aparece. La acción, si la hay, mide al menos 48 dp al tacto.
 */
@Composable
fun MessageBar(
    text: String,
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.onSurface, MaterialTheme.shapes.medium)
            .padding(start = 16.dp, end = if (actionLabel != null) 4.dp else 16.dp, top = 4.dp, bottom = 4.dp)
            .heightIn(min = 48.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 10.dp),
        )
        if (actionLabel != null && onAction != null) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    textDecoration = TextDecoration.Underline,
                ),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button, onClick = onAction)
                    .padding(horizontal = 12.dp)
                    .semantics(mergeDescendants = true) {},
                // Centrado vertical del texto dentro de los 48 dp.
            )
        }
    }
}

@Preview(name = "Aviso · copiada")
@Composable
private fun MessageBarCopiedPreview() {
    PreviewSurface(modifier = Modifier.padding(24.dp)) {
        MessageBar(text = "Copiada · se borra del portapapeles en 30 s", icon = R.drawable.ic_check_16)
    }
}

@Preview(name = "Aviso · eliminado")
@Composable
private fun MessageBarUndoPreview() {
    PreviewSurface(modifier = Modifier.padding(24.dp)) {
        MessageBar(
            text = "Gmail se eliminó",
            icon = R.drawable.ic_trash_20,
            actionLabel = "Deshacer",
            onAction = {},
        )
    }
}
