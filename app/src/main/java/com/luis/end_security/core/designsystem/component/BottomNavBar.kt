package com.luis.end_security.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import kotlin.math.abs
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp as lerpFloat
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

/** Resorte sin rebote que mueve la píldora (Apple: amortiguamiento 1,0, respuesta 0,3 s); llega en unos 250 ms. */
private val PillSpring = spring<Float>(dampingRatio = 1f, stiffness = 440f)

/** El ícono «se enciende» y la etiqueta cambia de color mientras llega la píldora. */
private const val TabColorMillis = 150

/** Pulso del ícono que pasa a estar seleccionado: escala de [PulseFrom] a 1 en [PulseMillis]. */
private const val PulseFrom = 0.92f
private const val PulseMillis = 200

/** El pulso empieza cuando la píldora ya va llegando (ella tarda unos 250 ms). */
private const val PulseDelayMillis = 80L

/** Cuánto vale un relevo: si el cambio de pestaña es más viejo que esto, ya no es el de esta barra. */
private const val HandoffWindowNanos = 1_500_000_000L

/** Etiqueta de prueba de la píldora. */
const val PillTag = "NavPill"

/**
 * Cada pantalla de pestaña dibuja su propia barra, así que al cambiar de pestaña **llega una barra nueva**. Para que la píldora se
 * deslice desde la pestaña anterior, la barra que se toca anota aquí de dónde salió y la barra nueva lo recoge al crearse.
 * Una barra que aparece por otra razón (abrir la app, volver de Desbloquear) no encuentra relevo y dibuja la píldora ya en su lugar.
 */
@Stable
class TabBarHandoff {
    private var from: BottomNavTab? = null
    private var to: BottomNavTab? = null
    private var atNanos = 0L

    fun record(from: BottomNavTab, to: BottomNavTab) {
        this.from = from
        this.to = to
        atNanos = System.nanoTime()
    }

    /** La pestaña de la que viene [selected], o `null` si no hay un cambio reciente hacia ella. Se gasta al recogerla. */
    fun consume(selected: BottomNavTab): BottomNavTab? {
        val origin = from
        val fresh = to == selected && origin != null && origin != selected && System.nanoTime() - atNanos <= HandoffWindowNanos
        from = null
        to = null
        return if (fresh) origin else null
    }
}

/** La da `AppNavHost`; sin ella (vistas previas, pruebas de la barra sola) cada barra lleva el suyo. */
val LocalTabBarHandoff = compositionLocalOf<TabBarHandoff?> { null }

/**
 * «Navegación inferior» de Figma (nodo 35:364), en todas las vistas con sesión iniciada (DEC-56).
 * La pestaña activa lleva el ícono claro sobre una píldora `on-surface` y la etiqueta en Bold; las etiquetas siempre se ven.
 * Cada pestaña mide 76 × 54 (más de 48 dp) y TalkBack la lee como pestaña, seleccionada o no.
 *
 * **Movimiento (tarea 25):** hay una sola píldora, dibujada detrás de la fila, que se desliza de una pestaña a otra con un resorte sin
 * rebote. Su posición se lee en la fase de dibujo, así que ni la barra ni las pestañas se recomponen cuadro a cuadro. El ícono que
 * pasa a estar seleccionado hace un pulso de escala y los colores cambian en 150 ms. Con [reducedMotion] («Quitar animaciones») todo
 * es instantáneo.
 */
@Composable
fun BottomNavBar(
    selected: BottomNavTab,
    onTabSelected: (BottomNavTab) -> Unit,
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = rememberReducedMotion(),
) {
    val handoff = LocalTabBarHandoff.current ?: remember { TabBarHandoff() }
    // De dónde parte esta barra: la pestaña anterior si llegó por un cambio de pestaña; si no, ya está en su lugar.
    val origin = remember { handoff.consume(selected)?.takeUnless { reducedMotion } ?: selected }
    // Posición de la píldora en «índices de pestaña» (0 a 3): sobrevive a un cambio de ancho y el resorte dura lo mismo cerca o lejos.
    val pillIndex = remember { Animatable(origin.ordinal.toFloat()) }
    // Qué pestaña se ve encendida: arranca en la de origen y pasa a la seleccionada en el primer cuadro (así el ícono «se enciende»).
    var settled by remember { mutableStateOf(origin == selected) }
    LaunchedEffect(Unit) { settled = true }
    val shown = if (settled) selected else origin
    LaunchedEffect(selected, reducedMotion) {
        if (reducedMotion) pillIndex.snapTo(selected.ordinal.toFloat()) else pillIndex.animateTo(selected.ordinal.toFloat(), PillSpring)
    }
    // Centro horizontal de cada pestaña, medido (no calculado). Se lee solo al dibujar la píldora.
    val centers = remember { mutableStateListOf(0f, 0f, 0f, 0f) }
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
            Box(
                modifier = Modifier
                    .size(width = PillWidth, height = PillHeight)
                    .graphicsLayer {
                        val position = pillIndex.value.coerceIn(0f, (centers.size - 1).toFloat())
                        val low = position.toInt().coerceAtMost(centers.size - 2)
                        val center = lerpFloat(centers[low], centers[low + 1], position - low)
                        translationX = center - size.width / 2f
                    }
                    .background(pillColor, ActiveIconShape)
                    .testTag(PillTag),
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
                        lit = tab == shown,
                        index = index,
                        pillIndex = pillIndex,
                        reducedMotion = reducedMotion,
                        onClick = {
                            handoff.record(from = selected, to = tab)
                            onTabSelected(tab)
                        },
                        modifier = Modifier.onGloballyPositioned { coordinates ->
                            val center = coordinates.positionInParent().x + coordinates.size.width / 2f
                            if (centers[index] != center) centers[index] = center
                        },
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
    lit: Boolean,
    index: Int,
    pillIndex: Animatable<Float, AnimationVector1D>,
    reducedMotion: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val colorSpec: AnimationSpec<Color> = if (reducedMotion) snap() else tween(TabColorMillis, easing = Motion.EaseOut)
    val iconPainter = painterResource(tab.icon)
    val litIcon = colors.background
    val dimIcon = colors.onSurfaceVariant
    val labelColor by animateColorAsState(if (lit) colors.onSurface else colors.onSurfaceVariant, colorSpec, label = "labelColor")

    // Pulso: solo cuando la pestaña PASA a estar encendida (no en el primer dibujo ni al tocar la que ya lo está).
    val pulse = remember { Animatable(1f) }
    var wasLit by remember { mutableStateOf(lit) }
    LaunchedEffect(lit) {
        if (lit && !wasLit && !reducedMotion) {
            delay(PulseDelayMillis) // a la par de la llegada de la píldora, no de su salida
            pulse.snapTo(PulseFrom)
            pulse.animateTo(1f, tween(PulseMillis, easing = Motion.EaseOut))
        }
        wasLit = lit
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
                        val reach = (1f - abs(pillIndex.value - index)).coerceIn(0f, 1f)
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
