package com.luis.end_security.feature.vault.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.luis.end_security.R
import com.luis.end_security.core.designsystem.component.BottomNavBar
import com.luis.end_security.core.designsystem.component.BottomNavTab
import com.luis.end_security.core.designsystem.component.OutlinedIconButton
import com.luis.end_security.core.designsystem.component.SkeletonBlock
import com.luis.end_security.core.designsystem.component.SkeletonPulse
import com.luis.end_security.core.designsystem.theme.PreviewSurface

// Medidas de Figma (nodo 69:3207).
private val ContentHorizontalPadding = 32.dp
private val RowShape = RoundedCornerShape(18.dp)
private val AvatarShape = RoundedCornerShape(13.dp)

/** Ancho del nombre y del usuario de cada fila de esqueleto, como en Figma. */
private val SkeletonRows: List<Pair<Dp, Dp>> = listOf(
    110.dp to 170.dp,
    80.dp to 120.dp,
    130.dp to 150.dp,
    95.dp to 180.dp,
    120.dp to 140.dp,
)

/**
 * SCR-39 · Bóveda · cargando: la primera descarga de la bóveda después de entrar (OP-32).
 * Tema claro (DEC-51). Los esqueletos no son contenido: TalkBack solo oye «Descargando tu
 * bóveda» una vez, como título del panel, y puede leer el título y el estado.
 */
@Composable
fun VaultLoadingScreen(
    onTabSelected: (BottomNavTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val announcement = stringResource(R.string.vault_loading_announcement)
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .semantics { paneTitle = announcement },
    ) {
        SkeletonPulse {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .statusBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = ContentHorizontalPadding),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Inicial de la cuenta (SCR-10), todavía sin datos.
                    SkeletonBlock(modifier = Modifier.size(44.dp), shape = AvatarShape)
                    // «Bloquear ahora» (DEC-52): deshabilitado mientras no hay bóveda que bloquear.
                    OutlinedIconButton(
                        icon = R.drawable.ic_lock_20,
                        contentDescription = stringResource(R.string.vault_lock_now),
                        onClick = {},
                        enabled = false,
                    )
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    text = stringResource(R.string.vault_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(16.dp))
                LoadingStatus()
                Spacer(Modifier.height(16.dp))
                // Buscador.
                SkeletonBlock(modifier = Modifier.fillMaxWidth().height(52.dp))
                Spacer(Modifier.height(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SkeletonRows.forEach { (titleWidth, subtitleWidth) ->
                        SkeletonRow(titleWidth = titleWidth, subtitleWidth = subtitleWidth)
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
        BottomNavBar(selected = BottomNavTab.Vault, onTabSelected = onTabSelected)
    }
}

/** Punto `primary` y «Descargando tu bóveda…». */
@Composable
private fun LoadingStatus() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.vault_loading),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** «Fila cargando»: ícono, nombre, usuario y la marca de la derecha. */
@Composable
private fun SkeletonRow(titleWidth: Dp, subtitleWidth: Dp) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RowShape)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkeletonBlock(modifier = Modifier.size(44.dp), shape = AvatarShape)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeletonBlock(modifier = Modifier.width(titleWidth).height(14.dp), shape = RoundedCornerShape(7.dp))
            SkeletonBlock(modifier = Modifier.width(subtitleWidth).height(11.dp), shape = RoundedCornerShape(6.dp))
        }
        SkeletonBlock(modifier = Modifier.width(16.dp).height(14.dp), shape = RoundedCornerShape(7.dp))
    }
}

@Preview(name = "Bóveda · cargando", device = "spec:width=412dp,height=917dp")
@Composable
private fun VaultLoadingPreview() {
    PreviewSurface {
        VaultLoadingScreen(onTabSelected = {})
    }
}

@Preview(name = "Bóveda · cargando · pantalla baja", device = "spec:width=360dp,height=740dp")
@Composable
private fun VaultLoadingSmallPreview() {
    PreviewSurface {
        VaultLoadingScreen(onTabSelected = {})
    }
}
