package com.luis.end_security.feature.health.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
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
import com.luis.end_security.core.designsystem.component.FadeOnChange
import com.luis.end_security.core.designsystem.component.LoadingCrossfade
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.saveable.rememberSaveable
import com.luis.end_security.core.designsystem.component.HostedMessage
import com.luis.end_security.core.designsystem.component.DirectionalContentSwap
import com.luis.end_security.core.designsystem.component.rememberAnimatedFloat
import com.luis.end_security.core.designsystem.component.staggeredEnter
import com.luis.end_security.core.designsystem.theme.Motion
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
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
import com.luis.end_security.core.designsystem.component.TwoWeightTitle
import com.luis.end_security.core.designsystem.theme.PreviewSurface
import com.luis.end_security.core.security.SecureScreen
import com.luis.end_security.feature.health.domain.AnalysisItem
import com.luis.end_security.feature.health.domain.HealthAnalyzer
import com.luis.end_security.feature.health.domain.HealthProgressCalculator
import com.luis.end_security.feature.health.domain.ProgressSummary
import com.luis.end_security.feature.auth.domain.PasswordStrengthEstimator
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@Composable
fun HealthRoute(
    onOpenBreached: () -> Unit,
    onOpenRepeated: () -> Unit,
    onOpenWeak: () -> Unit,
    onOpenOld: () -> Unit,
    onOpenRanking: () -> Unit,
    viewModel: HealthViewModel = hiltViewModel(),
) {
    // Al ir a un detalle, el regreso con Atrás no repite las animaciones.
    val entrance = viewModel.entrance
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Muestra nombres y fuerza de las contraseñas: sin capturas ni vista en Recientes.
    SecureScreen()
    HealthScreen(
        state = state,
        onTab = viewModel::onTab,
        onCheckNow = viewModel::onCheckNow,
        onOpenBreached = { entrance.leavingForDetail(); onOpenBreached() },
        onOpenRepeated = { entrance.leavingForDetail(); onOpenRepeated() },
        onOpenWeak = { entrance.leavingForDetail(); onOpenWeak() },
        onOpenOld = { entrance.leavingForDetail(); onOpenOld() },
        onOpenRanking = { entrance.leavingForDetail(); onOpenRanking() },
        entrance = entrance,
    )
}

/** SCR-22 · Resumen, SCR-23 · Progreso y SCR-40 · cargando. */
@Composable
fun HealthScreen(
    state: HealthUiState,
    onTab: (HealthTab) -> Unit,
    onCheckNow: () -> Unit,
    onOpenBreached: () -> Unit,
    onOpenRepeated: () -> Unit,
    onOpenWeak: () -> Unit,
    onOpenOld: () -> Unit,
    onOpenRanking: () -> Unit,
    modifier: Modifier = Modifier,
    /** Cuándo se animan las vistas; la da el ViewModel para que sobreviva a salir y volver a la pestaña. */
    entrance: HealthEntrance = remember { HealthEntrance() },
) {
    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(modifier = Modifier.weight(1f)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
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
                // Del esqueleto («Revisando filtraciones…») al contenido: fundido cruzado de 200 ms.
                val phase = when {
                    state.isLoading -> 0
                    !state.hasItems -> 1
                    else -> 2
                }
                LoadingCrossfade(phase) { current ->
                    when (current) {
                        0 -> LoadingContent()
                        1 -> EmptyContent()
                        else -> DirectionalContentSwap(targetIndex = state.tab.ordinal) { tab ->
                            if (tab == HealthTab.Summary.ordinal) {
                                SummaryContent(state, entrance, onCheckNow, onOpenBreached, onOpenRepeated, onOpenWeak, onOpenOld)
                            } else {
                                ProgressContent(state.progress, entrance, onOpenRanking)
                            }
                        }
                    }
                }
            }
            OfflineNotice(
                id = state.offlineNoticeId,
                modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 32.dp, vertical = 8.dp),
            )
        }
    }
}

/** «Sin conexión. Te mostramos la última revisión.» unos segundos tras tocar «Revisar ahora» sin red; [id] cambia en cada aviso. */
@Composable
private fun OfflineNotice(id: Int, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(id) {
        if (id > 0) {
            visible = true
            delay(NOTICE_MILLIS)
            visible = false
        }
    }
    if (visible) HostedMessage(text = stringResource(R.string.health_offline_notice), icon = R.drawable.ic_wifi_off_20, modifier = modifier)
}

private const val NOTICE_MILLIS = 4_000L

/** Etiquetas de prueba de la gráfica de 12 semanas y de la barra «Por fuerza». */
const val HealthChartTag = "HealthChart"
const val HealthStrengthBarTag = "HealthStrengthBar"

