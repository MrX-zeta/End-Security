package com.luis.end_security.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luis.end_security.core.designsystem.theme.EndSecurityTheme
import com.luis.end_security.core.designsystem.theme.PreviewSurface

/**
 * Cuadro con la inicial en `primary`: el avatar de la cuenta (44, SCR-10), la inicial de cada
 * elemento de la lista (44, radio 13) y la del detalle (grande). Es decorativo: el nombre ya
 * está escrito al lado.
 */
@Composable
fun LetterTile(
    letter: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    cornerRadius: Dp = 13.dp,
    container: Color = EndSecurityTheme.extendedColors.blob,
    textStyle: TextStyle = MaterialTheme.typography.titleMedium,
) {
    Box(
        modifier = modifier
            .size(size)
            .background(container, RoundedCornerShape(cornerRadius)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = letter,
            style = textStyle.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** La inicial de [name] en mayúscula, o `?` si está vacío. Cuenta puntos de código, no unidades UTF-16. */
fun initialOf(name: String): String {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return "?"
    return String(Character.toChars(trimmed.codePointAt(0))).uppercase()
}

@Preview(name = "Inicial")
@Composable
private fun LetterTilePreview() {
    PreviewSurface(modifier = Modifier.padding(24.dp)) {
        LetterTile(letter = "A", textStyle = TextStyle(fontSize = 18.sp))
    }
}
