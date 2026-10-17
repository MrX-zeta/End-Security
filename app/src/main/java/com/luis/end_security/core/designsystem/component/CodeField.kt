package com.luis.end_security.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.luis.end_security.R
import com.luis.end_security.core.designsystem.theme.EndSecurityTheme
import com.luis.end_security.core.designsystem.theme.PreviewSurface

/** Filtro del código: solo dígitos ASCII y como máximo [LENGTH]. Al pegar «123 456» queda «123456». */
object CodeFieldFilter {
    const val LENGTH = 6

    fun filter(input: String): String = input.filter { it in '0'..'9' }.take(LENGTH)
}

/**
 * Código de verificación de Figma («Código de 6 dígitos», nodo 36:314): un solo campo de texto,
 * dibujado como 6 casillas de 48 × 60 con radio 14. Se reutiliza en Verificar correo, 2FA y
 * Desbloquear.
 * - Teclado numérico (`NumberPassword`); el teclado puede sugerir el código del SMS o correo.
 * - La casilla activa lleva borde `on-surface` de 2 (Figma) y un cursor; con error, todas `error`.
 * - TalkBack lo lee como un solo campo: «Código de verificación, 3 de 6 dígitos».
 */
@Composable
fun CodeField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    onDone: () -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    val description = stringResource(R.string.code_field_description, value.length, CodeFieldFilter.LENGTH)
    BasicTextField(
        value = value,
        onValueChange = { onValueChange(CodeFieldFilter.filter(it)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .semantics {
                contentDescription = description
                contentType = ContentType.SmsOtpCode
            },
        // El texto real no se dibuja: cada dígito va en su casilla.
        decorationBox = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clearAndSetSemantics {},
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                repeat(CodeFieldFilter.LENGTH) { index ->
                    CodeCell(
                        digit = value.getOrNull(index),
                        active = focused && index == value.length.coerceAtMost(CodeFieldFilter.LENGTH - 1) &&
                            value.length < CodeFieldFilter.LENGTH,
                        isError = isError,
                    )
                }
            }
        },
    )
}

@Composable
private fun CodeCell(digit: Char?, active: Boolean, isError: Boolean) {
    val shape = MaterialTheme.shapes.small
    val colors = MaterialTheme.colorScheme
    val borderColor = when {
        isError -> colors.error
        active -> colors.onSurface
        else -> colors.outline
    }
    Box(
        modifier = Modifier
            .size(width = 48.dp, height = 60.dp)
            .dropShadow(
                shape = shape,
                shadow = Shadow(
                    radius = 16.dp,
                    color = EndSecurityTheme.extendedColors.shadowDark,
                    offset = DpOffset(x = 0.dp, y = 6.dp),
                ),
            )
            .background(colors.surface, shape)
            .border(if (active) 2.dp else 1.dp, borderColor, shape),
        contentAlignment = Alignment.Center,
    ) {
        when {
            digit != null -> Text(
                text = digit.toString(),
                style = MaterialTheme.typography.headlineSmall,
                color = colors.onSurface,
            )
            active -> Box(
                Modifier
                    .size(width = 2.dp, height = 24.dp)
                    .background(colors.onSurface),
            )
        }
    }
}

@Preview(name = "Campo de código")
@Composable
private fun CodeFieldPreview() {
    PreviewSurface(modifier = Modifier.padding(32.dp)) {
        Column {
            CodeField(value = "481", onValueChange = {})
            CodeField(value = "123456", onValueChange = {}, isError = true, modifier = Modifier.padding(top = 24.dp))
        }
    }
}
