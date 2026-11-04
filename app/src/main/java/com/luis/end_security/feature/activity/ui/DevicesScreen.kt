package com.luis.end_security.feature.activity.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luis.end_security.R
import com.luis.end_security.core.designsystem.component.BottomNavTab
import com.luis.end_security.core.designsystem.component.DangerButton
import com.luis.end_security.core.designsystem.component.ListCard
import com.luis.end_security.core.designsystem.component.ListRowDivider
import com.luis.end_security.core.designsystem.component.ListRowIcon
import com.luis.end_security.core.designsystem.component.PrimaryButton
import com.luis.end_security.core.designsystem.component.SecondaryButton
import com.luis.end_security.core.designsystem.component.SheetIconDisc
import com.luis.end_security.core.designsystem.component.TwoWeightTitle
import com.luis.end_security.feature.account.ui.TitleStyle
import com.luis.end_security.feature.activity.domain.LastUsed
import kotlinx.coroutines.delay
import androidx.compose.material3.Icon

/**
 * SCR-33 · Dispositivos (Figma 47:1817, cargando 69:3411, sin conexión 69:3850). Todo viene del servidor (EP-23, EP-24).
 * No muestra secretos: no lleva `SecureScreen`.
 */
@Composable
fun DevicesRoute(
    onBack: () -> Unit,
    onChangePassword: () -> Unit,
    onTabSelected: (BottomNavTab) -> Unit,
    viewModel: DevicesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DevicesScreen(
        state = state,
        onAction = viewModel::onAction,
        onBack = onBack,
        onChangePassword = onChangePassword,
        onTabSelected = onTabSelected,
    )
}

