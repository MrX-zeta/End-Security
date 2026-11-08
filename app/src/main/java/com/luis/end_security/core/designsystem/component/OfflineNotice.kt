package com.luis.end_security.core.designsystem.component

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.luis.end_security.R
import com.luis.end_security.core.designsystem.theme.PreviewSurface

/**
 * Aviso «Sin conexión» de Figma (SCR-44, nodo 69:3744): un marco de contorno, sin relleno, con el ícono de
 * wifi tachado. Informa, no pide nada: la bóveda sigue usándose y los cambios se suben al reconectar (DEC-58).
 */
@Composable
fun OfflineNotice(modifier: Modifier = Modifier) {
    val shape = MaterialTheme.shapes.medium
    Row(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_wifi_off_20),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = stringResource(R.string.vault_offline_notice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Preview(name = "Sin conexión")
@Composable
private fun OfflineNoticePreview() {
    PreviewSurface(modifier = Modifier.padding(24.dp)) {
        OfflineNotice()
    }
}
