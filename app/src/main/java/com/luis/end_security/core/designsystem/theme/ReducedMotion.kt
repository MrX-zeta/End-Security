package com.luis.end_security.core.designsystem.theme

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * `true` si el usuario activó «Quitar animaciones» en Accesibilidad (la escala de duración
 * de animaciones del sistema vale 0). Los componentes cambian de estado sin transición.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
}
