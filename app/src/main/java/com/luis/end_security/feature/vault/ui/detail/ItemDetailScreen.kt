package com.luis.end_security.feature.vault.ui.detail

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luis.end_security.R
import com.luis.end_security.core.designsystem.component.BackButton
import com.luis.end_security.core.designsystem.component.BottomNavBar
import com.luis.end_security.core.designsystem.component.BottomNavTab
import com.luis.end_security.core.designsystem.component.DangerButton
import com.luis.end_security.core.designsystem.component.FieldActionButton
import com.luis.end_security.core.designsystem.component.LetterTile
import com.luis.end_security.core.designsystem.component.MessageBar
import com.luis.end_security.core.designsystem.component.OutlinedIconButton
import com.luis.end_security.core.designsystem.component.PrimaryButton
import com.luis.end_security.core.designsystem.component.ReadOnlyField
import com.luis.end_security.core.designsystem.component.SecondaryButton
import com.luis.end_security.core.designsystem.theme.PreviewSurface
import com.luis.end_security.core.security.SecureScreen
import com.luis.end_security.feature.vault.domain.EditedAge
import java.util.UUID

@Composable
fun ItemDetailRoute(
    onBack: () -> Unit,
    onEdit: (UUID) -> Unit,
    onTabSelected: (BottomNavTab) -> Unit,
    itemId: UUID,
    viewModel: ItemDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val currentOnBack by rememberUpdatedState(onBack)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                ItemDetailEffect.Close -> currentOnBack()
                is ItemDetailEffect.OpenSite -> try {
                    // Solo http/https (SiteUrl); el navegador, nunca la propia app.
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, effect.url.toUri()).addCategory(Intent.CATEGORY_BROWSABLE),
                    )
                } catch (_: ActivityNotFoundException) {
                    // Sin navegador: no hay nada que hacer.
                }
            }
        }
    }
    // Muestra la contraseña: sin capturas ni vista en Recientes (regla no negociable 7).
    SecureScreen()
    // Al salir de primer plano la contraseña vuelve a ocultarse.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onHidePassword() }

    ItemDetailScreen(
        state = state,
        onBack = onBack,
        onEdit = { onEdit(itemId) },
        onTabSelected = onTabSelected,
        onCopyUsername = viewModel::onCopyUsername,
        onCopyPassword = viewModel::onCopyPassword,
        onTogglePassword = viewModel::onTogglePassword,
        onOpenSite = viewModel::onOpenSite,
        onDelete = viewModel::onDeleteClick,
        onDeleteConfirm = viewModel::onDeleteConfirm,
        onDeleteDismiss = viewModel::onDeleteDismiss,
    )
}

/** SCR-12 · Detalle (y SCR-13 con el aviso «Copiada»). */
@Composable
fun ItemDetailScreen(
    state: ItemDetailUiState,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onTabSelected: (BottomNavTab) -> Unit,
    onCopyUsername: () -> Unit,
    onCopyPassword: () -> Unit,
    onTogglePassword: () -> Unit,
    onOpenSite: () -> Unit,
    onDelete: () -> Unit,
    onDeleteConfirm: () -> Unit,
    onDeleteDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        if (state.status != DetailStatus.Found) {
            // Cargando, eliminado o bloqueado: nada que mostrar.
            Box(modifier = Modifier.weight(1f))
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .statusBarsPadding(),
            ) {
                TopBar(onBack = onBack, onEdit = onEdit)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(8.dp))
                    LetterTile(
                        letter = state.initial,
                        size = 85.dp,
                        cornerRadius = 24.dp,
                        container = MaterialTheme.colorScheme.onSurface,
                        textStyle = MaterialTheme.typography.headlineMedium.copy(fontSize = 40.sp),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = state.name,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { heading() },
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = editedText(state.edited),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(20.dp))
                    Fields(
                        state = state,
                        onCopyUsername = onCopyUsername,
                        onTogglePassword = onTogglePassword,
                        onOpenSite = onOpenSite,
                    )
                    Spacer(Modifier.height(20.dp))
                    DeleteLink(onClick = onDelete)
                    Spacer(Modifier.height(16.dp))
                }
                Column(
                    modifier = Modifier.padding(start = 32.dp, end = 32.dp, bottom = 16.dp, top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.copied?.let { copied ->
                        val seconds = copied.seconds
                        MessageBar(
                            text = stringResource(
                                if (copied.kind == CopiedKind.Password) R.string.item_copied_password else R.string.item_copied_username,
                                seconds,
                            ),
                            icon = R.drawable.ic_check_16,
                        )
                    }
                    PrimaryButton(
                        text = stringResource(R.string.item_copy_password),
                        onClick = onCopyPassword,
                        showArrow = false,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        BottomNavBar(selected = BottomNavTab.Vault, onTabSelected = onTabSelected)
    }
    if (state.showDeleteSheet) {
        DeleteConfirmationSheet(name = state.name, onConfirm = onDeleteConfirm, onDismiss = onDeleteDismiss)
    }
}

@Composable
private fun TopBar(onBack: () -> Unit, onEdit: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BackButton(onClick = onBack)
        Text(
            text = stringResource(R.string.item_detail_title),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp)
                .semantics { heading() },
        )
        OutlinedIconButton(
            icon = R.drawable.ic_pencil_20,
            contentDescription = stringResource(R.string.item_edit),
            onClick = onEdit,
            size = 44.dp,
        )
    }
}

