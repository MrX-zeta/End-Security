package com.luis.end_security.feature.health.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luis.end_security.R
import com.luis.end_security.core.designsystem.component.BottomNavBar
import com.luis.end_security.core.designsystem.component.BottomNavTab
import com.luis.end_security.core.designsystem.component.TwoWeightTitle
import com.luis.end_security.core.designsystem.theme.PreviewSurface
import com.luis.end_security.core.security.SecureScreen
import com.luis.end_security.feature.health.domain.AnalysisItem
import com.luis.end_security.feature.health.domain.HealthAnalyzer
import com.luis.end_security.feature.health.domain.HealthProgressCalculator
import com.luis.end_security.feature.health.domain.ProgressSummary
import com.luis.end_security.feature.auth.domain.PasswordStrengthEstimator
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@Composable
fun HealthRoute(
    onTabSelected: (BottomNavTab) -> Unit,
    onOpenBreached: () -> Unit,
    onOpenRepeated: () -> Unit,
    onOpenWeak: () -> Unit,
    onOpenOld: () -> Unit,
    onOpenRanking: () -> Unit,
    viewModel: HealthViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Muestra nombres y fuerza de las contraseñas: sin capturas ni vista en Recientes.
    SecureScreen()
    HealthScreen(
        state = state,
        onTabSelected = onTabSelected,
        onTab = viewModel::onTab,
        onCheckNow = viewModel::onCheckNow,
        onOpenBreached = onOpenBreached,
        onOpenRepeated = onOpenRepeated,
        onOpenWeak = onOpenWeak,
        onOpenOld = onOpenOld,
        onOpenRanking = onOpenRanking,
    )
}

/** SCR-22 · Resumen, SCR-23 · Progreso y SCR-40 · cargando. */
@Composable
fun HealthScreen(
    state: HealthUiState,
    onTabSelected: (BottomNavTab) -> Unit,
    onTab: (HealthTab) -> Unit,
    onCheckNow: () -> Unit,
    onOpenBreached: () -> Unit,
    onOpenRepeated: () -> Unit,
    onOpenWeak: () -> Unit,
    onOpenOld: () -> Unit,
    onOpenRanking: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .weight(1f)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp)
                .padding(top = 32.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            TwoWeightTitle(
                boldLine = stringResource(R.string.health_title_bold),
                regularLine = stringResource(R.string.health_title_regular),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Normal, lineHeight = 35.sp),
            )
            SegmentedSelector(
                options = listOf(stringResource(R.string.health_tab_summary), stringResource(R.string.health_tab_progress)),
                selectedIndex = state.tab.ordinal,
                onSelect = { onTab(HealthTab.entries[it]) },
            )
            when {
                state.isLoading -> LoadingContent()
                !state.hasItems -> EmptyContent()
                state.tab == HealthTab.Summary -> SummaryContent(
                    state, onCheckNow, onOpenBreached, onOpenRepeated, onOpenWeak, onOpenOld,
                )
                else -> ProgressContent(state.progress, onOpenRanking)
            }
        }
        BottomNavBar(selected = BottomNavTab.Health, onTabSelected = onTabSelected)
    }
}

