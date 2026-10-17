package com.luis.end_security.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.luis.end_security.core.designsystem.theme.EndSecurityTheme
import com.luis.end_security.core.designsystem.theme.PreviewSurface

const val StrengthMeterBars = 4
private val BarShape = RoundedCornerShape(3.dp)

/**
 * Medidor de fuerza de Figma (nodo 36:221): 4 barras de 52 × 6 con 6 de separación y la
 * etiqueta en SemiBold 13, por defecto del mismo color que las barras llenas. Las vacías van
 * en `outline` al 30 %. Es información, no acción, así que nunca usa amarillo (DEC-50).
 * TalkBack lee solo la etiqueta y la anuncia cuando cambia.
 */
@Composable
fun StrengthMeter(
    filledBars: Int,
    color: Color,
    label: String?,
    modifier: Modifier = Modifier,
    labelColor: Color = color,
) {
    val emptyColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    Row(
        modifier = modifier
            .heightIn(min = 18.dp)
            .clearAndSetSemantics {
                if (label != null) {
                    contentDescription = label
                    liveRegion = LiveRegionMode.Polite
                }
            },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(StrengthMeterBars) { index ->
            Box(
                modifier = Modifier
                    .size(width = 52.dp, height = 6.dp)
                    .background(if (index < filledBars) color else emptyColor, BarShape),
            )
        }
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = labelColor,
                maxLines = 1,
            )
        }
    }
}

@Preview(name = "Medidor de fuerza")
@Composable
private fun StrengthMeterPreview() {
    PreviewSurface(modifier = Modifier.padding(24.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            StrengthMeter(filledBars = 0, color = MaterialTheme.colorScheme.outline, label = null)
            StrengthMeter(filledBars = 1, color = MaterialTheme.colorScheme.error, label = "Débil")
            StrengthMeter(filledBars = 2, color = MaterialTheme.colorScheme.onSurfaceVariant, label = "Regular")
            StrengthMeter(filledBars = 3, color = EndSecurityTheme.extendedColors.success, label = "Fuerte")
            StrengthMeter(filledBars = 4, color = EndSecurityTheme.extendedColors.success, label = "Muy fuerte")
        }
    }
}
