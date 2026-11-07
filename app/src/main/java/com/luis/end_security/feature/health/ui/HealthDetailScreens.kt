package com.luis.end_security.feature.health.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.luis.end_security.core.designsystem.component.rememberArrivalWindow
import com.luis.end_security.core.designsystem.component.staggeredEnter
import com.luis.end_security.core.designsystem.theme.Motion
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luis.end_security.R
import com.luis.end_security.feature.health.domain.ReuseKind
import androidx.compose.ui.platform.LocalConfiguration
import java.text.NumberFormat
import com.luis.end_security.core.designsystem.component.DirectionalContentSwap
import com.luis.end_security.core.designsystem.component.LetterTile
import com.luis.end_security.core.designsystem.component.TwoWeightTitle
import com.luis.end_security.core.designsystem.component.initialOf
import com.luis.end_security.core.designsystem.theme.PreviewSurface
import com.luis.end_security.core.security.SecureScreen
import com.luis.end_security.feature.auth.ui.signup.PasswordStrengthLevel
import com.luis.end_security.feature.health.domain.HealthReport
import com.luis.end_security.feature.health.domain.ItemHealth
import com.luis.end_security.feature.vault.domain.EditedAge
import java.time.Instant
import java.util.UUID

/** El armazón común de las pantallas internas de Salud: barra superior, contenido con desplazamiento y barra inferior. */
@Composable
private fun HealthSubScreen(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        HealthTopBar(title = title, onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp)
                .padding(top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            content()
        }
    }
}

// --- Ranking (SCR-24, SCR-25) ---

@Composable
fun HealthRankingRoute(onBack: () -> Unit, viewModel: HealthViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SecureScreen()
    HealthRankingScreen(state, onBack, viewModel::onRankingTab)
}

@Composable
fun HealthRankingScreen(
    state: HealthUiState,
    onBack: () -> Unit,
    onRankingTab: (RankingTab) -> Unit,
) {
    HealthSubScreen(stringResource(R.string.health_ranking_title), onBack) {
        SegmentedSelector(
            options = listOf(stringResource(R.string.health_ranking_weakest), stringResource(R.string.health_ranking_improved)),
            selectedIndex = state.rankingTab.ordinal,
            onSelect = { onRankingTab(RankingTab.entries[it]) },
        )
        // Cambio entre «Más fuertes» y «Más débiles»: fundido cruzado con desplazamiento en el sentido del selector.
        DirectionalContentSwap(targetIndex = state.rankingTab.ordinal) { tab ->
            val ranking = state.rankingFor(RankingTab.entries[tab])
            if (ranking.isEmpty()) {
                Text(
                    text = stringResource(if (tab == RankingTab.Improved.ordinal) R.string.health_improved_empty else R.string.health_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
                        .padding(vertical = 4.dp),
                ) {
                    val arriving = rememberArrivalWindow(Unit)
                    ranking.forEachIndexed { index, item ->
                        if (index > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                        RankingRow(position = index + 1, item = item, modifier = Modifier.staggeredEnter(index, arriving, maxStaggered = Motion.MaxStaggeredRows))
                    }
                }
            }
        }
        FootNote(stringResource(R.string.health_ranking_footnote))
    }
}

@Composable
private fun RankingRow(position: Int, item: ItemHealth, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val level = PasswordStrengthLevel.fromScore(item.strengthScore)
    val label = stringResource(if (item.strengthScore >= 4) R.string.strength_very_strong else level.label)
    val chars = pluralStringResource(R.plurals.health_chars, item.passwordLength, item.passwordLength)
    val spoken = stringResource(R.string.health_ranking_row, position, item.name, chars, label)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) { contentDescription = spoken },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = position.toString(),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = colors.onSurfaceVariant,
            modifier = Modifier.width16(),
        )
        LetterTile(letter = initialOf(item.name), size = 36.dp, cornerRadius = 11.dp, container = colors.onSurface, textStyle = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = item.name, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = colors.onSurface, maxLines = 1)
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(4) { bar ->
                    Box(
                        Modifier
                            .size(width = 14.dp, height = 5.dp)
                            .background(if (bar < level.filledBars) colors.onSurface else colors.outline.copy(alpha = 0.3f), RoundedCornerShape(3.dp)),
                    )
                }
                Text(text = "  $chars", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
        }
        Text(text = label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = colors.onSurface)
    }
}

private fun Modifier.width16(): Modifier = this.then(Modifier.size(width = 16.dp, height = 20.dp))

// --- Filtradas (SCR-26) ---

