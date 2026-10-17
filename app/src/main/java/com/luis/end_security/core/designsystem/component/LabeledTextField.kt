package com.luis.end_security.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.luis.end_security.R
import com.luis.end_security.core.designsystem.theme.EndSecurityTheme
import com.luis.end_security.core.designsystem.theme.PreviewSurface

// Medidas de Figma («Campo · Correo», nodo 19:176): etiqueta 14 SemiBold, 8 de separación,
// contenedor de 56 de alto con 18 de margen interno y 12 entre ícono y texto.
private val FieldHeight = 56.dp
internal val FieldStartPadding = 18.dp
private val FieldIconSize = 20.dp

/**
 * Campo de texto con etiqueta arriba e ícono al inicio, para datos que no son secretos
 * (para la contraseña maestra usa [PasswordField]). Superficie `surface`, borde `outline`
 * de 1 px, radio 16 y sombra suave (DEC-50).
 */
@Composable
fun LabeledTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    @DrawableRes leadingIcon: Int,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    isError: Boolean = false,
    contentType: ContentType? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    FieldLayout(label = label, supportingText = supportingText, isError = isError, modifier = modifier) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = fieldTextStyle(),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            modifier = Modifier
                .fillMaxWidth()
                .fieldSemantics(label, supportingText, isError, contentType),
            decorationBox = { innerTextField ->
                FieldContainer(leadingIcon = leadingIcon, endPadding = FieldStartPadding) {
                    innerTextField()
                }
            },
        )
    }
}

/**
 * Variante con [TextFieldState] para un secreto que se ve mientras se escribe (como el código de
 * respaldo): el texto vive en el estado del campo y no pasa por el estado de la pantalla.
 * [inputTransformation] filtra lo que se escribe y [outputTransformation] solo cambia cómo se ve
 * (p. ej. el guion de «XXXX-XXXX»), sin guardarlo.
 */
@Composable
fun LabeledTextField(
    state: TextFieldState,
    label: String,
    @DrawableRes leadingIcon: Int,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    isError: Boolean = false,
    inputTransformation: InputTransformation? = null,
    outputTransformation: OutputTransformation? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onKeyboardAction: KeyboardActionHandler? = null,
) {
    FieldLayout(label = label, supportingText = supportingText, isError = isError, modifier = modifier) {
        BasicTextField(
            state = state,
            inputTransformation = inputTransformation,
            outputTransformation = outputTransformation,
            lineLimits = TextFieldLineLimits.SingleLine,
            textStyle = fieldTextStyle(),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
            keyboardOptions = keyboardOptions,
            onKeyboardAction = onKeyboardAction,
            modifier = Modifier
                .fillMaxWidth()
                .fieldSemantics(label, supportingText, isError, contentType = null),
            decorator = TextFieldDecorator { innerTextField ->
                FieldContainer(leadingIcon = leadingIcon, endPadding = FieldStartPadding) {
                    innerTextField()
                }
            },
        )
    }
}

/** Texto del campo: 15/24 Medium, como el valor escrito en Figma. */
@Composable
internal fun fieldTextStyle(): TextStyle = MaterialTheme.typography.bodyLarge.copy(
    fontWeight = FontWeight.Medium,
    color = MaterialTheme.colorScheme.onSurface,
)

/**
 * La etiqueta visible no se lee por separado: se vuelve la descripción del campo, para que
 * TalkBack diga «Correo, cuadro de edición». El error se anuncia como error del campo.
 */
internal fun Modifier.fieldSemantics(
    label: String,
    supportingText: String?,
    isError: Boolean,
    contentType: ContentType?,
): Modifier = semantics {
    contentDescription = label
    if (isError && supportingText != null) error(supportingText)
    if (contentType != null) this.contentType = contentType
}

/** Etiqueta, campo y línea de ayuda o de error. Compartido con [PasswordField]. */
@Composable
internal fun FieldLayout(
    label: String,
    supportingText: String?,
    isError: Boolean,
    modifier: Modifier = Modifier,
    field: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.clearAndSetSemantics {},
        )
        field()
        if (supportingText != null) {
            SupportingText(text = supportingText, isError = isError)
        }
    }
}

/** Contenedor visual del campo: superficie, borde, sombra, ícono al inicio y extremo opcional. */
@Composable
internal fun FieldContainer(
    @DrawableRes leadingIcon: Int,
    endPadding: Dp,
    trailing: (@Composable () -> Unit)? = null,
    input: @Composable () -> Unit,
) {
    val shape = MaterialTheme.shapes.medium
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = FieldHeight)
            .dropShadow(
                shape = shape,
                shadow = Shadow(
                    radius = 20.dp,
                    color = EndSecurityTheme.extendedColors.shadowDark,
                    offset = DpOffset(x = 0.dp, y = 8.dp),
                ),
            )
            .background(MaterialTheme.colorScheme.surface, shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(start = FieldStartPadding, end = endPadding),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Decorativo: la etiqueta ya dice qué va en el campo.
        Icon(
            painter = painterResource(leadingIcon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(FieldIconSize),
        )
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            input()
        }
        trailing?.invoke()
    }
}

/**
 * Línea bajo el campo con el ícono de información: ayuda en `on-surface-variant` o error en
 * `error` (danger). El error se anuncia en cuanto aparece.
 */
@Composable
private fun SupportingText(text: String, isError: Boolean) {
    val color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isError) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_info_16),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp),
        )
        Text(text = text, style = MaterialTheme.typography.bodySmall, color = color)
    }
}

@Preview(name = "Campo con etiqueta")
@Composable
private fun LabeledTextFieldPreview() {
    PreviewSurface(modifier = Modifier.padding(32.dp)) {
        LabeledTextField(
            value = "ana.lopez@correo.com",
            onValueChange = {},
            label = "Correo",
            leadingIcon = R.drawable.ic_mail_20,
        )
    }
}
