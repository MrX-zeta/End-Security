package com.luis.end_security.feature.vault.ui.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luis.end_security.R
import com.luis.end_security.core.designsystem.component.BlobShape
import com.luis.end_security.core.designsystem.component.BottomNavBar
import com.luis.end_security.core.designsystem.component.BottomNavTab
import com.luis.end_security.core.designsystem.component.FloatingAddButton
import com.luis.end_security.core.designsystem.component.LetterTile
import com.luis.end_security.core.designsystem.component.MessageBar
import com.luis.end_security.core.designsystem.component.NeumorphicDisc
import com.luis.end_security.core.designsystem.component.NoticeCard
import com.luis.end_security.core.designsystem.component.OfflineNotice
import com.luis.end_security.core.designsystem.component.OrganicBlob
import com.luis.end_security.core.designsystem.component.OutlinedIconButton
import com.luis.end_security.core.designsystem.component.PrimaryButton
import com.luis.end_security.core.designsystem.component.SearchField
import com.luis.end_security.core.designsystem.component.TwoWeightTitle
import com.luis.end_security.core.designsystem.theme.EndSecurityTheme
import com.luis.end_security.core.designsystem.theme.PreviewSurface
import com.luis.end_security.core.security.SecureScreen
import java.util.UUID

// Medidas de Figma (Bóveda · Lista, nodo 46:356).
private val ContentHorizontalPadding = 32.dp
private val RowShape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)

@Composable
fun VaultListRoute(
    onTabSelected: (BottomNavTab) -> Unit,
    onOpenItem: (UUID) -> Unit,
    onOpenNew: () -> Unit,
    onOpenConflict: (UUID) -> Unit,
    onOpenConflicts: () -> Unit,
    viewModel: VaultListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnOpenItem by rememberUpdatedState(onOpenItem)
    val currentOnOpenNew by rememberUpdatedState(onOpenNew)
    val currentOnOpenConflict by rememberUpdatedState(onOpenConflict)
    val currentOnOpenConflicts by rememberUpdatedState(onOpenConflicts)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is VaultListEffect.OpenItem -> currentOnOpenItem(effect.id)
                VaultListEffect.OpenNew -> currentOnOpenNew()
                is VaultListEffect.OpenConflict -> currentOnOpenConflict(effect.id)
                VaultListEffect.OpenConflicts -> currentOnOpenConflicts()
            }
        }
    }
    // Nombres, usuarios y sitios de las contraseñas: no deben verse en Recientes ni en capturas.
    SecureScreen()
    VaultListScreen(
        state = state,
        onTabSelected = onTabSelected,
        onQueryChange = viewModel::onQueryChange,
        onLockNow = viewModel::onLockNow,
        onItemClick = viewModel::onItemClick,
        onAddClick = viewModel::onAddClick,
        onUndoDelete = viewModel::onUndoDelete,
        onResolve = viewModel::onResolveClick,
    )
}

/** SCR-10 (lista) y SCR-11 (vacía). */
@Composable
fun VaultListScreen(
    state: VaultListUiState,
    onTabSelected: (BottomNavTab) -> Unit,
    onQueryChange: (String) -> Unit,
    onLockNow: () -> Unit,
    onItemClick: (VaultRow) -> Unit,
    onAddClick: () -> Unit,
    onUndoDelete: () -> Unit,
    modifier: Modifier = Modifier,
    onResolve: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            if (state.isEmpty) {
                EmptyContent(state = state, onLockNow = onLockNow, onAddClick = onAddClick)
            } else {
                ListContent(
                    state = state,
                    onQueryChange = onQueryChange,
                    onLockNow = onLockNow,
                    onItemClick = onItemClick,
                    onResolve = onResolve,
                )
            }
            // Abajo: el botón «+» y, si se acaba de eliminar algo, el aviso con «Deshacer».
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = ContentHorizontalPadding)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!state.isEmpty && !state.isLoading) {
                    FloatingAddButton(
                        contentDescription = stringResource(R.string.vault_new_password),
                        onClick = onAddClick,
                        // 24 dp del borde, como en Figma (la columna ya deja 32).
                        modifier = Modifier
                            .align(Alignment.End)
                            .offset(x = 8.dp),
                    )
                }
                state.deleted?.let { notice ->
                    MessageBar(
                        text = notice.name?.let { stringResource(R.string.vault_deleted, it) }
                            ?: stringResource(R.string.vault_deleted_generic),
                        icon = R.drawable.ic_trash_20,
                        actionLabel = stringResource(R.string.vault_undo),
                        onAction = onUndoDelete,
                    )
                }
            }
        }
        // TODO(DEC-56): Salud, Generador y Ajustes llevan a su raíz cuando existan.
        BottomNavBar(selected = BottomNavTab.Vault, onTabSelected = onTabSelected)
    }
}

