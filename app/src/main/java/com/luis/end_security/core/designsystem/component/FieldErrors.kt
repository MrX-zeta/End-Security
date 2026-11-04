package com.luis.end_security.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.luis.end_security.R
import com.luis.end_security.core.designsystem.theme.EndSecurityTheme
import com.luis.end_security.core.designsystem.theme.PreviewSurface

/**
 * Mensaje de error de un campo que no es de texto (casillas, códigos): ícono de alerta + texto en `danger`, anunciado en
 * cuanto aparece (Figma «Error · Términos», 210:3868). Nunca solo color.
 */
@Composable
fun FieldErrorText(text: String, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.error
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_alert_20),
            contentDescription = null,
            tint = color,
            modifier = Modifier.padding(top = 1.dp).size(16.dp),
        )
        Text(text = text, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium), color = color)
    }
}

/**
 * Avisa cuando el foco **sale** del campo (tuvo foco y ya no lo tiene): el momento en que, si tiene texto y está mal, se
 * muestra su error («al salir del campo», tarea 15). Nunca dispara mientras se escribe por primera vez.
 */
fun Modifier.onFocusLost(onLost: () -> Unit): Modifier = composed {
    var hadFocus by remember { mutableStateOf(false) }
    onFocusChanged { focus ->
        if (hadFocus && !focus.hasFocus) onLost()
        hadFocus = focus.hasFocus
    }
}

/** Estado de un requisito: todavía sin evaluar (gris), cumplido (palomita `success`) o que falta (tache `danger`). */
enum class RequirementState { Neutral, Met, Unmet }

/**
 * Una línea de la lista de requisitos de la contraseña (Figma 210:3843): ícono + texto, nunca solo color. TalkBack lee
 * el estado («cumplido» o «falta») junto con el texto.
 */
@Composable
fun RequirementLine(text: String, state: RequirementState, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val color = when (state) {
        RequirementState.Neutral -> colors.onSurfaceVariant
        RequirementState.Met -> EndSecurityTheme.extendedColors.success
        RequirementState.Unmet -> colors.error
    }
    val icon = when (state) {
        RequirementState.Neutral -> R.drawable.ic_circle_16
        RequirementState.Met -> R.drawable.ic_check_16
        RequirementState.Unmet -> R.drawable.ic_x_16
    }
    val status = stringResource(
        when (state) {
            RequirementState.Neutral -> R.string.requirement_pending
            RequirementState.Met -> R.string.requirement_met
            RequirementState.Unmet -> R.string.requirement_unmet
        },
    )
    Row(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = "$text, $status" },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painter = painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Text(text = text, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium), color = color)
    }
}

@Preview(name = "Errores de campo")
@Composable
private fun FieldErrorsPreview() {
    PreviewSurface(modifier = Modifier.padding(24.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldErrorText("Acepta los Términos y el Aviso de privacidad para continuar.")
            RequirementLine("12 caracteres o más", RequirementState.Met)
            RequirementLine("Fuerza buena o mejor", RequirementState.Unmet)
            RequirementLine("Las contraseñas coinciden", RequirementState.Neutral)
        }
    }
}
