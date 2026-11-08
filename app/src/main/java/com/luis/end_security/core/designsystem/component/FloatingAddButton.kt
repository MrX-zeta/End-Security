package com.luis.end_security.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.luis.end_security.R
import com.luis.end_security.core.designsystem.theme.PreviewSurface

/** «Botón flotante» de Figma (nodo 46:484): 64 × 64, `primary`, con el «+» de 28. */
@Composable
fun FloatingAddButton(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shadowElevation = 8.dp,
    ) {
        Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(R.drawable.ic_plus_28),
                contentDescription = contentDescription,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Preview(name = "Botón flotante")
@Composable
private fun FloatingAddButtonPreview() {
    PreviewSurface(modifier = Modifier.padding(24.dp)) {
        FloatingAddButton(contentDescription = "Nueva contraseña", onClick = {})
    }
}
