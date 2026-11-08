package com.luis.end_security.core.designsystem.theme

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Ajusta el color de los íconos de las barras del sistema mientras la pantalla está visible
 * y restaura el valor anterior al salir. Las pantallas oscuras (onboarding) piden íconos claros.
 */
@Composable
fun SystemBarsAppearance(darkBackground: Boolean) {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    DisposableEffect(window, view, darkBackground) {
        val controller = WindowCompat.getInsetsController(window, view)
        val previousStatus = controller.isAppearanceLightStatusBars
        val previousNavigation = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = !darkBackground
        controller.isAppearanceLightNavigationBars = !darkBackground
        onDispose {
            controller.isAppearanceLightStatusBars = previousStatus
            controller.isAppearanceLightNavigationBars = previousNavigation
        }
    }
}