@Composable
private fun SummaryContent(
    state: HealthUiState,
    entrance: HealthEntrance,
    onCheckNow: () -> Unit,
    onOpenBreached: () -> Unit,
    onOpenRepeated: () -> Unit,
    onOpenWeak: () -> Unit,
    onOpenOld: () -> Unit,
) {
    val report = state.report ?: return
    val colors = MaterialTheme.colorScheme
    // Cada vez que se entra al Resumen (no al recomponer ni al volver de un detalle) el aro cuenta, el número sube y todo entra
    // escalonado; la primera vez de la sesión en 600 ms y las repeticiones en 400.
    val mode = remember { entrance.modeFor(HealthTab.Summary) }
    val firstTime = mode != EntranceMode.None
    val countMillis = if (mode == EntranceMode.First) Motion.CountUp else Motion.ChartReplay
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        ScoreRing(score = report.score ?: 0, modifier = Modifier.staggeredEnter(0, firstTime), animateIn = firstTime, animateMillis = countMillis)
        Column(
            modifier = Modifier.staggeredEnter(1, firstTime),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            report.message?.let { message ->
                Text(
                    text = messageText(message),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = colors.onSurface,
                    textAlign = TextAlign.Center,
                )
            }
            if (state.checking) {
                Text(
                    text = stringResource(R.string.health_checking),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            } else {
                // Una línea con la fecha y, debajo, «Revisar ahora» como botón de texto en su propia línea (48 dp al tacto).
                // «Revisado hoy a las…» cambia en su lugar: fundido de 150 ms.
                FadeOnChange(checkedText(state.lastChecked)) { text ->
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                Box(
                    modifier = Modifier
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .clip(MaterialTheme.shapes.small)
                        .clickable(role = Role.Button, onClick = onCheckNow)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.health_check_now),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline),
                        color = colors.onSurface,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            // «N de M contraseñas están sanas»; con filtradas, qué hacer para subir el índice (el índice tiene tope por ellas).
            val total = report.items.size
            Text(
                text = pluralStringResource(R.plurals.health_healthy_count, total, report.healthyCount, total),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (report.breached.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.health_breached_cap_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
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
            SummaryRow(R.drawable.ic_shield_alert_20, stringResource(R.string.health_row_breached), stringResource(R.string.health_row_breached_hint), report.breached.size, onOpenBreached, Modifier.staggeredEnter(2, firstTime))
            RowDivider()
            SummaryRow(R.drawable.ic_repeat_20, stringResource(R.string.health_row_repeated), stringResource(R.string.health_row_repeated_hint), report.repeated.size, onOpenRepeated, Modifier.staggeredEnter(3, firstTime))
            RowDivider()
            SummaryRow(R.drawable.ic_key_20, stringResource(R.string.health_row_weak), stringResource(R.string.health_row_weak_hint), report.weak.size, onOpenWeak, Modifier.staggeredEnter(4, firstTime))
            RowDivider()
            SummaryRow(R.drawable.ic_clock_20, stringResource(R.string.health_row_old), stringResource(R.string.health_row_old_hint), report.old.size, onOpenOld, Modifier.staggeredEnter(5, firstTime))
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
private fun ProgressContent(progress: ProgressSummary?, entrance: HealthEntrance, onOpenRanking: () -> Unit) {
    if (progress == null) {
        EmptyContent()
        return
    }
    // Cada vez que se entra al Progreso: las tarjetas entran escalonadas y la gráfica y las barras se dibujan.
    val mode = remember { entrance.modeFor(HealthTab.Progress) }
    val firstTime = mode != EntranceMode.None
    val drawMillis = if (mode == EntranceMode.First) Motion.ChartDraw else Motion.ChartReplay
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ChartCard(progress, firstTime, drawMillis, Modifier.staggeredEnter(0, firstTime))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            IndicatorCard(progress.strengthenedThisMonth, stringResource(R.string.health_strengthened), Modifier.weight(1f).staggeredEnter(1, firstTime))
            IndicatorCard(progress.resolvedBreaches, stringResource(R.string.health_resolved), Modifier.weight(1f).staggeredEnter(2, firstTime))
        }
        DistributionCard(progress, firstTime, onOpenRanking, Modifier.staggeredEnter(3, firstTime))
    }
}

@Composable
private fun ChartCard(progress: ProgressSummary, firstTime: Boolean, drawMillis: Int, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(20.dp)).padding(20.dp),
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
        Chart(progress, firstTime, drawMillis)
        // Debajo de la línea del índice: cuántas contraseñas hay en cada categoría por semana.
        WeeklyMixChart(progress, firstTime)
    }
}

/** La gráfica de las últimas 12 semanas (Figma 62:2392): guías, línea, último punto y los valores de los extremos. */
@Composable
private fun Chart(progress: ProgressSummary, firstTime: Boolean, drawMillis: Int) {
    val colors = MaterialTheme.colorScheme
    val locale = LocalConfiguration.current.locales[0]
    val points = progress.points
    // La línea se traza de izquierda a derecha la primera vez (ChartDraw); si los datos cambian después, los puntos van de su valor
    // anterior al nuevo en ValueChange, sin volver a trazarse.
    val drawProgress by rememberAnimatedFloat(1f, fromZeroFirst = firstTime, firstMillis = drawMillis)
    val scores by rememberMorphingValues(points.map { it.score.toFloat() })
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

    fun yFor(score: Float) = plotTop + (plotBottom - plotTop) * ((100f - score) / (100 - axisMin))

    fun yFor(score: Int) = yFor(score.toFloat())

    Column(modifier = Modifier.semantics { contentDescription = description }) {
        Box(modifier = Modifier.fillMaxWidth().height(areaHeight).testTag(HealthChartTag)) {
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
                    val y = yFor(scores.getOrElse(i) { p.score.toFloat() }).toPx()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                // El trazo avanza recortándolo con un rectángulo que crece hacia la derecha.
                val revealRight = if (drawProgress >= 1f) size.width else plotLeft.toPx() + plotWidth.toPx() * drawProgress
                clipRect(left = 0f, top = 0f, right = revealRight, bottom = size.height) {
                    drawPath(path, colors.onSurface, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                // El último punto aparece cuando la línea llega a él.
                if (drawProgress >= 1f) {
                    val last = points.last()
                    val lx = xFor(HealthProgressCalculator.weeksBetween(firstWeek, last.weekStart)).toPx()
                    val ly = yFor(scores.getOrElse(points.lastIndex) { last.score.toFloat() }).toPx()
                    drawCircle(colors.primary, radius = 6.dp.toPx(), center = Offset(lx, ly))
                    drawCircle(colors.onSurface, radius = 6.dp.toPx(), center = Offset(lx, ly), style = Stroke(2.dp.toPx()))
                }
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
                    ).graphicsLayer { alpha = drawProgress },
                )
                Text(
                    text = progress.from.toString(),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = colors.onSurface,
                    modifier = Modifier.offset(
                        x = xFor(HealthProgressCalculator.weeksBetween(firstWeek, points.first().weekStart)),
                        y = yFor(points.first().score) + 6.dp,
                    ).graphicsLayer { alpha = drawProgress },
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
private fun DistributionCard(progress: ProgressSummary, firstTime: Boolean, onOpenRanking: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    // Las barras crecen de cero a su ancho (BarGrow), escalonadas Stagger ms; si los datos cambian, van del valor anterior al nuevo.
    val total = (progress.weak + progress.fair + progress.strong).coerceAtLeast(1)
    val totalShown by rememberAnimatedFloat(total.toFloat())
    val weakShown by rememberAnimatedFloat(progress.weak.toFloat(), firstTime, Motion.BarGrow, Motion.ValueChange, 0)
    val fairShown by rememberAnimatedFloat(progress.fair.toFloat(), firstTime, Motion.BarGrow, Motion.ValueChange, Motion.Stagger)
    val strongShown by rememberAnimatedFloat(progress.strong.toFloat(), firstTime, Motion.BarGrow, Motion.ValueChange, 2 * Motion.Stagger)
    val weakColor = colors.outline.copy(alpha = 0.35f)
    Column(
        modifier = modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(20.dp)).padding(20.dp),
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
        Canvas(modifier = Modifier.fillMaxWidth().height(12.dp).testTag(HealthStrengthBarTag)) {
            val segments = listOf(weakShown to weakColor, fairShown to colors.outline, strongShown to colors.onSurface).filter { it.first > 0.01f }
            val gap = 2.dp.toPx()
            val available = size.width - gap * (segments.size - 1).coerceAtLeast(0)
            val denominator = totalShown.coerceAtLeast(1f)
            var x = 0f
            for ((value, color) in segments) {
                val width = (available * value / denominator).coerceAtLeast(0f)
                drawRoundRect(color, topLeft = Offset(x, 0f), size = Size(width, size.height), cornerRadius = CornerRadius(4.dp.toPx()))
                x += width + gap
            }
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
            {}, {}, {}, {}, {}, {}, {},
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
            {}, {}, {}, {}, {}, {}, {},
        )
    }
}

@Preview(name = "Salud · cargando", device = "spec:width=412dp,height=917dp")
@Composable
private fun HealthLoadingPreview() {
    PreviewSurface {
        HealthScreen(HealthUiState(isLoading = true, checking = true), {}, {}, {}, {}, {}, {}, {})
    }
}
