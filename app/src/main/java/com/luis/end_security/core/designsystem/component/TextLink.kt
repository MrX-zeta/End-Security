package com.luis.end_security.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.luis.end_security.core.designsystem.theme.PreviewSurface

/**
 * Enlace de texto: subrayado porque comparte color con el texto (DEC-50), SemiBold 15 y
 * un área táctil de al menos 48 dp de alto.
 */
@Composable
fun TextLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textDecoration = TextDecoration.Underline,
        )
    }
}

@Preview(name = "Enlace de texto")
@Composable
private fun TextLinkPreview() {
    PreviewSurface(darkTheme = true) {
        TextLink(text = "Ya tengo una cuenta", onClick = {}, modifier = Modifier.fillMaxWidth())
    }
}
