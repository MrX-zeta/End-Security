package com.luis.end_security.feature.health.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luis.end_security.R
import com.luis.end_security.core.designsystem.component.BackButton
import com.luis.end_security.feature.health.domain.HealthMessage
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** «Resumen | Progreso», «Más fuertes | Más débiles»: el selector fijo de Salud (Figma 62:2352); no suma pasos al historial. */
@Composable
fun SegmentedSelector(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(16.dp))
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .background(if (selected) colors.onSurface else colors.surface, RoundedCornerShape(12.dp))
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(index) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = if (selected) colors.background else colors.onSurfaceVariant,
                )
            }
        }
    }
}

/** Barra superior de las pantallas internas de Salud: atrás y título (Figma 62:2501). */
@Composable
fun HealthTopBar(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 24.dp)
            .heightIn(min = 64.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BackButton(onClick = onBack)
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
    }
}

/** El círculo de la puntuación (Figma 47:1071): pista tenue y arco `on-surface`; «78» en 64 y «de 100». */
@Composable
fun ScoreRing(score: Int, modifier: Modifier = Modifier, size: Dp = 200.dp) {
    val colors = MaterialTheme.colorScheme
    val description = stringResource(R.string.health_score_description, score)
    Box(modifier = modifier.size(size).clearAndSetSemantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        val track = colors.outline.copy(alpha = 0.25f)
        val arc = colors.onSurface
        Canvas(Modifier.size(size)) {
            val stroke = 12.dp.toPx()
            val inset = stroke / 2
            val diameter = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), diameter, style = Stroke(stroke))
            drawArc(arc, -90f, 360f * score.coerceIn(0, 100) / 100f, false, Offset(inset, inset), diameter, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = score.toString(),
                style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-2.56).sp),
                color = colors.onSurface,
            )
            Text(text = stringResource(R.string.health_score_of), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
fun messageText(message: HealthMessage): String = stringResource(
    when (message) {
        HealthMessage.Excellent -> R.string.health_message_excellent
        HealthMessage.Good -> R.string.health_message_good
        HealthMessage.NeedsAttention -> R.string.health_message_attention
        HealthMessage.AtRisk -> R.string.health_message_risk
    },
)

/** «Revisado hoy a las 10:30», «Revisado ayer…» o «Sin revisar»; con `offline`, agrega «· sin conexión». */
@Composable
fun checkedText(lastChecked: Instant?, offline: Boolean, now: Instant = Instant.now()): String {
    val locale: Locale = LocalConfiguration.current.locales[0]
    val zone = ZoneId.systemDefault()
    val base = if (lastChecked == null) {
        stringResource(R.string.health_not_checked)
    } else {
        val local = lastChecked.atZone(zone)
        val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(local)
        val today = now.atZone(zone).toLocalDate()
        when (local.toLocalDate()) {
            today -> stringResource(R.string.health_checked_today, time)
            today.minusDays(1) -> stringResource(R.string.health_checked_yesterday, time)
            else -> stringResource(
                R.string.health_checked_date,
                DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(local), time,
            )
        }
    }
    return if (offline) base + " · " + stringResource(R.string.health_offline_suffix) else base
}

/** Una fila del resumen (Figma 47:1083): ícono, título, descripción, número y flecha; toda la fila es el botón. */
@Composable
fun SummaryRow(icon: Int, title: String, hint: String, count: Int, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 12.dp, end = 14.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$title, $count. $hint" },
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(40.dp).background(colors.background, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painter = painterResource(icon), contentDescription = null, tint = colors.onSurface, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = colors.onSurface)
            Text(text = hint, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1)
        }
        Text(text = count.toString(), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = colors.onSurfaceVariant)
        Icon(painter = painterResource(R.drawable.ic_chevron_right_20), contentDescription = null, tint = colors.onSurface)
    }
}

/** Un comentario con ícono al pie de una pantalla (Figma 62:2591): escudo y texto chico. */
@Composable
fun FootNote(text: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Icon(
            painter = painterResource(R.drawable.ic_shield_check_22),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Text(text = text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Los nombres cortos de los meses de [from] a [to] para el eje de la gráfica (sin repetir). */
fun monthLabels(from: LocalDate, to: LocalDate, locale: Locale): List<String> {
    val formatter = DateTimeFormatter.ofPattern("MMM", locale)
    val labels = ArrayList<String>()
    var day = from.withDayOfMonth(1)
    while (!day.isAfter(to)) {
        labels += formatter.format(day).replace(".", "").replaceFirstChar { it.titlecase(locale) }
        day = day.plusMonths(1)
    }
    return labels
}
