package com.luis.end_security.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.luis.end_security.core.designsystem.component.PageIndicator
import com.luis.end_security.core.designsystem.component.PrimaryButton
import com.luis.end_security.core.designsystem.component.TextLink
import com.luis.end_security.core.designsystem.component.TwoWeightTitle
import com.luis.end_security.core.designsystem.theme.EndSecurityTheme
import com.luis.end_security.core.designsystem.theme.PreviewSurface

// Catálogo del sistema de diseño solo para @Preview: tokens, tipografía y componentes base
// en el tema claro (app) y el oscuro (onboarding). Los nombres son los de las variables de Figma.

@Composable
private fun DesignSystemCatalog() {
    val colors = MaterialTheme.colorScheme
    val extended = EndSecurityTheme.extendedColors
    Column(
        modifier = Modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        TwoWeightTitle(boldLine = "Carbón", regularLine = "y miel")
        Text(
            text = "Texto de apoyo en on-surface-variant.",
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurfaceVariant,
        )

        SwatchRow(
            "background" to colors.background,
            "surface" to colors.surface,
            "outline" to colors.outline,
            "on-surface" to colors.onSurface,
        )
        SwatchRow(
            "on-surface-variant" to colors.onSurfaceVariant,
            "primary" to colors.primary,
            "on-primary" to colors.onPrimary,
            "blob" to extended.blob,
        )
        SwatchRow(
            "danger" to colors.error,
            "danger-container" to colors.errorContainer,
            "success" to extended.success,
            "success-container" to extended.successContainer,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PageIndicator(pageCount = 3, currentPage = 1)
            PrimaryButton(text = "Siguiente", onClick = {}, modifier = Modifier.widthIn(min = 168.dp))
        }
        PrimaryButton(text = "Comenzar", onClick = {}, modifier = Modifier.fillMaxWidth())
        TextLink(text = "Ya tengo una cuenta", onClick = {}, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun SwatchRow(vararg swatches: Pair<String, Color>) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        swatches.forEach { (name, color) ->
            Column(
                modifier = Modifier.widthIn(max = 72.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(48.dp)
                        .background(color, MaterialTheme.shapes.small)
                        .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small),
                )
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                )
            }
        }
    }
}

@Preview(name = "Sistema de diseño · claro", widthDp = 412)
@Composable
private fun DesignSystemLightPreview() {
    PreviewSurface(darkTheme = false) { DesignSystemCatalog() }
}

@Preview(name = "Sistema de diseño · oscuro (onboarding)", widthDp = 412)
@Composable
private fun DesignSystemDarkPreview() {
    PreviewSurface(darkTheme = true) { DesignSystemCatalog() }
}