@Composable
fun DevicesScreen(
    state: DevicesUiState,
    onAction: (DevicesAction) -> Unit,
    onBack: () -> Unit,
    onChangePassword: () -> Unit,
    onTabSelected: (BottomNavTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    // El aviso se ve unos segundos; uno nuevo (otro `id`) reemplaza al anterior.
    var shown by remember { mutableStateOf<DevicesNotice?>(null) }
    LaunchedEffect(state.notice) {
        val notice = state.notice ?: return@LaunchedEffect
        shown = notice
        delay(NOTICE_MILLIS)
        if (shown == notice) shown = null
    }
    AccountListPage(
        title = stringResource(R.string.devices_title),
        onBack = onBack,
        onTabSelected = onTabSelected,
        modifier = modifier,
        notice = shown?.let { noticeText(it) },
        bottom = when {
            state.phase == DevicesPhase.Offline -> {
                {
                    PrimaryButton(
                        text = stringResource(R.string.devices_retry),
                        onClick = { onAction(DevicesAction.Retry) },
                        showArrow = false,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            state.phase == DevicesPhase.Loaded && state.canCloseOthers -> {
                {
                    DangerButton(
                        text = stringResource(R.string.devices_close_others),
                        onClick = { onAction(DevicesAction.CloseOthersClicked) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            else -> null
        },
    ) {
        when (state.phase) {
            DevicesPhase.Loading -> DevicesSkeleton(loadingText = stringResource(R.string.devices_loading_description))
            DevicesPhase.Offline -> OfflinePanel(
                title = stringResource(R.string.devices_offline_title),
                body = stringResource(R.string.devices_offline_body),
            )
            DevicesPhase.Loaded -> DevicesContent(state, onAction, onChangePassword)
        }
    }
    state.sheet?.let { sheet ->
        val title: String
        val body: String
        when (sheet) {
            is DevicesSheet.CloseOne -> {
                title = stringResource(R.string.devices_close_one_title, sheet.name)
                body = stringResource(R.string.devices_close_one_body)
            }
            DevicesSheet.CloseOthers -> {
                title = stringResource(R.string.devices_close_others_title)
                body = stringResource(R.string.devices_close_others_body)
            }
        }
        CloseSessionSheet(
            title = title,
            body = body,
            loading = state.isClosing,
            onConfirm = { onAction(DevicesAction.SheetConfirmed) },
            onDismiss = { onAction(DevicesAction.SheetDismissed) },
        )
    }
}

@Composable
private fun DevicesContent(state: DevicesUiState, onAction: (DevicesAction) -> Unit, onChangePassword: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ListPagePadding)
            .padding(top = 8.dp, bottom = 16.dp),
    ) {
        val count = state.devices.size
        TwoWeightTitle(
            boldLine = pluralStringResource(R.plurals.devices_heading_bold, count, count),
            regularLine = stringResource(R.string.devices_heading_regular),
            style = TitleStyle(),
        )
        Spacer(Modifier.height(16.dp))
        ListCard {
            state.devices.forEachIndexed { index, row ->
                if (index > 0) ListRowDivider()
                DeviceRowItem(row = row, onClick = { onAction(DevicesAction.RowClicked(row.id)) })
            }
        }
        Spacer(Modifier.height(16.dp))
        val before = stringResource(R.string.devices_hint_before)
        val link = stringResource(R.string.devices_hint_link)
        val after = stringResource(R.string.devices_hint_after)
        val linkStyle = TextLinkStyles(
            SpanStyle(fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline, color = MaterialTheme.colorScheme.onSurface),
        )
        val currentOnChangePassword by rememberUpdatedState(onChangePassword)
        Text(
            text = buildAnnotatedString {
                append(before)
                withLink(LinkAnnotation.Clickable("change-password", linkStyle) { currentOnChangePassword() }) { append(link) }
                append(after)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** «Fila de ajuste» de Figma 47:1854: ícono, nombre, cuándo se usó por última vez y «Este» (sin acción) o la flecha. */
@Composable
private fun DeviceRowItem(row: DeviceRow, onClick: () -> Unit) {
    val lastUsed = lastUsedText(row.lastUsed)
    val closeLabel = stringResource(R.string.devices_row_close_label)
    val thisDevice = stringResource(R.string.devices_this_description)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            // La actual no tiene acción ni flecha: la sesión de este teléfono se cierra desde Ajustes → Cerrar sesión.
            .then(if (row.current) Modifier else Modifier.clickable(onClickLabel = closeLabel, role = Role.Button, onClick = onClick))
            .padding(vertical = 8.dp)
            .semantics(mergeDescendants = true) {
                if (row.current) contentDescription = "${row.name}, $thisDevice, $lastUsed"
            },
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ListRowIcon(R.drawable.ic_smartphone_20, danger = false)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = row.name,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Text(
                text = lastUsed,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
        if (row.current) {
            Text(
                text = stringResource(R.string.devices_this),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 6.dp),
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right_20),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(end = 4.dp),
            )
        }
    }
}

@Composable
internal fun lastUsedText(lastUsed: LastUsed): String = when (lastUsed) {
    LastUsed.Now -> stringResource(R.string.devices_active_now)
    is LastUsed.Minutes -> pluralStringResource(R.plurals.devices_ago_minutes, lastUsed.count, lastUsed.count)
    is LastUsed.Hours -> pluralStringResource(R.plurals.devices_ago_hours, lastUsed.count, lastUsed.count)
    is LastUsed.Days -> pluralStringResource(R.plurals.devices_ago_days, lastUsed.count, lastUsed.count)
    is LastUsed.Weeks -> pluralStringResource(R.plurals.devices_ago_weeks, lastUsed.count, lastUsed.count)
}

@Composable
private fun noticeText(notice: DevicesNotice): String = when (notice) {
    is DevicesNotice.Closed -> stringResource(R.string.devices_closed, notice.name)
    is DevicesNotice.OthersClosed -> stringResource(R.string.devices_others_closed)
    is DevicesNotice.OneFailed -> stringResource(R.string.devices_close_one_failed, notice.name)
    is DevicesNotice.SomeFailed -> pluralStringResource(R.plurals.devices_close_failed, notice.count, notice.count)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CloseSessionSheet(title: String, body: String, loading: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetGesturesEnabled = !loading,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = !loading),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        CloseSessionSheetContent(
            title = title,
            body = body,
            loading = loading,
            onConfirm = onConfirm,
            onCancel = onDismiss,
            modifier = Modifier.navigationBarsPadding(),
        )
    }
}

/** Hoja de confirmación con el diseño de «Cerrar sesión» (Figma 215:3811) y los textos de Dispositivos. */
@Composable
fun CloseSessionSheetContent(
    title: String,
    body: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = 8.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SheetIconDisc(R.drawable.ic_log_out_20, danger = true)
        Spacer(Modifier.height(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        DangerButton(
            text = stringResource(R.string.devices_close_confirm),
            onClick = onConfirm,
            loading = loading,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        SecondaryButton(
            text = stringResource(R.string.settings_sign_out_cancel),
            onClick = { if (!loading) onCancel() },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private const val NOTICE_MILLIS = 4_000L
