package com.luis.end_security.core.designsystem.component

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.luis.end_security.core.designsystem.theme.EndSecurityTheme
import com.luis.end_security.core.designsystem.theme.PreviewSurface
import kotlin.math.roundToInt

/**
 * Las tres manchas de la biblioteca de Figma («mancha/a», «mancha/b», «mancha/c»).
 * El trazo se escala sin conservar proporción, igual que en Figma (preserveAspectRatio="none").
 */
enum class BlobShape(
    private val pathData: String,
    private val viewportWidth: Float,
    private val viewportHeight: Float,
) {
    A(
        pathData = "M167.75 33.18C193.49 51.135 209.44 83.265 205.26 112.98C201.08 142.695 176.77 169.995 146.96 184.065" +
            "C117.15 198.135 81.84 198.975 54.56 184.695C27.28 170.415 8.03 141.015 11.44 112.875" +
            "C14.85 84.735 40.92 57.855 69.63 40.005C98.34 22.155 142.01 15.225 167.75 33.18Z",
        viewportWidth = 220f,
        viewportHeight = 210f,
    ),
    B(
        pathData = "M282.81 97.185C314.655 142.89 310.53 216.81 267.465 260.535C224.4 304.26 142.395 317.79 89.595 284.46" +
            "C36.795 251.13 13.2 170.94 40.095 116.325C66.99 61.71 144.375 32.67 200.64 36.135" +
            "C256.905 39.6 250.965 51.48 282.81 97.185Z",
        viewportWidth = 330f,
        viewportHeight = 330f,
    ),
    C(
        pathData = "M49.2 173.34C40.4 110.52 105.6 52.02 190.8 44.1C276 36.18 361.8 73.08 376.6 140.04" +
            "C391.4 207 339.2 276.3 257.8 298.98C176.4 321.66 58 236.16 49.2 173.34Z",
        viewportWidth = 400f,
        viewportHeight = 360f,
    ),
    ;

    private val basePath: Path by lazy { PathParser().parsePathString(pathData).toPath() }

    /** Devuelve una copia del trazo escalada al tamaño indicado. */
    internal fun pathFor(size: Size): Path = Path().apply {
        addPath(basePath)
        transform(Matrix().apply { scale(size.width / viewportWidth, size.height / viewportHeight) })
    }
}

/**
 * Mancha orgánica de fondo (DEC-50): máximo dos por pantalla y solo una con grano.
 * Es decorativa, así que no tiene semántica para TalkBack.
 */
@Composable
fun OrganicBlob(
    shape: BlobShape,
    modifier: Modifier = Modifier,
    grain: Boolean = false,
) {
    val colors = EndSecurityTheme.extendedColors
    val blobColor = colors.blob
    val grainTile = if (grain) rememberGrainTile(colors.blobGrain) else null
    Box(
        modifier = modifier.drawWithCache {
            val path = shape.pathFor(size)
            val grainBrush = grainTile?.let { ShaderBrush(ImageShader(it, TileMode.Repeated, TileMode.Repeated)) }
            onDrawBehind {
                drawPath(path, blobColor)
                if (grainBrush != null) drawPath(path, grainBrush)
            }
        },
    )
}

/**
 * Mosaico del grano: puntos del color del token sobre transparente, como el ruido de Figma
 * (aprox. una cuarta parte de los puntos encendidos, de unos 0.6 dp).
 * El patrón es fijo y se calcula con un hash entero: no usa `Random` (regla 4 de CLAUDE.md)
 * porque aquí no hace falta aleatoriedad.
 */
@Composable
private fun rememberGrainTile(color: Color): ImageBitmap {
    val density = LocalDensity.current.density
    return remember(color, density) {
        val cell = (density * GrainCellDp).roundToInt().coerceAtLeast(1)
        val tileSize = GrainTileCells * cell
        val argb = color.toArgb()
        val pixels = IntArray(tileSize * tileSize)
        for (cy in 0 until GrainTileCells) {
            for (cx in 0 until GrainTileCells) {
                if ((grainHash(cx, cy) and 0xFFFF) >= GrainThreshold) continue
                for (dy in 0 until cell) {
                    val row = (cy * cell + dy) * tileSize
                    for (dx in 0 until cell) pixels[row + cx * cell + dx] = argb
                }
            }
        }
        Bitmap.createBitmap(pixels, tileSize, tileSize, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
}

private const val GrainCellDp = 0.6f
private const val GrainTileCells = 128
private const val GrainThreshold = 0x4000 // 25 % de 0x10000

private fun grainHash(x: Int, y: Int): Int {
    var h = x * 374_761_393 + y * 668_265_263
    h = (h xor (h ushr 13)) * 1_274_126_177
    return h xor (h ushr 16)
}

@Preview(name = "Manchas orgánicas")
@Composable
private fun OrganicBlobPreview() {
    PreviewSurface(darkTheme = true, modifier = Modifier.padding(16.dp)) {
        Box(Modifier.size(width = 400.dp, height = 360.dp)) {
            OrganicBlob(shape = BlobShape.C, grain = true, modifier = Modifier.size(400.dp, 360.dp))
            OrganicBlob(shape = BlobShape.A, modifier = Modifier.size(220.dp, 210.dp))
        }
    }
}