@Composable
fun HealthBreachedRoute(onBack: () -> Unit, onChange: (UUID) -> Unit, viewModel: HealthViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SecureScreen()
    HealthBreachedScreen(state.report, onBack, onChange)
}

@Composable
fun HealthBreachedScreen(report: HealthReport?, onBack: () -> Unit, onOpen: (UUID) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val items = report?.breached.orEmpty().sortedByDescending { it.breachCount }
    HealthSubScreen(stringResource(R.string.health_breached_title), onBack) {
        if (items.isEmpty()) {
            EmptyList(report?.pendingBreachCount ?: 0)
        } else {
            TwoWeightTitle(
                boldLine = pluralStringResource(R.plurals.health_breached_heading_bold, items.size, items.size),
                regularLine = pluralStringResource(R.plurals.health_breached_heading_regular, items.size),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Normal, lineHeight = 31.sp, letterSpacing = (-0.52).sp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().background(colors.errorContainer, RoundedCornerShape(18.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(painter = painterResource(R.drawable.ic_alert_20), contentDescription = null, tint = colors.error, modifier = Modifier.size(20.dp))
                Text(
                    text = stringResource(R.string.health_breached_notice),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = colors.onSurface,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val arriving = rememberArrivalWindow(Unit)
                items.forEachIndexed { index, item ->
                    VaultLikeRow(
                        modifier = Modifier.staggeredEnter(index, arriving, maxStaggered = Motion.MaxStaggeredRows),
                        name = item.name,
                        subtitle = pluralStringResource(R.plurals.health_breached_seen, item.breachCount, NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0]).format(item.breachCount)),
                        atRisk = true,
                        onClick = { onOpen(item.id) },
                    )
                }
            }
            if ((report?.pendingBreachCount ?: 0) > 0) PendingNote(report?.pendingBreachCount ?: 0)
            FootNote(stringResource(R.string.health_breached_footnote))
        }
        // Atribución de HIBP (el texto completo irá en «Acerca de», PEND-12).
        Text(
            text = stringResource(R.string.health_hibp_attribution),
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun PendingNote(count: Int) {
    Text(
        text = pluralStringResource(R.plurals.health_pending_breach, count, count),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun EmptyList(pending: Int) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_shield_check_40),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(40.dp),
        )
        Text(
            text = stringResource(R.string.health_none_good_job),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (pending > 0) PendingNote(pending)
    }
}

/** Una fila como la de la Bóveda (Figma «Elemento de bóveda»): inicial, nombre, subtítulo, triángulo de riesgo y flecha. */
@Composable
private fun VaultLikeRow(name: String, subtitle: String, atRisk: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(20.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$name, $subtitle" },
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LetterTile(letter = initialOf(name), size = 48.dp, cornerRadius = 14.dp, container = colors.onSurfaceVariant)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = name, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = colors.onSurface, maxLines = 1)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1)
        }
        if (atRisk) {
            Icon(painter = painterResource(R.drawable.ic_alert_20), contentDescription = null, tint = colors.error, modifier = Modifier.size(20.dp))
        }
        Icon(painter = painterResource(R.drawable.ic_chevron_right_20), contentDescription = null, tint = colors.onSurface)
    }
}

// --- Repetidas (SCR-27) ---

@Composable
fun HealthRepeatedRoute(onBack: () -> Unit, onChange: (UUID) -> Unit, viewModel: HealthViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SecureScreen()
    HealthRepeatedScreen(state.report, onBack, onChange)
}

