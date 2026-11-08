package com.luis.end_security.core.designsystem.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme

/** Envoltorio para los @Preview: aplica el tema y pinta su fondo (sin hex en los previews). */
@Composable
fun PreviewSurface(
    darkTheme: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    EndSecurityTheme(darkTheme = darkTheme) {
        Box(modifier.background(MaterialTheme.colorScheme.background)) {
            content()
        }
    }
}
