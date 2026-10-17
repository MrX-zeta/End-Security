package com.luis.end_security.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.luis.end_security.R

// Montserrat empaquetada (TEC-MONTSERRAT, licencia OFL en assets/licenses/montserrat/OFL.txt).
val Montserrat = FontFamily(
    Font(R.font.montserrat_regular, FontWeight.Normal),
    Font(R.font.montserrat_medium, FontWeight.Medium),
    Font(R.font.montserrat_semibold, FontWeight.SemiBold),
    Font(R.font.montserrat_bold, FontWeight.Bold),
)

// Interletrado de −2 % que usan los títulos en Figma.
private val TitleLetterSpacing = (-0.02).em

// Figma reparte el interlineado arriba y abajo de cada línea; Compose por defecto lo recorta
// en la primera y la última, y cada texto queda unos dp más bajo que en el diseño.
private val FigmaLineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private val Base = Typography()

// Estilos tomados de Figma (página «Minimalista»). Los que no aparecen ahí conservan
// los tamaños de Material 3, pero también usan Montserrat.
val Typography = Typography(
    displayLarge = Base.displayLarge.copy(fontFamily = Montserrat),
    displayMedium = Base.displayMedium.copy(fontFamily = Montserrat),
    // Título en dos pesos de las pantallas de acceso (Iniciar sesión, Crear cuenta).
    displaySmall = TextStyle(
        fontFamily = Montserrat,
        fontWeight = FontWeight.Normal,
        fontSize = 34.sp,
        lineHeight = 42.sp,
        lineHeightStyle = FigmaLineHeightStyle,
        letterSpacing = TitleLetterSpacing,
    ),
    // Título de pantalla en dos pesos: la primera línea se pinta en Bold (TwoWeightTitle).
    headlineLarge = TextStyle(
        fontFamily = Montserrat,
        fontWeight = FontWeight.Normal,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        lineHeightStyle = FigmaLineHeightStyle,
        letterSpacing = TitleLetterSpacing,
    ),
    headlineMedium = Base.headlineMedium.copy(fontFamily = Montserrat),
    // Dígitos de las casillas de código (CodeField).
    headlineSmall = TextStyle(
        fontFamily = Montserrat,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        lineHeightStyle = FigmaLineHeightStyle,
    ),
    // Marca «End Security» en la barra superior.
    titleLarge = TextStyle(
        fontFamily = Montserrat,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 26.sp,
        lineHeightStyle = FigmaLineHeightStyle,
        letterSpacing = TitleLetterSpacing,
    ),
    // Enlaces de texto (se subrayan en el componente).
    titleMedium = TextStyle(
        fontFamily = Montserrat,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        lineHeightStyle = FigmaLineHeightStyle,
    ),
    titleSmall = Base.titleSmall.copy(fontFamily = Montserrat),
    // Texto de apoyo bajo los títulos.
    bodyLarge = TextStyle(
        fontFamily = Montserrat,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 24.sp,
        lineHeightStyle = FigmaLineHeightStyle,
    ),
    bodyMedium = Base.bodyMedium.copy(fontFamily = Montserrat),
    // Texto de ayuda y de error bajo los campos.
    bodySmall = TextStyle(
        fontFamily = Montserrat,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        lineHeightStyle = FigmaLineHeightStyle,
    ),
    // Texto del botón primario.
    labelLarge = TextStyle(
        fontFamily = Montserrat,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        lineHeightStyle = FigmaLineHeightStyle,
    ),
    // Botones secundarios pequeños, como «Omitir».
    labelMedium = TextStyle(
        fontFamily = Montserrat,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        lineHeightStyle = FigmaLineHeightStyle,
    ),
    labelSmall = Base.labelSmall.copy(fontFamily = Montserrat),
)
