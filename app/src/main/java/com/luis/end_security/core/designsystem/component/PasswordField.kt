package com.luis.end_security.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicSecureTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.luis.end_security.R
import com.luis.end_security.core.designsystem.theme.PreviewSurface

private val ToggleButtonShape = RoundedCornerShape(12.dp)

// El botón del ojo mide 40 a la vista pero ocupa 48 para el dedo; con 4 de margen,
// su borde visible queda a 8 del contenedor, como en Figma.
private val PasswordFieldEndPadding = 4.dp

/**
 * Campo de contraseña (DEC-50, regla no negociable 6): mismo marco que [LabeledTextField]
 * con un botón amarillo cuadrado para mostrar u ocultar. Con `onVisibleChange = null` no lleva
 * el botón (como «Confirma tu contraseña maestra» en Figma) y siempre está oculto.
 *
 * El texto vive en [state], que la pantalla crea con `remember { TextFieldState() }`:
 * nunca `rememberTextFieldState()` ni `rememberSaveable`, que lo guardarían en el Bundle.
 * `BasicSecureTextField` además bloquea copiar y cortar.
 */
@Composable
fun PasswordField(
    state: TextFieldState,
    label: String,
    visible: Boolean,
    onVisibleChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    isError: Boolean = false,
    imeAction: ImeAction = ImeAction.Done,
    onKeyboardAction: KeyboardActionHandler? = null,
    maxLength: Int? = null,
) {
    FieldLayout(label = label, supportingText = supportingText, isError = isError, modifier = modifier) {
        // Sin botón del ojo no hay forma de ocultarla otra vez: siempre va oculta.
        val shown = visible && onVisibleChange != null
        val baseStyle = fieldTextStyle()
        // Los puntos llevan el interletrado de Figma (1.8 px a 15 px).
        val textStyle = if (shown) baseStyle else baseStyle.copy(letterSpacing = 0.12.em)
        BasicSecureTextField(
            state = state,
            inputTransformation = maxLength?.let { InputTransformation.maxLength(it) },
            textStyle = textStyle,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
            textObfuscationMode = if (shown) TextObfuscationMode.Visible else TextObfuscationMode.Hidden,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = imeAction,
            ),
            onKeyboardAction = onKeyboardAction,
            modifier = Modifier
                .fillMaxWidth()
                .fieldSemantics(label, supportingText, isError, contentType = null),
            decorator = TextFieldDecorator { innerTextField ->
                FieldContainer(
                    leadingIcon = R.drawable.ic_lock_20,
                    endPadding = if (onVisibleChange != null) PasswordFieldEndPadding else FieldStartPadding,
                    trailing = onVisibleChange?.let { onChange ->
                        { VisibilityToggle(visible = visible, onVisibleChange = onChange) }
                    },
                ) {
                    innerTextField()
                }
            },
        )
    }
}

/** Botón de ícono amarillo (acción, DEC-50) de 40 × 40 con área táctil de 48. */
@Composable
private fun VisibilityToggle(visible: Boolean, onVisibleChange: (Boolean) -> Unit) {
    Surface(
        onClick = { onVisibleChange(!visible) },
        shape = ToggleButtonShape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(if (visible) R.drawable.ic_eye_off_20 else R.drawable.ic_eye_20),
                contentDescription = stringResource(
                    if (visible) R.string.password_hide else R.string.password_show,
                ),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Preview(name = "Campo de contraseña")
@Composable
private fun PasswordFieldPreview() {
    val hidden = remember { TextFieldState("contraseña de ejemplo") }
    val shown = remember { TextFieldState("contraseña de ejemplo") }
    PreviewSurface(modifier = Modifier.padding(32.dp)) {
        Column {
            PasswordField(
                state = hidden,
                label = "Contraseña maestra",
                visible = false,
                onVisibleChange = {},
                supportingText = "Si la olvidas, no podremos recuperarla.",
            )
            PasswordField(
                state = shown,
                label = "Contraseña maestra",
                visible = true,
                onVisibleChange = {},
                supportingText = "Correo o contraseña incorrectos.",
                isError = true,
                modifier = Modifier.padding(top = 24.dp),
            )
        }
    }
}