@Composable
private fun Fields(
    state: ItemDetailUiState,
    onCopyUsername: () -> Unit,
    onTogglePassword: () -> Unit,
    onOpenSite: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ReadOnlyField(
            label = stringResource(R.string.item_field_username),
            displayText = state.username,
            leadingIcon = R.drawable.ic_user_20,
            action = {
                FieldActionButton(
                    icon = R.drawable.ic_copy_20,
                    contentDescription = stringResource(R.string.item_copy_username),
                    onClick = onCopyUsername,
                )
            },
        )
        val shown = state.shownPassword
        ReadOnlyField(
            label = stringResource(R.string.item_field_password),
            // Oculta: puntos de la misma longitud (hasta 24); a la vista, el texto real.
            displayText = shown ?: "•".repeat(state.passwordLength.coerceAtMost(MAX_DOTS)),
            spokenText = shown ?: pluralStringResource(R.plurals.item_password_dots, state.passwordLength, state.passwordLength),
            leadingIcon = R.drawable.ic_lock_20,
            action = {
                FieldActionButton(
                    icon = if (shown != null) R.drawable.ic_eye_off_20 else R.drawable.ic_eye_20,
                    contentDescription = stringResource(if (shown != null) R.string.password_hide else R.string.password_show),
                    onClick = onTogglePassword,
                )
            },
        )
        state.site?.let { site ->
            ReadOnlyField(
                label = stringResource(R.string.item_field_site),
                displayText = site,
                leadingIcon = R.drawable.ic_globe_20,
                action = if (state.siteOpenable) {
                    {
                        FieldActionButton(
                            icon = R.drawable.ic_external_link_20,
                            contentDescription = stringResource(R.string.item_open_site),
                            onClick = onOpenSite,
                        )
                    }
                } else {
                    null
                },
            )
        }
        state.notes?.let { notes ->
            ReadOnlyField(
                label = stringResource(R.string.item_field_notes),
                displayText = notes,
                leadingIcon = R.drawable.ic_file_text_20,
            )
        }
    }
}

/** «Eliminar contraseña» en `danger`, centrado (Figma 46:608). 48 dp al tacto. */
@Composable
private fun DeleteLink(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_trash_20),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = stringResource(R.string.item_delete),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun editedText(age: EditedAge): String = when (age) {
    EditedAge.Now -> stringResource(R.string.item_edited_now)
    is EditedAge.Minutes -> pluralStringResource(R.plurals.item_edited_minutes, age.count, age.count)
    is EditedAge.Hours -> pluralStringResource(R.plurals.item_edited_hours, age.count, age.count)
    is EditedAge.Days -> pluralStringResource(R.plurals.item_edited_days, age.count, age.count)
    is EditedAge.Months -> pluralStringResource(R.plurals.item_edited_months, age.count, age.count)
    is EditedAge.Years -> pluralStringResource(R.plurals.item_edited_years, age.count, age.count)
}

private const val MAX_DOTS = 24

/** SCR-16 · «¿Eliminar {nombre}?» en hoja inferior (Figma 62:2972). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeleteConfirmationSheet(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        // El nombre de la contraseña no debe salir en una captura de la hoja.
        properties = ModalBottomSheetProperties(securePolicy = SecureFlagPolicy.SecureOn),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        SecureScreen()
        DeleteConfirmationContent(name = name, onConfirm = onConfirm, onDismiss = onDismiss, modifier = Modifier.navigationBarsPadding())
    }
}

@Composable
private fun DeleteConfirmationContent(
    name: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(start = 32.dp, end = 32.dp, top = 8.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .background(MaterialTheme.colorScheme.errorContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_trash_20),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.item_delete_title, name),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.item_delete_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        DangerButton(
            text = stringResource(R.string.item_delete_confirm),
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        SecondaryButton(
            text = stringResource(R.string.item_delete_cancel),
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun previewState(copied: CopiedNotice? = null) = ItemDetailUiState(
    status = DetailStatus.Found,
    name = "Gmail",
    initial = "G",
    edited = EditedAge.Months(3),
    username = "luis.ruiz@gmail.com",
    site = "accounts.google.com",
    siteOpenable = true,
    passwordLength = 12,
    copied = copied,
)

@Preview(name = "Detalle", device = "spec:width=412dp,height=917dp")
@Composable
private fun ItemDetailPreview() {
    PreviewSurface {
        ItemDetailScreen(previewState(), {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
    }
}

@Preview(name = "Detalle · copiada", device = "spec:width=412dp,height=917dp")
@Composable
private fun ItemDetailCopiedPreview() {
    PreviewSurface {
        ItemDetailScreen(previewState(CopiedNotice(CopiedKind.Password, 30)), {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
    }
}

@Preview(name = "Detalle · hoja de eliminar", widthDp = 412)
@Composable
private fun DeleteConfirmationPreview() {
    PreviewSurface {
        DeleteConfirmationContent(name = "Gmail", onConfirm = {}, onDismiss = {})
    }
}
