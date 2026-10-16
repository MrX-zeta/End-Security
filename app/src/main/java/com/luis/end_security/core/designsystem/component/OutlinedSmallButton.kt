package com.luis.end_security.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.luis.end_security.core.designsystem.theme.PreviewSurface

private val OutlinedSmallButtonShape = RoundedCornerShape(14.dp)

/**
 * Botón de contorno pequeño, como «Omitir» en el onboarding: alto visual 44, radio 14,
 * borde de 1 px con el token `outline`. No usa amarillo porque es una acción secundaria.
 * El área táctil se amplía a 48 dp (Surface aplica minimumInteractiveComponentSize).
 */
@Composable
fun OutlinedSmallButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = OutlinedSmallButtonShape,
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Box(
            modifier = Modifier
                .height(44.dp)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = text, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

@Preview(name = "Botón de contorno pequeño")
@Composable
private fun OutlinedSmallButtonPreview() {
    PreviewSurface(darkTheme = true, modifier = Modifier.padding(24.dp)) {
        OutlinedSmallButton(text = "Omitir", onClick = {})
    }
}
