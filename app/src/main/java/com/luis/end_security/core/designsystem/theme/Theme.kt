package com.luis.end_security.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    error = LightDanger,
    errorContainer = LightDangerContainer,
)

private val DarkColorScheme = darkColorScheme(
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    error = DarkDanger,
    errorContainer = DarkDangerContainer,
)

/**
 * Tokens de DEC-50 que no tienen lugar en el [androidx.compose.material3.ColorScheme].
 * `danger` y `danger-container` de Figma van en `colorScheme.error` y `errorContainer`,
 * para que los componentes de Material (p. ej. un campo con error) los usen solos.
 */
@Immutable
data class ExtendedColors(
    val blob: Color,
    val blobGrain: Color,
    val shadowLight: Color,
    val shadowDark: Color,
    val success: Color,
    val successContainer: Color,
)

private val LightExtendedColors = ExtendedColors(
    blob = LightBlob,
    blobGrain = BlobGrain,
    shadowLight = LightShadowLight,
    shadowDark = LightShadowDark,
    success = LightSuccess,
    successContainer = LightSuccessContainer,
)

private val DarkExtendedColors = ExtendedColors(
    blob = DarkBlob,
    blobGrain = BlobGrain,
    shadowLight = DarkShadowLight,
    shadowDark = DarkShadowDark,
    success = DarkSuccess,
    successContainer = DarkSuccessContainer,
)

private val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }

/**
 * Tema «Carbón y miel». La app es clara (DEC-51) y no sigue el modo oscuro del sistema;
 * solo el onboarding pide `darkTheme = true`. No usa color dinámico: la paleta es la marca.
 */
@Composable
fun EndSecurityTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalExtendedColors provides if (darkTheme) DarkExtendedColors else LightExtendedColors,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = Typography,
            content = content,
        )
    }
}

/** Acceso a los tokens extendidos: `EndSecurityTheme.extendedColors.blob`. */
object EndSecurityTheme {
    val extendedColors: ExtendedColors
        @Composable
        @ReadOnlyComposable
        get() = LocalExtendedColors.current
}
