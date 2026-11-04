package com.luis.end_security.feature.activity.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.luis.end_security.R
import com.luis.end_security.core.designsystem.component.BackButton
import com.luis.end_security.core.designsystem.component.BottomNavBar
import com.luis.end_security.core.designsystem.component.BottomNavTab
import com.luis.end_security.core.designsystem.component.MessageBar
import com.luis.end_security.core.designsystem.component.NeumorphicDisc
import com.luis.end_security.core.designsystem.component.SkeletonBlock
import com.luis.end_security.core.designsystem.component.SkeletonPulse

/** Margen lateral del contenido (Figma: 32). */
internal val ListPagePadding = 32.dp

/**
 * Página de la cuenta con lista (Dispositivos, Actividad; Figma 47:1817 y 47:1938): barra superior con atrás y título, el
 * [content] (que decide si se desplaza), acciones fijas abajo y la barra inferior con Ajustes activa. El [notice] flota
 * sobre la barra inferior como aviso oscuro y se anuncia solo.
 */
@Composable
internal fun AccountListPage(
    title: String,
    onBack: () -> Unit,
    onTabSelected: (BottomNavTab) -> Unit,
    modifier: Modifier = Modifier,
    notice: String? = null,
    bottom: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(modifier = Modifier.weight(1f)) {
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BackButton(onClick = onBack)
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 14.dp).semantics { heading() },
                    )
                }
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) { content() }
                if (bottom != null) {
                    Column(
                        modifier = Modifier.padding(horizontal = ListPagePadding).padding(top = 8.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) { bottom() }
                }
            }
            if (notice != null) {
                MessageBar(
                    text = notice,
                    icon = R.drawable.ic_info_20,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = ListPagePadding, vertical = 8.dp),
                )
            }
        }
        BottomNavBar(selected = BottomNavTab.Settings, onTabSelected = onTabSelected)
    }
}

/**
 * «Sin conexión» (Figma 69:3850): disco neumórfico con el wifi tachado, título y explicación centrados. El botón
 * «Reintentar» va abajo, en el `bottom` de la página.
 */
@Composable
internal fun OfflinePanel(title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = ListPagePadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(80.dp))
        NeumorphicDisc(
            icon = R.drawable.ic_wifi_off_40,
            contentDescription = null,
            iconTint = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(36.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** «Estado de carga» de Figma: punto amarillo y el texto. Región viva: TalkBack lo anuncia al aparecer. */
@Composable
internal fun LoadingStatus(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Una fila de esqueleto: tesela, dos líneas y, a la derecha, [trailingWidth] × [trailingHeight] (Figma 69:3453). */
@Composable
internal fun SkeletonRow(titleWidth: Dp, subtitleWidth: Dp, trailingWidth: Dp, trailingHeight: Dp) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkeletonBlock(modifier = Modifier.size(40.dp), shape = RoundedCornerShape(13.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeletonBlock(modifier = Modifier.width(titleWidth).height(14.dp), shape = RoundedCornerShape(7.dp))
            SkeletonBlock(modifier = Modifier.width(subtitleWidth).height(11.dp), shape = RoundedCornerShape(6.dp))
        }
        SkeletonBlock(modifier = Modifier.width(trailingWidth).height(trailingHeight), shape = RoundedCornerShape(7.dp))
    }
}

/** Tarjeta de esqueleto con una fila por cada par de anchos de [rows] (título, subtítulo). Divisores sólidos como en Figma. */
@Composable
internal fun SkeletonCard(rows: List<Pair<Dp, Dp>>, trailingWidth: Dp, trailingHeight: Dp) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
            .padding(vertical = 4.dp),
    ) {
        rows.forEachIndexed { index, (title, subtitle) ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(horizontal = 16.dp))
            SkeletonRow(title, subtitle, trailingWidth, trailingHeight)
        }
    }
}

/** Esqueleto de Dispositivos (Figma 69:3411): dos líneas de título y tres filas. */
@Composable
internal fun DevicesSkeleton(loadingText: String) {
    SkeletonPulse {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = ListPagePadding).padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SkeletonBlock(modifier = Modifier.width(190.dp).height(28.dp), shape = RoundedCornerShape(8.dp))
            SkeletonBlock(modifier = Modifier.width(250.dp).height(28.dp), shape = RoundedCornerShape(8.dp))
            SkeletonCard(
                rows = listOf(130.dp to 170.dp, 100.dp to 140.dp, 150.dp to 120.dp),
                trailingWidth = 14.dp,
                trailingHeight = 14.dp,
            )
            LoadingStatus(loadingText)
        }
    }
}

/** Esqueleto de Actividad (Figma 69:3513): dos días, con dos y tres filas. */
@Composable
internal fun ActivitySkeleton(loadingText: String) {
    SkeletonPulse {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = ListPagePadding).padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SkeletonBlock(modifier = Modifier.width(48.dp).height(14.dp), shape = RoundedCornerShape(7.dp))
            SkeletonCard(rows = listOf(130.dp to 170.dp, 100.dp to 140.dp), trailingWidth = 40.dp, trailingHeight = 12.dp)
            SkeletonBlock(modifier = Modifier.width(48.dp).height(14.dp), shape = RoundedCornerShape(7.dp))
            SkeletonCard(
                rows = listOf(130.dp to 170.dp, 100.dp to 140.dp, 150.dp to 120.dp),
                trailingWidth = 40.dp,
                trailingHeight = 12.dp,
            )
            LoadingStatus(loadingText)
        }
    }
}