@Composable
private fun SummaryContent(
    state: HealthUiState,
    onCheckNow: () -> Unit,
    onOpenBreached: () -> Unit,
    onOpenRepeated: () -> Unit,
    onOpenWeak: () -> Unit,
    onOpenOld: () -> Unit,
) {
    val report = state.report ?: return
    val colors = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        ScoreRing(score = report.score ?: 0)
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            report.message?.let { message ->
                Text(
                    text = messageText(message),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = colors.onSurface,
                    textAlign = TextAlign.Center,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (state.checking) {
                    Text(
                        text = stringResource(R.string.health_checking),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = checkedText(state.lastChecked, state.offline) + " ·",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.health_check_now),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline),
                        color = colors.onSurface,
                        modifier = Modifier
                            .clickable(role = Role.Button, onClick = onCheckNow)
                            // 48 dp de alto al tacto.
                            .padding(vertical = 14.dp),
                    )
                }
            }
            if (report.pendingBreachCount > 0 && !state.checking) {
                Text(
                    text = pluralStringResource(R.plurals.health_pending_breach, report.pendingBreachCount, report.pendingBreachCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface, RoundedCornerShape(20.dp))
                .padding(vertical = 4.dp),
        ) {
            SummaryRow(R.drawable.ic_shield_alert_20, stringResource(R.string.health_row_breached), stringResource(R.string.health_row_breached_hint), report.breached.size, onOpenBreached)
            RowDivider()
            SummaryRow(R.drawable.ic_repeat_20, stringResource(R.string.health_row_repeated), stringResource(R.string.health_row_repeated_hint), report.repeated.size, onOpenRepeated)
            RowDivider()
            SummaryRow(R.drawable.ic_key_20, stringResource(R.string.health_row_weak), stringResource(R.string.health_row_weak_hint), report.weak.size, onOpenWeak)
            RowDivider()
            SummaryRow(R.drawable.ic_clock_20, stringResource(R.string.health_row_old), stringResource(R.string.health_row_old_hint), report.old.size, onOpenOld)
        }
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
}

@Composable
private fun EmptyContent() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_shield_check_40),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(40.dp),
        )
        Text(
            text = stringResource(R.string.health_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** SCR-40: el aro y las filas en «hueso» (Figma 69:3307) con «Revisando filtraciones…». */
@Composable
private fun LoadingContent() {
    val bone = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    val description = stringResource(R.string.health_loading_description)
    Column(
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Box(modifier = Modifier.size(200.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(200.dp)) {
                val stroke = 12.dp.toPx()
                drawCircle(bone, radius = (size.minDimension - stroke) / 2, style = Stroke(stroke))
            }
            Box(Modifier.size(width = 72.dp, height = 44.dp).background(bone, RoundedCornerShape(10.dp)))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(width = 220.dp, height = 16.dp).background(bone, RoundedCornerShape(8.dp)))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)))
                Text(
                    text = stringResource(R.string.health_checking),
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp)).padding(vertical = 4.dp),
        ) {
            listOf(130 to 170, 100 to 140, 150 to 120, 115 to 160).forEachIndexed { index, (a, b) ->
                if (index > 0) RowDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(40.dp).background(bone, RoundedCornerShape(13.dp)))
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(width = a.dp, height = 14.dp).background(bone, RoundedCornerShape(7.dp)))
                        Box(Modifier.size(width = b.dp, height = 11.dp).background(bone, RoundedCornerShape(6.dp)))
                    }
                    Box(Modifier.size(width = 18.dp, height = 14.dp).background(bone, RoundedCornerShape(7.dp)))
                }
            }
        }
    }
}

// --- Progreso (SCR-23) ---

@Composable
private fun ProgressContent(progress: ProgressSummary?, onOpenRanking: () -> Unit) {
    if (progress == null) {
        EmptyContent()
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ChartCard(progress)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            IndicatorCard(progress.strengthenedThisMonth, stringResource(R.string.health_strengthened), Modifier.weight(1f))
            IndicatorCard(progress.resolvedBreaches, stringResource(R.string.health_resolved), Modifier.weight(1f))
        }
        DistributionCard(progress, onOpenRanking)
    }
}

@Composable
private fun ChartCard(progress: ProgressSummary) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(20.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (progress.hasHistory) {
            val delta = progress.delta
            Text(
                text = when {
                    delta > 0 -> pluralStringResource(R.plurals.health_delta_up, delta, delta)
                    delta < 0 -> pluralStringResource(R.plurals.health_delta_down, -delta, -delta)
                    else -> stringResource(R.string.health_delta_same)
                },
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.52).sp),
                color = colors.onSurface,
            )
            Text(
                text = stringResource(R.string.health_delta_sub, progress.from, progress.to),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        } else {
            Text(
                text = stringResource(R.string.health_no_history_title),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.52).sp),
                color = colors.onSurface,
            )
            Text(
                text = stringResource(R.string.health_no_history_sub, progress.to),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
        Chart(progress)
    }
}

