package com.luis.end_security.core.designsystem.theme

import androidx.compose.ui.graphics.Color

// Tokens de «Carbón y miel» (DEC-50). Este es el ÚNICO archivo con valores hex (INV-28);
// los composables usan MaterialTheme.colorScheme o EndSecurityTheme.extendedColors.
// Los nombres siguen las variables de Figma (color/background, color/on-surface, …).

// Tema claro: toda la app salvo el onboarding (DEC-51).
internal val LightBackground = Color(0xFFF6F6F6)
internal val LightSurface = Color(0xFFFFFFFF)
internal val LightOutline = Color(0xFF858585)
internal val LightOnSurface = Color(0xFF111111)
internal val LightOnSurfaceVariant = Color(0xFF2F2F2F)
internal val LightPrimary = Color(0xFFFFCB74)
internal val LightOnPrimary = Color(0xFF111111)
internal val LightBlob = Color(0xFF2F2F2F)
internal val LightShadowLight = Color(0xF2FFFFFF) // #FFFFFF al 95 %
internal val LightShadowDark = Color(0x1F111111) // #111111 al 12 %
internal val LightDanger = Color(0xFFB42318)
internal val LightDangerContainer = Color(0xFFFDE8E6)
internal val LightSuccess = Color(0xFF1E6B45)
internal val LightSuccessContainer = Color(0xFFE3F1EA)

// Tema oscuro: solo el onboarding (DEC-51).
internal val DarkBackground = Color(0xFF2F2F2F)
internal val DarkSurface = Color(0xFF111111)
internal val DarkOutline = Color(0xFF7D7D7D)
internal val DarkOnSurface = Color(0xFFF6F6F6)
internal val DarkOnSurfaceVariant = Color(0xFFB0B0B0)
internal val DarkPrimary = Color(0xFFFFCB74)
internal val DarkOnPrimary = Color(0xFF111111)
internal val DarkBlob = Color(0xFF111111)
internal val DarkShadowLight = Color(0x12FFFFFF) // #FFFFFF al 7 %
internal val DarkShadowDark = Color(0x73000000) // #000000 al 45 %
internal val DarkDanger = Color(0xFFF28B82)
internal val DarkDangerContainer = Color(0xFF4A2523)
internal val DarkSuccess = Color(0xFF7FD1A3)
internal val DarkSuccessContainer = Color(0xFF1F3A2C)

// Grano de las manchas orgánicas (blob-grain en DEC-50): ruido blanco de Figma,
// al 12 % en el tema claro y al 7 % en el oscuro.
internal val LightBlobGrain = Color(0x1FFFFFFF) // #FFFFFF al 12 %
internal val DarkBlobGrain = Color(0x12FFFFFF) // #FFFFFF al 7 %
