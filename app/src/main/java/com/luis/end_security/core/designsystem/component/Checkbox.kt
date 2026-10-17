package com.luis.end_security.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.luis.end_security.R
import com.luis.end_security.core.designsystem.theme.PreviewSurface

private val CheckboxShape = RoundedCornerShape(7.dp)

/**
 * Casilla de Figma («Casilla», nodo 116:3710): 24 × 24, radio 7. Marcada lleva relleno
 * `primary` y la palomita en `on-primary`; sin marcar, solo el borde `outline`.
 * El área táctil es de 48 × 48. [label] es lo que TalkBack lee junto con el estado.
 */
@Composable
fun Checkbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(if (checked) MaterialTheme.colorScheme.primary else Color.Transparent, CheckboxShape)
                .then(
                    if (checked) Modifier else Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CheckboxShape),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(
                    painter = painterResource(R.drawable.ic_check_16),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Preview(name = "Casilla")
@Composable
private fun CheckboxPreview() {
    PreviewSurface(modifier = Modifier.padding(16.dp)) {
        Row {
            Checkbox(checked = true, onCheckedChange = {}, label = "Marcada")
            Checkbox(checked = false, onCheckedChange = {}, label = "Sin marcar")
        }
    }
}
