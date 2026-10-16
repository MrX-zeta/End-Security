package com.luis.end_security.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.luis.end_security.R
import com.luis.end_security.core.designsystem.theme.EndSecurityTheme
import com.luis.end_security.core.designsystem.theme.PreviewSurface

private val DiscSize = 112.dp
private val DiscIconSize = 40.dp
private val ShadowOffset = 8.dp
private val ShadowBlur = 20.dp

/**
 * Disco neumórfico de 112 dp para los íconos destacados (DEC-50): mismo fondo que la
 * pantalla, luz arriba-izquierda y sombra abajo-derecha (desplazamiento 8, difuminado 20),
 * sin brillo interior para que no parezca un botón. El ícono es amarillo de 40 dp.
 */
@Composable
fun NeumorphicDisc(
    @DrawableRes icon: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val extended = EndSecurityTheme.extendedColors
    Box(
        modifier = modifier
            .size(DiscSize)
            .dropShadow(
                shape = CircleShape,
                shadow = Shadow(
                    radius = ShadowBlur,
                    color = extended.shadowLight,
                    offset = DpOffset(-ShadowOffset, -ShadowOffset),
                ),
            )
            .dropShadow(
                shape = CircleShape,
                shadow = Shadow(
                    radius = ShadowBlur,
                    color = extended.shadowDark,
                    offset = DpOffset(ShadowOffset, ShadowOffset),
                ),
            )
            .background(MaterialTheme.colorScheme.background, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(DiscIconSize),
        )
    }
}

@Preview(name = "Disco neumórfico")
@Composable
private fun NeumorphicDiscPreview() {
    PreviewSurface(darkTheme = true, modifier = Modifier.padding(40.dp)) {
        NeumorphicDisc(icon = R.drawable.ic_key_40, contentDescription = null)
    }
}
