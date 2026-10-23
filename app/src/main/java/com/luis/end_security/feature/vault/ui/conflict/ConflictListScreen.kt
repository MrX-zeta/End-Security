package com.luis.end_security.feature.vault.ui.conflict

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luis.end_security.R
import com.luis.end_security.core.designsystem.component.BackButton
import com.luis.end_security.core.designsystem.component.BottomNavBar
import com.luis.end_security.core.designsystem.component.BottomNavTab
import com.luis.end_security.core.designsystem.component.LetterTile
import com.luis.end_security.core.designsystem.component.MessageBar
import com.luis.end_security.core.designsystem.component.TwoWeightTitle
import com.luis.end_security.core.designsystem.theme.EndSecurityTheme
import com.luis.end_security.core.designsystem.theme.PreviewSurface
import com.luis.end_security.core.security.SecureScreen
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import java.util.UUID

private val RowShape = RoundedCornerShape(18.dp)

@Composable
fun ConflictListRoute(
    onBack: () -> Unit,
    onOpen: (UUID) -> Unit,
    onTabSelected: (BottomNavTab) -> Unit,
    viewModel: ConflictListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnBack by rememberUpdatedState(onBack)
    val currentOnOpen by rememberUpdatedState(onOpen)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ConflictListEffect.Open -> currentOnOpen(effect.id)
                ConflictListEffect.Close -> currentOnBack()
            }
        }
    }
    // Muestra nombres de contraseñas: sin capturas ni vista en Recientes.
    SecureScreen()
    ConflictListScreen(state = state, onBack = onBack, onRowClick = viewModel::onRowClick, onTabSelected = onTabSelected)
}

/** SCR-19 · Por resolver, y SCR-20 con el aviso «{nombre} quedó resuelta». */
@Composable
fun ConflictListScreen(
    state: ConflictListUiState,
    onBack: () -> Unit,
    onRowClick: (ConflictListRow) -> Unit,
    onTabSelected: (BottomNavTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .height(64.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BackButton(onClick = onBack)
                    Text(
                        text = stringResource(R.string.conflicts_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .padding(start = 14.dp)
                            .semantics { heading() },
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 32.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TwoWeightTitle(
                        boldLine = pluralStringResource(R.plurals.conflicts_heading_bold, state.rows.size, state.rows.size),
                        regularLine = pluralStringResource(R.plurals.conflicts_heading_regular, state.rows.size),
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Normal, lineHeight = 35.sp),
                    )
                    Text(
                        text = stringResource(R.string.conflicts_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                    )
                    state.rows.forEach { row -> ConflictRowItem(row = row, onClick = { onRowClick(row) }) }
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_info_16),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .size(16.dp),
                        )
                        Text(
                            text = stringResource(R.string.conflicts_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            state.resolvedName?.let { name ->
                MessageBar(
                    text = if (name.isEmpty()) {
                        stringResource(R.string.conflicts_resolved_generic)
                    } else {
                        stringResource(R.string.conflicts_resolved, name)
                    },
                    icon = R.drawable.ic_check_16,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 32.dp)
                        .padding(bottom = 16.dp),
                )
            }
        }
        BottomNavBar(selected = BottomNavTab.Vault, onTabSelected = onTabSelected)
    }
}

@Composable
private fun ConflictRowItem(row: ConflictListRow, onClick: () -> Unit) {
    val title = row.name ?: stringResource(R.string.conflicts_unnamed)
    val whenText = stringResource(R.string.conflicts_row_when, whenText(row.localAt))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .dropShadow(
                shape = RowShape,
                shadow = Shadow(radius = 20.dp, color = EndSecurityTheme.extendedColors.shadowDark, offset = DpOffset(0.dp, 8.dp)),
            )
            .background(MaterialTheme.colorScheme.surface, RowShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(16.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$title, $whenText" },
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LetterTile(letter = row.initial)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = whenText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right_20),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** «hoy, 10:31», «ayer, 21:05» o «22 oct 2026, 21:05», en la zona horaria y el idioma del teléfono. */
@Composable
fun whenText(at: Instant, capitalized: Boolean = false): String {
    val locale: Locale = LocalConfiguration.current.locales[0]
    val zone = ZoneId.systemDefault()
    val local = at.atZone(zone)
    val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(local)
    val today = LocalDate.now(zone)
    val text = when (local.toLocalDate()) {
        today -> stringResource(R.string.conflicts_when_today, time)
        today.minusDays(1) -> stringResource(R.string.conflicts_when_yesterday, time)
        else -> stringResource(R.string.conflicts_when_date, DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(local), time)
    }
    return if (capitalized) text.replaceFirstChar { it.titlecase(locale) } else text
}

private val PreviewRows = listOf(
    ConflictListRow(UUID(0, 1), "Netflix", "N", Instant.now()),
    ConflictListRow(UUID(0, 2), "Spotify", "S", Instant.now().minusSeconds(3_600)),
    ConflictListRow(UUID(0, 3), "Amazon", "A", Instant.now().minusSeconds(86_400)),
)

@Preview(name = "Por resolver", device = "spec:width=412dp,height=917dp")
@Composable
private fun ConflictListPreview() {
    PreviewSurface {
        ConflictListScreen(ConflictListUiState(PreviewRows, isLoading = false), {}, {}, {})
    }
}

@Preview(name = "Por resolver · 1 resuelta", device = "spec:width=412dp,height=917dp")
@Composable
private fun ConflictListResolvedPreview() {
    PreviewSurface {
        ConflictListScreen(ConflictListUiState(PreviewRows.drop(1), resolvedName = "Netflix", isLoading = false), {}, {}, {})
    }
}
