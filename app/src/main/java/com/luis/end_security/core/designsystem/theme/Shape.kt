package com.luis.end_security.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Radios de la biblioteca de Figma (nodo 35:10). Los tamaños sin uso en Figma
// conservan el valor de Material 3.
val Shapes = Shapes(
    // Indicador de páginas (pastilla de 8 de alto).
    extraSmall = RoundedCornerShape(4.dp),
    // Botones pequeños de contorno, como «Omitir».
    small = RoundedCornerShape(14.dp),
    // Botones primario, secundario y de peligro, y campos de texto.
    medium = RoundedCornerShape(16.dp),
)
