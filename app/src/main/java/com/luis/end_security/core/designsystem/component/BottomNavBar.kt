package com.luis.end_security.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.luis.end_security.R
import com.luis.end_security.core.designsystem.theme.EndSecurityTheme
import com.luis.end_security.core.designsystem.theme.PreviewSurface

/** Pestañas de la barra inferior (DEC-56), en orden. Íconos de la biblioteca de Figma (DEC-59). */
enum class BottomNavTab(@param:StringRes val label: Int, @param:DrawableRes val icon: Int) {
    Vault(R.string.nav_vault, R.drawable.ic_vault_22),
    Health(R.string.nav_health, R.drawable.ic_shield_check_22),
    Generator(R.string.nav_generator, R.drawable.ic_dice_22),
    Settings(R.string.nav_settings, R.drawable.ic_settings_22),
}

private val BarShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
private val ActiveIconShape = RoundedCornerShape(12.dp)

/**
 * «Navegación inferior» de Figma (nodo 35:364), en todas las vistas con sesión iniciada (DEC-56).
 * La pestaña activa lleva el ícono claro en un cuadro `on-surface` y la etiqueta en Bold; las
 * etiquetas siempre se ven. Cada pestaña mide 76 × 54 (más de 48 dp) y TalkBack la lee como
 * pestaña, seleccionada o no.
 */
@Composable
fun BottomNavBar(
    selected: BottomNavTab,
    onTabSelected: (BottomNavTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .dropShadow(
                shape = BarShape,
                shadow = Shadow(
                    radius = 12.dp,
                    color = EndSecurityTheme.extendedColors.shadowDark,
                    offset = DpOffset(x = 0.dp, y = (-6).dp),
                ),
            ),
        shape = BarShape,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier
                // 14 dp abajo como en Figma, o la barra de gestos si es más alta.
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets(bottom = 14.dp)))
                .padding(start = 20.dp, end = 20.dp, top = 10.dp)
                .selectableGroup(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BottomNavTab.entries.forEach { tab ->
                BottomNavItem(tab = tab, selected = tab == selected, onClick = { onTabSelected(tab) })
            }
        }
    }
}

@Composable
private fun BottomNavItem(
    tab: BottomNavTab,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .width(76.dp)
            .selectable(selected = selected, onClick = onClick, role = Role.Tab)
            .testTag(tab.name),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(width = 44.dp, height = 36.dp)
                .then(if (selected) Modifier.background(colors.onSurface, ActiveIconShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            // Decorativo: la etiqueta ya dice qué pestaña es.
            Icon(
                painter = painterResource(tab.icon),
                contentDescription = null,
                tint = if (selected) colors.background else colors.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(tab.label),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) colors.onSurface else colors.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun BottomNavBarPreview(selected: BottomNavTab) {
    PreviewSurface(modifier = Modifier.padding(top = 24.dp)) {
        BottomNavBar(selected = selected, onTabSelected = {})
    }
}

@Preview(name = "Barra inferior · Bóveda", widthDp = 412)
@Composable
private fun BottomNavBarVaultPreview() = BottomNavBarPreview(BottomNavTab.Vault)

@Preview(name = "Barra inferior · Salud", widthDp = 412)
@Composable
private fun BottomNavBarHealthPreview() = BottomNavBarPreview(BottomNavTab.Health)

@Preview(name = "Barra inferior · Generador", widthDp = 412)
@Composable
private fun BottomNavBarGeneratorPreview() = BottomNavBarPreview(BottomNavTab.Generator)

@Preview(name = "Barra inferior · Ajustes", widthDp = 412)
@Composable
private fun BottomNavBarSettingsPreview() = BottomNavBarPreview(BottomNavTab.Settings)
