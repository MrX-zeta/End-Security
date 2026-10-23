package com.luis.end_security.feature.vault.ui.conflict

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luis.end_security.R
import com.luis.end_security.core.designsystem.component.BackButton
import com.luis.end_security.core.designsystem.component.BottomNavBar
import com.luis.end_security.core.designsystem.component.BottomNavTab
import com.luis.end_security.core.designsystem.component.InlineErrorMessage
import com.luis.end_security.core.designsystem.component.PrimaryButton
import com.luis.end_security.core.designsystem.component.SecondaryButton
import com.luis.end_security.core.designsystem.component.TwoWeightTitle
import com.luis.end_security.core.designsystem.theme.PreviewSurface
import com.luis.end_security.core.security.SecureScreen
import com.luis.end_security.feature.vault.domain.KeepVersion
import java.time.Instant

private val CardShape = RoundedCornerShape(20.dp)

@Composable
fun ConflictDetailRoute(
    onClose: () -> Unit,
    onBack: () -> Unit,
    onTabSelected: (BottomNavTab) -> Unit,
    viewModel: ConflictDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnClose by rememberUpdatedState(onClose)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                ConflictDetailEffect.Close -> currentOnClose()
            }
        }
    }
    // Muestra las dos versiones de una contraseña: sin capturas ni vista en Recientes (regla no negociable 7).
    SecureScreen()
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onHidePasswords() }

    ConflictDetailScreen(
        state = state,
        onBack = onBack,
        onTabSelected = onTabSelected,
        onSelect = viewModel::onSelect,
        onToggleLocalPassword = viewModel::onToggleLocalPassword,
        onToggleRemotePassword = viewModel::onToggleRemotePassword,
        onKeep = { viewModel.onKeep() },
        onKeepSide = viewModel::onKeep,
        onRetry = viewModel::load,
    )
}