/** La gráfica de las últimas 12 semanas (Figma 62:2392): guías, línea, último punto y los valores de los extremos. */
@Composable
private fun Chart(progress: ProgressSummary) {
    val colors = MaterialTheme.colorScheme
    val locale = LocalConfiguration.current.locales[0]
    val points = progress.points
    // El eje va de 50 a 100 como en Figma; si alguna semana bajó de 50, llega hasta 0.
    val axisMin = if (points.minOf { it.score } < 50) 0 else 50
    val axisMid = (100 + axisMin) / 2
    val description = stringResource(R.string.health_chart_description, progress.from, progress.to)
    val weeks = HealthProgressCalculator.WEEKS
    val firstWeek = HealthProgressCalculator.weekStart(points.last().weekStart).minusWeeks((weeks - 1).toLong())
    val areaHeight = 130.dp
    val plotTop = 10.dp
    val plotBottom = 120.dp
    val plotLeft = 30.dp
    val plotWidth = 268.dp

    fun xFor(index: Long) = plotLeft + plotWidth * (index.toFloat() / (weeks - 1))

    fun yFor(score: Int) = plotTop + (plotBottom - plotTop) * ((100 - score).toFloat() / (100 - axisMin))

    Column(modifier = Modifier.semantics { contentDescription = description }) {
        Box(modifier = Modifier.fillMaxWidth().height(areaHeight)) {
            Canvas(Modifier.fillMaxWidth().height(areaHeight)) {
                val guide = colors.outline.copy(alpha = 0.25f)
                for (value in listOf(100, axisMid, axisMin)) {
                    val y = yFor(value).toPx()
                    drawLine(guide, Offset(27.dp.toPx(), y), Offset(299.dp.toPx(), y), strokeWidth = 1.dp.toPx())
                }
                val path = Path()
                points.forEachIndexed { i, p ->
                    val index = HealthProgressCalculator.weeksBetween(firstWeek, p.weekStart)
                    val x = xFor(index).toPx()
                    val y = yFor(p.score).toPx()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, colors.onSurface, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                val last = points.last()
                val lx = xFor(HealthProgressCalculator.weeksBetween(firstWeek, last.weekStart)).toPx()
                val ly = yFor(last.score).toPx()
                drawCircle(colors.primary, radius = 6.dp.toPx(), center = Offset(lx, ly))
                drawCircle(colors.onSurface, radius = 6.dp.toPx(), center = Offset(lx, ly), style = Stroke(2.dp.toPx()))
            }
            listOf(100, axisMid, axisMin).forEach { value ->
                Text(
                    text = value.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.offset(x = 0.dp, y = yFor(value) - 7.dp),
                )
            }
            if (progress.hasHistory) {
                Text(
                    text = progress.to.toString(),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = colors.onSurface,
                    modifier = Modifier.offset(
                        x = xFor(HealthProgressCalculator.weeksBetween(firstWeek, points.last().weekStart)) - 8.dp,
                        y = yFor(points.last().score) + 8.dp,
                    ),
                )
                Text(
                    text = progress.from.toString(),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = colors.onSurface,
                    modifier = Modifier.offset(
                        x = xFor(HealthProgressCalculator.weeksBetween(firstWeek, points.first().weekStart)),
                        y = yFor(points.first().score) + 6.dp,
                    ),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 30.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            monthLabels(firstWeek, points.last().weekStart, locale).takeLast(4).forEach { label ->
                Text(text = label, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun IndicatorCard(value: Int, label: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier.background(colors.surface, RoundedCornerShape(18.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.52).sp),
            color = colors.onSurface,
        )
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    }
}

@Composable
private fun DistributionCard(progress: ProgressSummary, onOpenRanking: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(20.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = stringResource(R.string.health_by_strength),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = colors.onSurface,
            )
            Text(
                text = pluralStringResource(R.plurals.health_total, progress.total, progress.total),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
        // Barra apilada (Figma 62:2420): débiles tenue, regulares en gris y fuertes en `on-surface`.
        Row(modifier = Modifier.fillMaxWidth().height(12.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            if (progress.weak > 0) Box(Modifier.weight(progress.weak.toFloat()).height(12.dp).background(colors.outline.copy(alpha = 0.35f), RoundedCornerShape(4.dp)))
            if (progress.fair > 0) Box(Modifier.weight(progress.fair.toFloat()).height(12.dp).background(colors.outline, RoundedCornerShape(4.dp)))
            if (progress.strong > 0) Box(Modifier.weight(progress.strong.toFloat()).height(12.dp).background(colors.onSurface, RoundedCornerShape(4.dp)))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Legend(colors.outline.copy(alpha = 0.35f), stringResource(R.string.health_legend_weak, progress.weak))
            Legend(colors.outline, stringResource(R.string.health_legend_fair, progress.fair))
            Legend(colors.onSurface, stringResource(R.string.health_legend_strong, progress.strong))
        }
        HorizontalDivider(color = colors.outline.copy(alpha = 0.25f))
        Row(
            modifier = Modifier.fillMaxWidth().heightMin48().clickable(role = Role.Button, onClick = onOpenRanking),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.health_see_ranking),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = colors.onSurface,
                modifier = Modifier.weight(1f),
            )
            Icon(painter = painterResource(R.drawable.ic_chevron_right_20), contentDescription = null, tint = colors.onSurface)
        }
    }
}

private fun Modifier.heightMin48(): Modifier = this.then(Modifier.height(48.dp))

@Composable
private fun Legend(color: androidx.compose.ui.graphics.Color, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, RoundedCornerShape(3.dp)))
        Text(text = text, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurface)
    }
}

// --- Vistas previas ---

private val PreviewEstimator = object : PasswordStrengthEstimator {
    override fun score(password: CharSequence): Int = password.toString().substring(1, 2).toInt()
}

private fun previewReport(): com.luis.end_security.feature.health.domain.HealthReport {
    val now = Instant.now()
    fun item(name: String, password: String, ageDays: Long = 20, breach: Int = 0) =
        AnalysisItem(UUID.randomUUID(), name, password, now.minusSeconds(ageDays * 86_400), breach)
    return HealthAnalyzer.analyze(
        listOf(
            item("Instagram", "s4-a", breach = 2), item("Facebook", "s4-b", breach = 1), item("BBVA", "s3-misma"),
            item("Amazon", "s3-misma"), item("Gmail", "s3-misma"), item("Netflix", "s0-x"), item("Spotify", "s4-c"),
            item("Twitch", "s2-d", ageDays = 500), item("Uber", "s4-e"),
        ),
        now, PreviewEstimator,
    )
}

private fun previewProgress(report: com.luis.end_security.feature.health.domain.HealthReport): ProgressSummary {
    val today = LocalDate.now()
    val snapshots = (1..11).map { week ->
        com.luis.end_security.feature.health.domain.SnapshotPoint(
            today.minusWeeks(week.toLong()),
            com.luis.end_security.feature.health.domain.HealthTotals(62 + (11 - week) * 2, 24, 4, 6, 14, 3, 2 + week % 2, 5),
        )
    }
    return HealthProgressCalculator.compute(snapshots, report, today, ZoneId.systemDefault())!!
}

@Preview(name = "Salud · Resumen", device = "spec:width=412dp,height=917dp")
@Composable
private fun HealthSummaryPreview() {
    PreviewSurface {
        val report = previewReport()
        HealthScreen(
            HealthUiState(isLoading = false, report = report, lastChecked = Instant.now(), progress = previewProgress(report)),
            {}, {}, {}, {}, {}, {}, {}, {},
        )
    }
}

@Preview(name = "Salud · Progreso", device = "spec:width=412dp,height=917dp")
@Composable
private fun HealthProgressPreview() {
    PreviewSurface {
        val report = previewReport()
        HealthScreen(
            HealthUiState(isLoading = false, report = report, lastChecked = Instant.now(), tab = HealthTab.Progress, progress = previewProgress(report)),
            {}, {}, {}, {}, {}, {}, {}, {},
        )
    }
}

@Preview(name = "Salud · cargando", device = "spec:width=412dp,height=917dp")
@Composable
private fun HealthLoadingPreview() {
    PreviewSurface {
        HealthScreen(HealthUiState(isLoading = true, checking = true), {}, {}, {}, {}, {}, {}, {}, {})
    }
}
