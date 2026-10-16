package com.luis.end_security.core.designsystem.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import com.luis.end_security.core.designsystem.theme.PreviewSurface

/**
 * Título en dos pesos (DEC-50): primera línea Montserrat Bold y segunda Regular,
 * con interletrado de −2 % (headlineLarge). TalkBack lo anuncia como encabezado.
 */
@Composable
fun TwoWeightTitle(
    boldLine: String,
    regularLine: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    val text = remember(boldLine, regularLine) {
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(boldLine) }
            append('\n')
            append(regularLine)
        }
    }
    Text(
        text = text,
        style = MaterialTheme.typography.headlineLarge,
        color = color,
        modifier = modifier.semantics { heading() },
    )
}

@Preview(name = "Título en dos pesos")
@Composable
private fun TwoWeightTitlePreview() {
    PreviewSurface(darkTheme = true, modifier = Modifier.padding(32.dp)) {
        TwoWeightTitle(boldLine = "Tus contraseñas", regularLine = "en un solo lugar")
    }
}
