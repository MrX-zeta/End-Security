package com.luis.end_security.core.designsystem.component

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer

/**
 * Textos largos que **aprovechan el ancho** (tarea 26).
 *
 * Android parte las líneas en los símbolos (`=`, `}`, `$`) como si fueran espacios entre palabras, y una contraseña de 33 caracteres
 * terminaba en 3 o 4 renglones dejando huecos donde cabían más caracteres. Aquí **el reparto lo hacemos nosotros**: se mide cuánto
 * ocupa cada pieza con [TextMeasurer], se calcula cuántas caben por renglón en el ancho disponible y se pone un salto de línea
 * explícito entre renglones (el texto se dibuja con `softWrap = false`, así que nada más lo parte).
 *
 * El reparto se calcula **al dibujar** y no se guarda en ningún estado. Es solo de presentación: quien copia usa siempre el valor
 * original, sin saltos de línea ni espacios añadidos.
 */

/** Parte [text] en renglones de [perLine] caracteres; el último puede quedar corto. */
internal fun splitInLines(text: String, perLine: Int): List<String> =
    if (text.isEmpty()) listOf("") else text.chunked(perLine.coerceAtLeast(1))

/**
 * Cuántos caracteres de [advancePx] de ancho caben en [availablePx] (al menos 1). Se deja un píxel de margen para que un redondeo
 * no haga que el último carácter se salga.
 */
internal fun charsPerLine(availablePx: Int, advancePx: Float): Int =
    if (advancePx <= 0f) 1 else ((availablePx - 1f) / advancePx).toInt().coerceAtLeast(1)

/**
 * Reparte [groups] (por ejemplo, grupos de 4 caracteres) en renglones con **tantos grupos como quepan**, sin partir un grupo.
 * [widthOf] mide un renglón ya armado (grupos separados por un espacio).
 */
internal fun packGroups(groups: List<String>, availablePx: Int, widthOf: (String) -> Int): List<String> {
    if (groups.isEmpty()) return listOf("")
    val lines = ArrayList<String>()
    var line = groups.first()
    for (group in groups.drop(1)) {
        val candidate = "$line $group"
        if (widthOf(candidate) <= availablePx) line = candidate else {
            lines += line
            line = group
        }
    }
    lines += line
    return lines
}

/**
 * Una contraseña en fuente **monoespaciada**, carácter por carácter, en renglones completos: se mide el ancho de un carácter, se
 * calcula cuántos caben por renglón y se divide el texto en esos trozos. Funciona al girar la pantalla y con cualquier escala de
 * fuente (la medición usa la densidad y la escala actuales).
 *
 * [onLines] (opcional) recibe cuántos renglones quedaron y cuántos caracteres caben por renglón; solo para pruebas.
 */
@Composable
fun FullWidthPassword(
    password: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    onLayout: ((perLine: Int, lines: Int) -> Unit)? = null,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    // El ancho de un carácter de la fuente monoespaciada, con su espaciado: se mide una cadena larga y se divide.
    val advance = remember(style, density.density, density.fontScale) {
        measurer.measure("0".repeat(SampleChars), style, softWrap = false).size.width / SampleChars.toFloat()
    }
    BoxWithConstraints(modifier) {
        val perLine = charsPerLine(constraints.maxWidth, advance)
        val lines = splitInLines(password, perLine)
        onLayout?.invoke(perLine, lines.size)
        Text(text = lines.joinToString("\n"), style = style, color = color, softWrap = false)
    }
}

/**
 * Una clave en grupos de [groupSize] caracteres separados por un espacio, con **tantos grupos por renglón como quepan** (sin partir
 * un grupo). [raw] se da sin espacios ni saltos de línea; se ignoran si los trae.
 */
@Composable
fun FullWidthGroups(
    raw: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    groupSize: Int = 4,
    onLayout: ((lines: List<String>) -> Unit)? = null,
) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier) {
        val groups = raw.filterNot { it.isWhitespace() }.chunked(groupSize)
        val lines = packGroups(groups, constraints.maxWidth) { candidate -> measurer.measure(candidate, style, softWrap = false).size.width }
        onLayout?.invoke(lines)
        Text(text = lines.joinToString("\n"), style = style, color = color, softWrap = false)
    }
}

private const val SampleChars = 16
