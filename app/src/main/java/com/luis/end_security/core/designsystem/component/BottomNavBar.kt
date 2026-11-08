package com.luis.end_security.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.lerp
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.graphicsLayer
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
import com.luis.end_security.core.designsystem.theme.Motion
import com.luis.end_security.core.designsystem.theme.PreviewSurface
import com.luis.end_security.core.designsystem.theme.rememberReducedMotion

/** Pestañas de la barra inferior (DEC-56), en orden. Íconos de la biblioteca de Figma (DEC-59). */
enum class BottomNavTab(@param:StringRes val label: Int, @param:DrawableRes val icon: Int) {
    Vault(R.string.nav_vault, R.drawable.ic_vault_22),
    Health(R.string.nav_health, R.drawable.ic_shield_check_22),
    Generator(R.string.nav_generator, R.drawable.ic_dice_22),
    Settings(R.string.nav_settings, R.drawable.ic_settings_22),
}

private val BarShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
private val ActiveIconShape = RoundedCornerShape(12.dp)
private val PillWidth = 44.dp
private val PillHeight = 36.dp

/** El ícono «se enciende» y la etiqueta cambia de color mientras llega la píldora. */
private const val TabColorMillis = 150

/** Pulso del ícono que pasa a estar seleccionado: escala de [PulseFrom] a 1 en [PulseMillis]. */
private const val PulseFrom = 0.92f
private const val PulseMillis = 200

/** El pulso empieza cuando la píldora ya va llegando (ella tarda unos 250 ms). */
private const val PulseDelayMillis = 80L

/** Etiqueta de prueba de la píldora. */
const val PillTag = "NavPill"

/**
 * «Navegación inferior» de Figma (nodo 35:364), en todas las vistas con sesión iniciada (DEC-56).
 * La pestaña activa lleva el ícono claro sobre una píldora `on-surface` y la etiqueta en Bold; las etiquetas siempre se ven.
 * Cada pestaña mide 76 × 54 (más de 48 dp) y TalkBack la lee como pestaña, seleccionada o no.
 *
 * **Movimiento (tareas 25 y 26):** hay **una sola barra** para toda la app (la dibuja `AppNavHost` fuera del `NavHost`), así que no se
 * vuelve a crear al cambiar de pestaña: una sola píldora, detrás de la fila, se desliza con un resorte sin rebote
 * ([SlidingSelectionIndicator], el mismo de los selectores de Salud). El ícono que pasa a estar seleccionado hace un pulso de escala y
 * los colores cambian en 150 ms. Con [reducedMotion] («Quitar animaciones») todo es instantáneo.
 */
@Composable
fun BottomNavBar(
    selected: BottomNavTab,
    onTabSelected: (BottomNavTab) -> Unit,
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = rememberReducedMotion(),
) {
    val selection = rememberSlidingSelectionState(selected.ordinal, BottomNavTab.entries.size, reducedMotion)
    val pillColor = MaterialTheme.colorScheme.onSurface

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
        Box(
            modifier = Modifier
                // 14 dp abajo como en Figma, o la barra de gestos si es más alta.
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets(bottom = 14.dp)))
                .padding(start = 20.dp, end = 20.dp, top = 10.dp)
                .fillMaxWidth(),
        ) {
            // La píldora va detrás de la fila. Decorativa: el estado lo dicen las pestañas.
            SlidingSelectionIndicator(
                state = selection,
                color = pillColor,
                shape = ActiveIconShape,
                modifier = Modifier.size(width = PillWidth, height = PillHeight).testTag(PillTag),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BottomNavTab.entries.forEachIndexed { index, tab ->
                    BottomNavItem(
                        tab = tab,
                        selected = tab == selected,
                        index = index,
                        selection = selection,
                        reducedMotion = reducedMotion,
                        onClick = { onTabSelected(tab) },
                        modifier = Modifier.trackSelectionCenter(selection, index),
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomNavItem(
    tab: BottomNavTab,
    selected: Boolean,
    index: Int,
    selection: SlidingSelectionState,
    reducedMotion: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val colorSpec: AnimationSpec<Color> = if (reducedMotion) snap() else tween(TabColorMillis, easing = Motion.EaseOut)
    val iconPainter = painterResource(tab.icon)
    val litIcon = colors.background
    val dimIcon = colors.onSurfaceVariant
    val labelColor by animateColorAsState(if (selected) colors.onSurface else colors.onSurfaceVariant, colorSpec, label = "labelColor")

    // Pulso: solo cuando la pestaña PASA a estar encendida (no en el primer dibujo ni al tocar la que ya lo está).
    val pulse = remember { Animatable(1f) }
    var wasLit by remember { mutableStateOf(selected) }
    LaunchedEffect(selected) {
        if (selected && !wasLit && !reducedMotion) {
            delay(PulseDelayMillis) // a la par de la llegada de la píldora, no de su salida
            pulse.snapTo(PulseFrom)
            pulse.animateTo(1f, tween(PulseMillis, easing = Motion.EaseOut))
        }
        wasLit = selected
    }

    Column(
        modifier = modifier
            .width(76.dp)
            // Sin onda al tocar: la píldora negra de la pestaña activa ya marca la selección (tarea 23). Se conservan el rol
            // `Tab`, el estado seleccionado para TalkBack y el área táctil de 76 × 54.
            .selectable(
                selected = selected,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .testTag(tab.name),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(width = PillWidth, height = PillHeight),
            contentAlignment = Alignment.Center,
        ) {
            // Decorativo: la etiqueta ya dice qué pestaña es.
            // El color del ícono sigue a la píldora: se enciende (claro) cuando ella llega debajo y se apaga cuando se va, así nunca queda
            // claro sobre el fondo claro mientras la píldora aún viene. Se lee al dibujar: no recompone.
            Spacer(
                Modifier
                    .size(22.dp)
                    .graphicsLayer {
                        scaleX = pulse.value
                        scaleY = pulse.value
                    }
                    .testTag(tab.name + "Icon")
                    .drawBehind {
                        val reach = selection.reach(index)
                        with(iconPainter) { draw(size, colorFilter = ColorFilter.tint(lerp(dimIcon, litIcon, reach))) }
                    },
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(tab.label),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = labelColor,
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
