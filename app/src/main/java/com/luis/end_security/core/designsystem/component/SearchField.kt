package com.luis.end_security.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.luis.end_security.R
import com.luis.end_security.core.designsystem.theme.PreviewSurface

/**
 * «Buscador» de Figma (nodo 46:392): 52 de alto, ícono de lupa y texto de ayuda. Mismo marco que
 * [LabeledTextField]. Lo que se escribe no se guarda en ningún lado: vive en el estado de la
 * pantalla mientras está abierta.
 */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = fieldTextStyle(),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            // Lo que se busca puede ser un nombre o un sitio privado: sin corrector que lo aprenda.
            autoCorrectEnabled = false,
            imeAction = ImeAction.Search,
        ),
        modifier = modifier.semantics { contentDescription = placeholder },
        decorationBox = { innerTextField ->
            FieldContainer(
                leadingIcon = R.drawable.ic_search_20,
                endPadding = FieldStartPadding,
                minHeight = 52.dp,
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = fieldTextStyle(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                innerTextField()
            }
        },
    )
}

@Preview(name = "Buscador")
@Composable
private fun SearchFieldPreview() {
    PreviewSurface(modifier = Modifier.padding(32.dp)) {
        Box {
            SearchField(value = "", onValueChange = {}, placeholder = "Buscar por nombre o sitio")
        }
    }
}
