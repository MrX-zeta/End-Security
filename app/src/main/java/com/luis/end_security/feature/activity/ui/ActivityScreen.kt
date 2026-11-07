package com.luis.end_security.feature.activity.ui

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luis.end_security.R
import com.luis.end_security.core.designsystem.component.ListCard
import com.luis.end_security.core.designsystem.component.ListRowDivider
import com.luis.end_security.core.designsystem.component.ListRowIcon
import com.luis.end_security.core.designsystem.component.PrimaryButton
import com.luis.end_security.feature.activity.domain.ActivityDay
import com.luis.end_security.feature.activity.domain.ActivityEvent
import com.luis.end_security.feature.activity.domain.ActivityRules
import com.luis.end_security.feature.activity.domain.DayLabel
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

/**
 * SCR-34 · Actividad reciente (Figma 47:1938, cargando 69:3513). Agrupada por día local; EP-25 con paginación hacia atrás
 * (50 por página). No muestra secretos: no lleva `SecureScreen`.
 */
@Composable
fun ActivityRoute(
    onBack: () -> Unit,
    onChangePassword: () -> Unit,
    viewModel: ActivityViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Pasada la medianoche local, «Hoy» pasa a ser «Ayer»: se recalcula a esa hora y al volver a la pantalla.
    LaunchedEffect(state.today) {
        delay(ActivityRules.millisUntilNextDay(Instant.now(), ZoneId.systemDefault()))
        viewModel.onAction(ActivityAction.DayChanged)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onAction(ActivityAction.DayChanged) }
    ActivityScreen(
        state = state,
        onAction = viewModel::onAction,
        onBack = onBack,
        onChangePassword = onChangePassword,
    )
}

@Composable
fun ActivityScreen(
    state: ActivityUiState,
    onAction: (ActivityAction) -> Unit,
    onBack: () -> Unit,
    onChangePassword: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AccountListPage(
        title = stringResource(R.string.activity_title),
        onBack = onBack,
        modifier = modifier,
        bottom = if (state.phase == ActivityPhase.Offline) {
            {
                PrimaryButton(
                    text = stringResource(R.string.devices_retry),
                    onClick = { onAction(ActivityAction.Retry) },
                    showArrow = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            null
        },
    ) {
        when (state.phase) {
            ActivityPhase.Loading -> ActivitySkeleton(loadingText = stringResource(R.string.activity_loading_description))
            ActivityPhase.Offline -> OfflinePanel(
                title = stringResource(R.string.activity_offline_title),
                body = stringResource(R.string.activity_offline_body),
            )
            ActivityPhase.Loaded -> ActivityList(state, onAction, onChangePassword)
        }
    }
}

@Composable
private fun ActivityList(state: ActivityUiState, onAction: (ActivityAction) -> Unit, onChangePassword: () -> Unit) {
    val context = LocalContext.current
    val timeFormat = remember(context) { DateFormat.getTimeFormat(context) }
    val currentOnAction by rememberUpdatedState(onAction)
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = ListPagePadding, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(state.days, key = { it.date.toEpochDay() }) { day ->
            DayBlock(day = day, today = state.today, timeOf = { timeFormat.format(Date(it.occurredAt.toEpochMilli())) })
        }
        item(key = "more") {
            when {
                state.loadingMore -> {
                    val description = stringResource(R.string.activity_loading_more_description)
                    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(20.dp)
                                .semantics { contentDescription = description; liveRegion = LiveRegionMode.Polite },
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                state.loadMoreFailed -> Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.activity_load_more_failed) + " ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.activity_load_more_retry),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .clickable(role = Role.Button) { currentOnAction(ActivityAction.Retry) },
                            
                    )
                }
                // Al llegar aquí (la lista es perezosa: solo se compone al desplazarse hasta el final) se pide la siguiente página.
                state.hasMore -> LaunchedEffect(state.events.size) { currentOnAction(ActivityAction.LoadMore) }
            }
        }
        item(key = "footer") {
            val before = stringResource(R.string.activity_footer_before)
            val link = stringResource(R.string.activity_footer_link)
            val currentOnChangePassword by rememberUpdatedState(onChangePassword)
            val linkStyle = TextLinkStyles(
                SpanStyle(fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline, color = MaterialTheme.colorScheme.onSurface),
            )
            Text(
                text = buildAnnotatedString {
                    append(before)
                    append("  ")
                    withLink(LinkAnnotation.Clickable("change-password", linkStyle) { currentOnChangePassword() }) { append(link) }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
    }
}

/** El título del día («Hoy», «Ayer» o la fecha) y su tarjeta de filas. */
@Composable
private fun DayBlock(day: ActivityDay, today: LocalDate, timeOf: (ActivityEvent) -> String) {
    val label = when (val relative = DayLabel.of(day.date, today)) {
        DayLabel.Today -> stringResource(R.string.activity_today)
        DayLabel.Yesterday -> stringResource(R.string.activity_yesterday)
        is DayLabel.Date -> formatDay(relative.date, today)
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { heading() },
        )
        ListCard {
            day.events.forEachIndexed { index, event ->
                if (index > 0) ListRowDivider()
                ActivityRow(event = event, time = timeOf(event))
            }
        }
    }
}

/**
 * «Fila de ajuste» de Figma 47:1975 **sin flecha ni acción** (no hay pantalla de detalle). Los fallidos llevan la alerta y el
 * título en `danger`. El título largo se corta en una línea con «…» y la hora no se mueve.
 */
@Composable
private fun ActivityRow(event: ActivityEvent, time: String) {
    val title = stringResource(event.rowTitleRes())
    val device = event.deviceName ?: stringResource(R.string.activity_unknown_device)
    val timeDescription = stringResource(R.string.activity_time_description, time)
    val failedSuffix = if (event.success) "" else stringResource(R.string.activity_failed_suffix)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(vertical = 8.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$title$failedSuffix, $device, $timeDescription" },
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ListRowIcon(event.rowIconRes(), danger = !event.success)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = if (event.success) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = device,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = time,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(end = 2.dp),
        )
    }
}