/** SCR-21 · Elegir versión. */
@Composable
fun ConflictDetailScreen(
    state: ConflictDetailUiState,
    onBack: () -> Unit,
    onTabSelected: (BottomNavTab) -> Unit,
    onSelect: (KeepVersion) -> Unit,
    onToggleLocalPassword: () -> Unit,
    onToggleRemotePassword: () -> Unit,
    onKeep: () -> Unit,
    onKeepSide: (KeepVersion) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .statusBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .heightIn(min = 64.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BackButton(onClick = onBack)
                Text(
                    text = stringResource(R.string.conflict_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .padding(start = 14.dp)
                        .semantics { heading() },
                )
            }
            when (state.status) {
                ConflictStatus.Ready -> ReadyContent(
                    state, onSelect, onToggleLocalPassword, onToggleRemotePassword, onKeep, onKeepSide,
                )
                ConflictStatus.Offline, ConflictStatus.Failed -> Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    InlineErrorMessage(
                        text = stringResource(
                            if (state.status == ConflictStatus.Offline) R.string.conflict_error_offline else R.string.conflict_error_generic,
                        ),
                    )
                    SecondaryButton(text = stringResource(R.string.conflict_retry), onClick = onRetry, modifier = Modifier.fillMaxWidth())
                }
                // Cargando, resuelto o bloqueado: nada que mostrar.
                ConflictStatus.Loading, ConflictStatus.Gone -> Box(modifier = Modifier.weight(1f))
            }
        }
        BottomNavBar(selected = BottomNavTab.Vault, onTabSelected = onTabSelected)
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.ReadyContent(
    state: ConflictDetailUiState,
    onSelect: (KeepVersion) -> Unit,
    onToggleLocalPassword: () -> Unit,
    onToggleRemotePassword: () -> Unit,
    onKeep: () -> Unit,
    onKeepSide: (KeepVersion) -> Unit,
) {
    val local = state.local ?: return
    val remote = state.remote ?: return
    Column(
        modifier = Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TwoWeightTitle(
            boldLine = state.name?.let { stringResource(R.string.conflict_heading_bold, it) } ?: stringResource(R.string.conflict_heading_bold_generic),
            regularLine = stringResource(R.string.conflict_heading_regular),
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Normal, lineHeight = 35.sp),
        )
        Text(
            text = stringResource(R.string.conflict_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        VersionCardView(
            card = local,
            title = stringResource(R.string.conflict_here),
            deletedText = stringResource(R.string.conflict_deleted_here),
            selected = state.selected == KeepVersion.Local,
            selectable = !state.hasDeletion && local.kind != SideKind.Unreadable,
            onSelect = { onSelect(KeepVersion.Local) },
            onTogglePassword = onToggleLocalPassword,
        )
        VersionCardView(
            card = remote,
            title = stringResource(R.string.conflict_there),
            deletedText = stringResource(R.string.conflict_deleted_there),
            selected = state.selected == KeepVersion.Remote,
            selectable = !state.hasDeletion && remote.kind != SideKind.Unreadable,
            onSelect = { onSelect(KeepVersion.Remote) },
            onTogglePassword = onToggleRemotePassword,
        )
        state.error?.let { error ->
            if (error == ResolveError.Unavailable) InlineErrorMessage(text = stringResource(R.string.conflict_error_unavailable))
        }
    }
    Column(
        modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.hasDeletion) {
            // Una eliminación se resuelve con dos botones, no con el selector (grafo, DEC-57).
            val remoteDeleted = remote.kind == SideKind.Deleted
            PrimaryButton(
                text = stringResource(if (remoteDeleted) R.string.conflict_keep_mine else R.string.conflict_keep_other),
                onClick = { onKeepSide(if (remoteDeleted) KeepVersion.Local else KeepVersion.Remote) },
                showArrow = false,
                loading = state.isResolving,
                modifier = Modifier.fillMaxWidth(),
            )
            SecondaryButton(
                text = stringResource(R.string.conflict_delete),
                // Eliminarla = que la eliminación gane: la del servidor si allá se eliminó, la de aquí si fue aquí.
                onClick = { onKeepSide(if (remoteDeleted) KeepVersion.Remote else KeepVersion.Local) },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            PrimaryButton(
                text = stringResource(R.string.conflict_keep),
                onClick = onKeep,
                showArrow = false,
                loading = state.isResolving,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Una de las dos versiones como tarjeta seleccionable (Figma 46:1196): radio, título, hora y renglones. */
@Composable
private fun VersionCardView(
    card: VersionCard,
    title: String,
    deletedText: String,
    selected: Boolean,
    selectable: Boolean,
    onSelect: () -> Unit,
    onTogglePassword: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val border = if (selected && selectable) colors.onSurface else colors.outline
    val selectedText = stringResource(R.string.conflict_selected)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, CardShape)
            .border(if (selected && selectable) 2.dp else 1.dp, border, CardShape)
            .then(
                if (selectable) {
                    Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
                } else {
                    Modifier
                },
            )
            .semantics { if (selectable && selected) stateDescription = selectedText }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selectable) RadioMark(selected = selected)
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = colors.onSurface,
                )
                Text(
                    text = whenText(card.at, capitalized = true),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        when (card.kind) {
            SideKind.Deleted -> Text(text = deletedText, style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
            SideKind.Unreadable -> Text(
                text = stringResource(R.string.conflict_unreadable),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            SideKind.Live -> {
                card.fields.forEach { field ->
                    FieldLine(label = stringResource(field.kind.labelRes()), value = field.value.ifEmpty { stringResource(R.string.conflict_empty_value) }, bold = field.differs)
                    if (field.kind == FieldKind.Username) PasswordLine(card, onTogglePassword)
                }
            }
        }
    }
}

@Composable
private fun RadioMark(selected: Boolean) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .border(2.dp, if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(Modifier.size(12.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
        }
    }
}

@Composable
private fun FieldLine(label: String, value: String, bold: Boolean) {
    Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            maxLines = 2,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

/** La contraseña de esta versión: puntos y el ojo (48 dp al tacto); a la vista, el texto. */
@Composable
private fun PasswordLine(card: VersionCard, onToggle: () -> Unit) {
    val shown = card.shownPassword
    Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.conflict_field_password),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(12.dp))
        Text(
            text = shown ?: "•".repeat(card.passwordLength.coerceAtMost(MAX_DOTS)),
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = if (card.passwordDiffers) FontWeight.Bold else FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            modifier = Modifier.weight(1f, fill = false),
        )
        Box(
            modifier = Modifier
                .size(48.dp)
                .clickable(role = Role.Button, onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(if (shown != null) R.drawable.ic_eye_off_20 else R.drawable.ic_eye_20),
                contentDescription = stringResource(if (shown != null) R.string.password_hide else R.string.password_show),
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private const val MAX_DOTS = 16

private fun FieldKind.labelRes(): Int = when (this) {
    FieldKind.Name -> R.string.conflict_field_name
    FieldKind.Username -> R.string.conflict_field_username
    FieldKind.Site -> R.string.conflict_field_site
    FieldKind.Notes -> R.string.conflict_field_notes
}

private fun previewState(remoteDeleted: Boolean = false) = ConflictDetailUiState(
    status = ConflictStatus.Ready,
    name = "Netflix",
    local = VersionCard(
        SideKind.Live, Instant.now().minusSeconds(3_000),
        listOf(VersionField(FieldKind.Name, "Netflix", false), VersionField(FieldKind.Username, "luis.ruiz@gmail.com", false), VersionField(FieldKind.Site, "netflix.com", true)),
        passwordLength = 14, passwordDiffers = true,
    ),
    remote = if (remoteDeleted) {
        VersionCard(SideKind.Deleted, Instant.now().minusSeconds(600))
    } else {
        VersionCard(
            SideKind.Live, Instant.now().minusSeconds(600),
            listOf(VersionField(FieldKind.Name, "Netflix", false), VersionField(FieldKind.Username, "luis.ruiz@gmail.com", false), VersionField(FieldKind.Site, "netflix.com/mx", true)),
            passwordLength = 12, passwordDiffers = true,
        )
    },
)

@Preview(name = "Elegir versión", device = "spec:width=412dp,height=917dp")
@Composable
private fun ConflictDetailPreview() {
    PreviewSurface {
        ConflictDetailScreen(previewState(), {}, {}, {}, {}, {}, {}, {}, {})
    }
}

@Preview(name = "Elegir versión · eliminada en otro dispositivo", device = "spec:width=412dp,height=917dp")
@Composable
private fun ConflictDetailDeletedPreview() {
    PreviewSurface {
        ConflictDetailScreen(previewState(remoteDeleted = true), {}, {}, {}, {}, {}, {}, {}, {})
    }
}