@Composable
fun HealthRepeatedScreen(report: HealthReport?, onBack: () -> Unit, onChange: (UUID) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val groups = report?.repeatGroups.orEmpty()
    HealthSubScreen(stringResource(R.string.health_repeated_title), onBack) {
        if (groups.isEmpty()) {
            EmptyList(0)
        } else {
            val sites = groups.sumOf { it.items.size }
            TwoWeightTitle(
                boldLine = pluralStringResource(R.plurals.health_repeated_heading_bold, groups.size, groups.size),
                regularLine = stringResource(if (groups.size == 1) R.string.health_repeated_heading_one else R.string.health_repeated_heading_many, sites),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Normal, lineHeight = 31.sp, letterSpacing = (-0.52).sp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().background(colors.onSurface, RoundedCornerShape(18.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(painter = painterResource(R.drawable.ic_repeat_20), contentDescription = null, tint = colors.background, modifier = Modifier.size(20.dp))
                Text(
                    text = stringResource(R.string.health_repeated_notice),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = colors.background,
                )
            }
            val arriving = rememberArrivalWindow(Unit)
            groups.forEachIndexed { groupIndex, group ->
                Column(
                    modifier = Modifier
                        .staggeredEnter(groupIndex, arriving, maxStaggered = Motion.MaxStaggeredRows)
                        .fillMaxWidth()
                        .background(colors.surface, RoundedCornerShape(20.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(if (group.kind == ReuseKind.Similar) R.string.health_repeated_group_similar else R.string.health_repeated_group, group.items.size),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = colors.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        ReviewBadge()
                    }
                    group.items.forEach { item -> ChangeRow(item.name, onClick = { onChange(item.id) }) }
                }
            }
        }
    }
}

@Composable
private fun ReviewBadge() {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.height(28.dp).background(colors.primary, RoundedCornerShape(14.dp)).padding(start = 10.dp, end = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painter = painterResource(R.drawable.ic_alert_20), contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(14.dp))
        Text(
            text = stringResource(R.string.health_review_badge),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = colors.onPrimary,
        )
    }
}

/** Nombre con su inicial y «Cambiar» (48 dp al tacto): lleva a Editar ese elemento. */
@Composable
private fun ChangeRow(name: String, subtitle: String? = null, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val description = stringResource(R.string.health_change_description, name)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LetterTile(letter = initialOf(name), size = 32.dp, cornerRadius = 10.dp, container = colors.onSurface, textStyle = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = colors.onSurface, maxLines = 1)
            if (subtitle != null) Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1)
        }
        Text(
            text = stringResource(R.string.health_change),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline),
            color = colors.onSurface,
        )
    }
}

// --- Antiguas (SCR-28) ---

@Composable
fun HealthOldRoute(onBack: () -> Unit, onChange: (UUID) -> Unit, viewModel: HealthViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SecureScreen()
    HealthOldScreen(state.report, onBack, onChange)
}

@Composable
fun HealthOldScreen(report: HealthReport?, onBack: () -> Unit, onChange: (UUID) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val items = report?.old.orEmpty().sortedBy { it.passwordChangedAt }
    HealthSubScreen(stringResource(R.string.health_old_title), onBack) {
        if (items.isEmpty()) {
            EmptyList(0)
        } else {
            TwoWeightTitle(
                boldLine = pluralStringResource(R.plurals.health_old_heading_bold, items.size, items.size),
                regularLine = pluralStringResource(R.plurals.health_old_heading_regular, items.size),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Normal, lineHeight = 31.sp, letterSpacing = (-0.52).sp),
            )
            Column(
                modifier = Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(20.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val now = Instant.now()
                val arriving = rememberArrivalWindow(Unit)
                items.forEachIndexed { index, item ->
                    ChangeRow(
                        item.name,
                        subtitle = editedAgeText(EditedAge.of(item.passwordChangedAt, now)),
                        onClick = { onChange(item.id) },
                        modifier = Modifier.staggeredEnter(index, arriving, maxStaggered = Motion.MaxStaggeredRows),
                    )
                }
            }
        }
    }
}

@Composable
internal fun editedAgeText(age: EditedAge): String = when (age) {
    EditedAge.Now -> stringResource(R.string.item_edited_now)
    is EditedAge.Minutes -> pluralStringResource(R.plurals.item_edited_minutes, age.count, age.count)
    is EditedAge.Hours -> pluralStringResource(R.plurals.item_edited_hours, age.count, age.count)
    is EditedAge.Days -> pluralStringResource(R.plurals.item_edited_days, age.count, age.count)
    is EditedAge.Months -> pluralStringResource(R.plurals.item_edited_months, age.count, age.count)
    is EditedAge.Years -> pluralStringResource(R.plurals.item_edited_years, age.count, age.count)
}.replaceFirstChar { it.titlecase() }

// --- Vistas previas ---

@Preview(name = "Salud · Ranking", device = "spec:width=412dp,height=917dp")
@Composable
private fun RankingPreview() {
    PreviewSurface {
        val report = com.luis.end_security.feature.health.domain.HealthAnalyzer.analyze(
            listOf("Gmail" to "s4-a", "GitHub" to "s4-b", "Netflix" to "s3-c", "Spotify" to "s3-d", "Amazon" to "s2-e").map { (name, pass) ->
                com.luis.end_security.feature.health.domain.AnalysisItem(UUID.randomUUID(), name, pass, Instant.now(), 0)
            },
            Instant.now(),
            object : com.luis.end_security.feature.auth.domain.PasswordStrengthEstimator {
                override fun score(password: CharSequence): Int = password.toString().substring(1, 2).toInt()
            },
        )
        HealthRankingScreen(HealthUiState(isLoading = false, report = report), {}, {})
    }
}