@Composable
private fun ListContent(
    state: VaultListUiState,
    onQueryChange: (String) -> Unit,
    onLockNow: () -> Unit,
    onItemClick: (VaultRow) -> Unit,
    onResolve: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        // Deja libres el botón «+» (64) y el aviso (≈ 56) al llegar al final.
        contentPadding = PaddingValues(start = ContentHorizontalPadding, end = ContentHorizontalPadding, bottom = 160.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Header(initial = state.accountInitial, onLockNow = onLockNow) }
        item {
            Spacer(Modifier.height(10.dp))
            TwoWeightTitle(
                boldLine = stringResource(R.string.vault_title),
                regularLine = pluralStringResource(R.plurals.vault_count, state.totalCount, state.totalCount),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Normal, lineHeight = 35.sp),
            )
        }
        state.recoveryCodesLeft?.let { left ->
            item {
                NoticeCard(text = pluralStringResource(R.plurals.vault_recovery_codes_left, left, left))
            }
        }
        item {
            Spacer(Modifier.height(8.dp))
            SearchField(
                value = state.query,
                onValueChange = onQueryChange,
                placeholder = stringResource(R.string.vault_search_hint),
            )
            // SCR-44: sin conexión, y SCR-18: contraseñas que cambiaron en dos dispositivos.
            if (state.offline) {
                Spacer(Modifier.height(16.dp))
                OfflineNotice()
            }
            if (state.conflictCount > 0) {
                Spacer(Modifier.height(16.dp))
                MessageBar(
                    text = pluralStringResource(R.plurals.vault_conflicts_notice, state.conflictCount, state.conflictCount),
                    icon = R.drawable.ic_repeat_20,
                    actionLabel = stringResource(R.string.vault_resolve),
                    onAction = onResolve,
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.vault_all),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(R.string.vault_sort_az),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
        if (state.noResults) {
            item {
                Text(
                    text = stringResource(R.string.vault_no_results),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        }
        items(state.rows, key = { it.id }) { row ->
            VaultItemRow(row = row, onClick = { onItemClick(row) })
        }
    }
}

/** El avatar de la cuenta y «Bloquear ahora» (DEC-52). */
@Composable
private fun Header(initial: String, onLockNow: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LetterTile(
            letter = initial,
            container = MaterialTheme.colorScheme.onSurface,
            textStyle = MaterialTheme.typography.titleMedium,
        )
        OutlinedIconButton(
            icon = R.drawable.ic_lock_20,
            contentDescription = stringResource(R.string.vault_lock_now),
            onClick = onLockNow,
        )
    }
}

/** «Elemento de bóveda» de Figma (nodo 46:410): 76 de alto, inicial, nombre, usuario y chevrón. */
@Composable
private fun VaultItemRow(row: VaultRow, onClick: () -> Unit) {
    val title: String
    val subtitle: String
    val initial: String
    when (row) {
        is VaultRow.Item -> {
            title = row.name
            subtitle = row.username
            initial = row.initial
        }
        is VaultRow.Damaged -> {
            title = stringResource(R.string.vault_item_damaged)
            subtitle = stringResource(R.string.vault_item_damaged_hint)
            initial = "!"
        }
        is VaultRow.Newer -> {
            title = stringResource(R.string.vault_item_newer)
            subtitle = stringResource(R.string.vault_item_newer_hint)
            initial = "↑"
        }
    }
    // Todas abren un detalle: el normal, o uno mínimo con «Eliminar» si está dañada o es de otra versión.
    val clickable = true
    val openLabel = stringResource(R.string.vault_item_open, title)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .dropShadow(
                shape = RowShape,
                shadow = Shadow(
                    radius = 20.dp,
                    color = EndSecurityTheme.extendedColors.shadowDark,
                    offset = DpOffset(0.dp, 8.dp),
                ),
            )
            .background(MaterialTheme.colorScheme.surface, RowShape)
            .then(if (clickable) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(16.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = if (clickable) "$openLabel, $subtitle" else "$title, $subtitle"
                if (!clickable) role = Role.Image
            },
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LetterTile(letter = initial)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right_20),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** SCR-11: la bóveda todavía no tiene elementos. */
@Composable
private fun EmptyContent(
    state: VaultListUiState,
    onLockNow: () -> Unit,
    onAddClick: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Manchas de fondo, como en las pantallas de acceso (decorativas).
        OrganicBlob(
            shape = BlobShape.B,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 12.dp)
                .height(200.dp)
                .fillMaxWidth(0.55f),
        )
        OrganicBlob(
            shape = BlobShape.C,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = 80.dp)
                .height(180.dp)
                .fillMaxWidth(0.35f),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = ContentHorizontalPadding),
        ) {
            Header(initial = state.accountInitial, onLockNow = onLockNow)
            Spacer(Modifier.height(18.dp))
            TwoWeightTitle(
                boldLine = stringResource(R.string.vault_title),
                regularLine = stringResource(R.string.vault_empty_title_regular),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Normal, lineHeight = 35.sp),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                NeumorphicDisc(
                    icon = R.drawable.ic_lock_40,
                    contentDescription = null,
                    iconTint = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(28.dp))
                Text(
                    text = stringResource(R.string.vault_empty_heading),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.vault_empty_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(32.dp))
                PrimaryButton(
                    text = stringResource(R.string.vault_add_password),
                    onClick = onAddClick,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(96.dp))
        }
    }
}

