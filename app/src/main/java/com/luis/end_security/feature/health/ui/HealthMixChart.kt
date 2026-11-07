package com.luis.end_security.feature.health.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.luis.end_security.R
import com.luis.end_security.core.designsystem.component.rememberAnimatedFloat
import com.luis.end_security.core.designsystem.theme.EndSecurityTheme
import com.luis.end_security.core.designsystem.theme.Motion
import com.luis.end_security.feature.health.domain.HealthMix
import com.luis.end_security.feature.health.domain.HealthProgressCalculator
import com.luis.end_security.feature.health.domain.ProgressSummary
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Etiqueta de prueba de las barras por semana. */
const val HealthMixChartTag = "HealthMixChart"

/** Las cinco categorías de las barras por semana, de abajo hacia arriba. */
internal enum class MixCategory { Healthy, Old, Weak, Reused, Breached }

/** Cuántas contraseñas hay en [category] según [mix]. */
internal fun HealthMix.countOf(category: MixCategory): Int = when (category) {
    MixCategory.Healthy -> healthy
    MixCategory.Old -> old
    MixCategory.Weak -> weak
    MixCategory.Reused -> reused
    MixCategory.Breached -> breached
}

/**
 * Las barras apiladas **por semana** de Progreso (tarea 27), debajo de la línea del índice y con sus mismos márgenes y ejes. Cada
 * contraseña cuenta **solo en su categoría más grave** (filtrada > repetida o parecida > débil > antigua > sana), así que la altura de
 * la barra es el total. Las semanas con fotos de antes del desglose muestran solo el total, en gris.
 *
 * Las barras crecen desde abajo, escalonadas [Motion.Stagger] ms ([play]); si los datos cambian, van de su valor anterior al nuevo.
 * TalkBack lee cada semana como «Semana del 2 de noviembre: 15 sanas, 2 débiles, 1 filtrada».
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WeeklyMixChart(progress: ProgressSummary, play: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val locale = LocalConfiguration.current.locales[0]
    val points = progress.points
    val weeks = HealthProgressCalculator.WEEKS
    val firstWeek = HealthProgressCalculator.weekStart(points.last().weekStart).minusWeeks((weeks - 1).toLong())
    val plotLeft = 30.dp
    val plotWidth = 268.dp
    val barWidth = 12.dp
    val areaHeight = 80.dp
    val maxTotal = points.maxOf { it.mix?.total ?: it.total }.coerceAtLeast(1)

    // Los colores de cada categoría: sanas `success`, antiguas gris, débiles `primary`, repetidas o parecidas `primary` más tenue
    // (no hay un token de advertencia) y filtradas `error`.
    val palette = categoryColors()
    val grow = points.mapIndexed { index, _ ->
        key(index) { rememberAnimatedFloat(1f, fromZeroFirst = play, firstMillis = Motion.BarGrow, firstDelayMillis = index * Motion.Stagger) }
    }
    // Los valores de cada semana (5 categorías y el total sin desglose), que van del valor anterior al nuevo si los datos cambian.
    val target = points.flatMap { p ->
        val mix = p.mix
        if (mix != null) MixCategory.entries.map { mix.countOf(it).toFloat() } + 0f + 0f else List(MixCategory.entries.size) { 0f } + p.total.toFloat() + 0f
    }
    val values by rememberMorphingValues(target)
    val stride = MixCategory.entries.size + 2
    val dateFormat = DateTimeFormatter.ofPattern(stringResource(R.string.health_week_date_pattern), locale)

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.health_mix_title),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = colors.onSurface,
        )
        Box(modifier = Modifier.fillMaxWidth().height(areaHeight).testTag(HealthMixChartTag)) {
            Canvas(Modifier.fillMaxWidth().height(areaHeight).clearAndSetSemantics { }) {
                val bar = barWidth.toPx()
                val full = areaHeight.toPx()
                points.forEachIndexed { index, point ->
                    val weekIndex = HealthProgressCalculator.weeksBetween(firstWeek, point.weekStart)
                    val x = (plotLeft + plotWidth * (weekIndex.toFloat() / (weeks - 1))).toPx() - bar / 2f
                    val g = grow[index].value
                    var bottom = full
                    val base = index * stride
                    MixCategory.entries.forEachIndexed { c, category ->
                        val h = full * (values.getOrElse(base + c) { 0f } / maxTotal) * g
                        if (h > 0.5f) {
                            drawRoundRect(palette.getValue(category), Offset(x, bottom - h), Size(bar, h), CornerRadius(2.dp.toPx()))
                            bottom -= h
                        }
                    }
                    // Sin desglose (foto vieja): solo el total, en gris.
                    val plain = values.getOrElse(base + MixCategory.entries.size) { 0f }
                    if (plain > 0f) {
                        val h = full * (plain / maxTotal) * g
                        drawRoundRect(colors.outline.copy(alpha = 0.35f), Offset(x, bottom - h), Size(bar, h), CornerRadius(2.dp.toPx()))
                    }
                }
            }
            // Una zona por semana, solo para TalkBack: el valor final de la semana, nunca los números intermedios.
            points.forEach { point ->
                val weekIndex = HealthProgressCalculator.weeksBetween(firstWeek, point.weekStart)
                val x = plotLeft + plotWidth * (weekIndex.toFloat() / (weeks - 1)) - barWidth / 2
                val description = weekDescription(point.weekStart, point.mix, point.total, dateFormat)
                Box(Modifier.offset(x = x).size(barWidth, areaHeight).semantics { contentDescription = description })
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            MixCategory.entries.forEach { category ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).background(palette.getValue(category), RoundedCornerShape(3.dp)))
                    Text(
                        text = stringResource(legendOf(category)),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun categoryColors(): Map<MixCategory, Color> {
    val colors = MaterialTheme.colorScheme
    return mapOf(
        MixCategory.Healthy to EndSecurityTheme.extendedColors.success,
        MixCategory.Old to colors.outline,
        MixCategory.Weak to colors.primary,
        MixCategory.Reused to colors.primary.copy(alpha = 0.55f),
        MixCategory.Breached to colors.error,
    )
}

private fun legendOf(category: MixCategory): Int = when (category) {
    MixCategory.Healthy -> R.string.health_mix_healthy
    MixCategory.Old -> R.string.health_mix_old
    MixCategory.Weak -> R.string.health_mix_weak
    MixCategory.Reused -> R.string.health_mix_reused
    MixCategory.Breached -> R.string.health_mix_breached
}

/** «Semana del 2 de noviembre: 15 sanas, 2 débiles, 1 filtrada»; sin desglose, solo el total. */
@Composable
internal fun weekDescription(weekStart: LocalDate, mix: HealthMix?, total: Int, format: DateTimeFormatter): String {
    val date = format.format(weekStart)
    val parts = if (mix == null) {
        listOf(pluralStringResource(R.plurals.health_week_total, total, total))
    } else {
        MixCategory.entries.mapNotNull { category ->
            val n = mix.countOf(category)
            if (n == 0) null else pluralStringResource(pluralOf(category), n, n)
        }
    }
    return stringResource(R.string.health_week_description, date, parts.joinToString(", "))
}

private fun pluralOf(category: MixCategory): Int = when (category) {
    MixCategory.Healthy -> R.plurals.health_week_healthy
    MixCategory.Old -> R.plurals.health_week_old
    MixCategory.Weak -> R.plurals.health_week_weak
    MixCategory.Reused -> R.plurals.health_week_reused
    MixCategory.Breached -> R.plurals.health_week_breached
}
