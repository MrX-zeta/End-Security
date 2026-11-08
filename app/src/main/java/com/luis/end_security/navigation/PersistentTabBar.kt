package com.luis.end_security.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.luis.end_security.core.designsystem.component.BottomNavBar
import com.luis.end_security.core.designsystem.component.BottomNavTab
import com.luis.end_security.core.designsystem.component.isKeyboardVisible
import com.luis.end_security.core.designsystem.theme.Motion
import com.luis.end_security.core.designsystem.theme.rememberReducedMotion

private val VaultTabRoutes = setOf(
    Routes.VAULT_LOADING,
    Routes.VAULT,
    Routes.VAULT_ITEM,
    Routes.VAULT_EDIT,
    Routes.CONFLICTS,
    Routes.CONFLICT_ITEM,
)

private val HealthTabRoutes = setOf(
    Routes.HEALTH,
    Routes.HEALTH_RANKING,
    Routes.HEALTH_BREACHED,
    Routes.HEALTH_REPEATED,
    Routes.HEALTH_OLD,
)

private val SettingsTabRoutes = setOf(
    Routes.SETTINGS,
    Routes.TWO_FACTOR_SETUP,
    Routes.TWO_FACTOR_ACTIVE,
    Routes.CHANGE_PASSWORD,
    Routes.DELETE_ACCOUNT,
    Routes.CHANGE_EMAIL,
    Routes.DEVICES,
    Routes.ACTIVITY,
)

/**
 * La pestaña a la que pertenece un destino, o `null` si ahí no hay barra inferior (acceso, Desbloquear, «Cuenta eliminada», documentos
 * legales). [route] es el patrón del destino (`vault_item/{id}`), no la ruta con valores.
 */
internal fun tabForRoute(route: String?): BottomNavTab? = when (route) {
    null -> null
    in VaultTabRoutes -> BottomNavTab.Vault
    in HealthTabRoutes -> BottomNavTab.Health
    Routes.GENERATOR -> BottomNavTab.Generator
    in SettingsTabRoutes -> BottomNavTab.Settings
    else -> null
}

/**
 * La barra inferior **única** de la app (tarea 26), fuera del `NavHost`: no se vuelve a crear al cambiar de pestaña, así que la píldora
 * se desliza sobre la misma barra. Aparece y se oculta deslizándose hacia abajo y desvaneciéndose ([Motion.BarVisibility]); con el
 * teclado abierto se oculta al instante (el teclado ya se está moviendo) y con «Quitar animaciones» no hay movimiento.
 *
 * [tab] es la pestaña del destino actual, `null` donde no hay barra. Al ocultarse conserva la última para que la salida no cambie de
 * pestaña a mitad del movimiento.
 */
@Composable
internal fun PersistentTabBar(
    tab: BottomNavTab?,
    onTabSelected: (BottomNavTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val keyboardOpen = isKeyboardVisible()
    var lastTab by remember { mutableStateOf(BottomNavTab.Vault) }
    SideEffect { if (tab != null) lastTab = tab }
    val spec = tween<Float>(Motion.BarVisibility, easing = Motion.EaseOut)
    val offsetSpec = tween<androidx.compose.ui.unit.IntOffset>(Motion.BarVisibility, easing = Motion.EaseOut)
    AnimatedVisibility(
        visible = tab != null && !keyboardOpen,
        modifier = modifier,
        enter = if (reducedMotion) EnterTransition.None else slideInVertically(offsetSpec) { it } + fadeIn(spec),
        exit = if (reducedMotion || keyboardOpen) ExitTransition.None else slideOutVertically(offsetSpec) { it } + fadeOut(spec),
    ) {
        BottomNavBar(selected = tab ?: lastTab, onTabSelected = onTabSelected, reducedMotion = reducedMotion)
    }
}