private val PreviewRows = listOf(
    VaultRow.Item(UUID(0, 1), "Amazon", "luis.ruiz@gmail.com", "A"),
    VaultRow.Item(UUID(0, 2), "BBVA", "Luis Ruiz", "B"),
    VaultRow.Item(UUID(0, 3), "Gmail", "luis.ruiz@gmail.com", "G"),
    VaultRow.Damaged(UUID(0, 4)),
)

@Preview(name = "Bóveda · lista", device = "spec:width=412dp,height=917dp")
@Composable
private fun VaultListPreview() {
    PreviewSurface {
        VaultListScreen(
            state = VaultListUiState(accountInitial = "L", isLoading = false, rows = PreviewRows, totalCount = 24),
            onTabSelected = {}, onQueryChange = {}, onLockNow = {}, onItemClick = {}, onAddClick = {}, onUndoDelete = {},
        )
    }
}

@Preview(name = "Bóveda · lista con aviso de eliminado", device = "spec:width=412dp,height=917dp")
@Composable
private fun VaultListUndoPreview() {
    PreviewSurface {
        VaultListScreen(
            state = VaultListUiState(
                accountInitial = "L", isLoading = false, rows = PreviewRows, totalCount = 23, deleted = DeletedNotice("Gmail"),
            ),
            onTabSelected = {}, onQueryChange = {}, onLockNow = {}, onItemClick = {}, onAddClick = {}, onUndoDelete = {},
        )
    }
}

@Preview(name = "Bóveda · vacía", device = "spec:width=412dp,height=917dp")
@Composable
private fun VaultEmptyPreview() {
    PreviewSurface {
        VaultListScreen(
            state = VaultListUiState(accountInitial = "L", isLoading = false),
            onTabSelected = {}, onQueryChange = {}, onLockNow = {}, onItemClick = {}, onAddClick = {}, onUndoDelete = {},
        )
    }
}
