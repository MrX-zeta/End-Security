package com.luis.end_security.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.luis.end_security.core.designsystem.theme.EndSecurityTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Renglones completos (tarea 26): con 16, 33 y 64 caracteres, a 360 y 412 dp y con la fuente al 130 %, ningún renglón (salvo el
 * último) deja espacio para otro carácter, y el texto repartido es exactamente el original.
 */
@RunWith(AndroidJUnit4::class)
class FullWidthTextInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    // El estilo de la tarjeta del Generador.
    private val passwordStyle = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = 1.4.sp)

    // El de la clave del 2FA.
    private val keyStyle = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = 2.sp, lineHeight = 30.sp)

    private fun password(length: Int) = buildString { repeat(length) { append("Ab3\$xZ9=}q&L@u7#"[it % 16]) } }

    private fun checkPassword(length: Int, widthDp: Int, fontScale: Float = 1f) {
        val text = password(length)
        var perLine = 0
        var lines = 0
        var measurer: TextMeasurer? = null
        var availablePx = 0
        var density: Density? = null
        composeRule.setContent {
            val base = LocalDensity.current
            val scaled = Density(base.density, fontScale)
            CompositionLocalProvider(LocalDensity provides scaled) {
                EndSecurityTheme {
                    measurer = rememberTextMeasurer()
                    density = LocalDensity.current
                    // La tarjeta del Generador: 24 dp de relleno a cada lado.
                    Box(Modifier.width(widthDp.dp).padding(24.dp)) {
                        FullWidthPassword(text, passwordStyle, Color.Black, onLayout = { p, l -> perLine = p; lines = l })
                    }
                }
            }
        }
        composeRule.waitForIdle()
        availablePx = with(density!!) { (widthDp.dp - 48.dp).roundToPx() }
        val m = measurer!!
        fun widthOf(n: Int) = m.measure("0".repeat(n), passwordStyle, softWrap = false, density = density!!).size.width
        assertTrue("caben $perLine por renglón y no llegan a pasarse del ancho ($availablePx px)", widthOf(perLine) <= availablePx)
        assertTrue("un carácter más ya no cabría ($length car., $widthDp dp, x$fontScale)", widthOf(perLine + 1) > availablePx)
        assertEquals("los renglones cubren todo el texto", (length + perLine - 1) / perLine, lines)
        // El reparto nunca cambia el texto: unir los renglones da el original.
        assertEquals(text, splitInLines(text, perLine).joinToString(""))
        assertTrue("todos los renglones menos el último van completos", splitInLines(text, perLine).dropLast(1).all { it.length == perLine })
    }

    @Test fun contrasena_de_16_a_360() = checkPassword(16, 360)

    @Test fun contrasena_de_16_a_412() = checkPassword(16, 412)

    @Test fun contrasena_de_33_a_360() = checkPassword(33, 360)

    @Test fun contrasena_de_33_a_412() = checkPassword(33, 412)

    @Test fun contrasena_de_64_a_360() = checkPassword(64, 360)

    @Test fun contrasena_de_64_a_412() = checkPassword(64, 412)

    @Test fun contrasena_de_33_con_letra_al_130_por_ciento() = checkPassword(33, 360, fontScale = 1.3f)

    @Test fun contrasena_de_64_con_letra_al_130_por_ciento() = checkPassword(64, 412, fontScale = 1.3f)

    private fun checkKey(widthDp: Int, reservedDp: Int = 100) {
        val raw = "YRHLPYJNZ4BTHQ5R2FN7U5MUHTHG3U2Y"
        var shown: List<String> = emptyList()
        var measurer: TextMeasurer? = null
        var density: Density? = null
        composeRule.setContent {
            EndSecurityTheme {
                measurer = rememberTextMeasurer()
                density = LocalDensity.current
                // La tarjeta de la clave: 20 dp de relleno, el botón de copiar (48) y su separación (12).
                Box(Modifier.width((widthDp - reservedDp).dp)) {
                    FullWidthGroups(raw, keyStyle, Color.Black, onLayout = { shown = it })
                }
            }
        }
        composeRule.waitForIdle()
        val available = with(density!!) { (widthDp - reservedDp).dp.roundToPx() }
        val m = measurer!!
        fun widthOf(line: String) = m.measure(line, keyStyle, softWrap = false, density = density!!).size.width
        assertEquals("los grupos de 4 se conservan enteros y en orden", raw.chunked(4), shown.flatMap { it.split(" ") })
        shown.forEach { assertTrue("el renglón «$it» cabe", widthOf(it) <= available) }
        // Ningún renglón (salvo el último) deja espacio para otro grupo.
        val groups = raw.chunked(4)
        var index = 0
        shown.dropLast(1).forEach { line ->
            index += line.split(" ").size
            assertTrue("«$line» no dejó lugar para «${groups[index]}»", widthOf("$line ${groups[index]}") > available)
        }
    }

    @Test fun clave_a_360() = checkKey(360)

    @Test fun clave_a_412() = checkKey(412)

    @Test
    fun la_clave_a_412_aprovecha_mas_que_dos_grupos_por_renglon() {
        var shown: List<String> = emptyList()
        composeRule.setContent {
            EndSecurityTheme { Box(Modifier.width(312.dp)) { FullWidthGroups("YRHLPYJNZ4BTHQ5R2FN7U5MUHTHG3U2Y", keyStyle, Color.Black, onLayout = { shown = it }) } }
        }
        composeRule.waitForIdle()
        assertTrue("antes eran 4 renglones de 2 grupos; ahora caben más ($shown)", shown.first().split(" ").size > 2)
    }
}
