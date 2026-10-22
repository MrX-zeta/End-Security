package com.luis.end_security.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.luis.end_security.R
import com.luis.end_security.core.designsystem.theme.PreviewSurface

/**
 * Campo de solo lectura del detalle (SCR-12): mismo marco que [LabeledTextField], con la etiqueta
 * arriba y, al final, un botón de acción amarillo opcional (copiar, ver, abrir).
 *
 * TalkBack lee «Etiqueta: valor». [displayText] es lo que se ve y [spokenText] lo que se lee: para una
 * contraseña oculta, los puntos se leen como «Contraseña oculta, 12 caracteres», no uno por uno.
 */
@Composable
fun ReadOnlyField(
    label: String,
    displayText: String,
    @DrawableRes leadingIcon: Int,
    modifier: Modifier = Modifier,
    spokenText: String = displayText,
    action: (@Composable () -> Unit)? = null,
) {
    FieldLayout(label = label, supportingText = null, isError = false, modifier = modifier) {
        FieldContainer(
            leadingIcon = leadingIcon,
            endPadding = if (action != null) FieldActionEndPadding else FieldStartPadding,
            trailing = action,
        ) {
            Text(
                text = displayText,
                style = fieldTextStyle(),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { contentDescription = "$label: $spokenText" },
            )
        }
    }
}

// Como en PasswordField: el botón mide 40 a la vista y 48 al tacto; con 4 de margen su borde queda a 8.
private val FieldActionEndPadding = 4.dp

@Preview(name = "Campo de solo lectura")
@Composable
private fun ReadOnlyFieldPreview() {
    PreviewSurface(modifier = Modifier.padding(32.dp)) {
        ReadOnlyField(
            label = "Usuario",
            displayText = "luis.ruiz@gmail.com",
            leadingIcon = R.drawable.ic_user_20,
            action = {
                FieldActionButton(icon = R.drawable.ic_copy_20, contentDescription = "Copiar usuario", onClick = {})
            },
        )
    }
}
