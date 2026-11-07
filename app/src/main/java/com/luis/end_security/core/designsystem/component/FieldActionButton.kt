package com.luis.end_security.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

private val FieldActionShape = RoundedCornerShape(12.dp)

/**
 * Botón de ícono amarillo (acción, DEC-50) al final de un campo: 40 × 40 a la vista y 48 de área
 * táctil. Es el ojo de [PasswordField], el copiar y el abrir del detalle (SCR-12).
 */
@Composable
fun FieldActionButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** Si es un botón de copiar: su ícono pasa a palomita un momento y anuncia «Copiado» (tarea 29). */
    confirmation: CopyConfirmation? = null,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.alpha(if (enabled) 1f else 0.4f).then(if (confirmation != null) Modifier.copiedSemantics(confirmation) else Modifier),
        shape = FieldActionShape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            if (confirmation != null) {
                CopyIcon(confirmation, contentDescription, copyIcon = icon)
            } else {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = contentDescription,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
