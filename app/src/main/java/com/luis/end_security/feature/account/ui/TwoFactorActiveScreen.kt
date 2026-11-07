package com.luis.end_security.feature.account.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luis.end_security.R
import com.luis.end_security.core.designsystem.component.DangerButton
import com.luis.end_security.core.designsystem.component.PrimaryButton
import com.luis.end_security.core.designsystem.component.SecondaryButton
import com.luis.end_security.core.designsystem.component.TwoWeightTitle
import com.luis.end_security.core.security.SecureScreen
import com.luis.end_security.feature.auth.ui.lockoutMessage

/**
 * «Verificación en dos pasos está activa» (Figma 218:4304; OP-24, OP-25) con sus hojas de reautenticación (218:4421) y
 * la vista de los códigos de respaldo nuevos. Los códigos nuevos son secretos: la pantalla lleva `SecureScreen`.
 */
@Composable
fun TwoFactorActiveRoute(
    onBack: () -> Unit,
    onDisabled: () -> Unit,
    viewModel: TwoFactorManageViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SecureScreen()
    val currentOnDisabled by rememberUpdatedState(onDisabled)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                ManageEffect.Disabled -> currentOnDisabled()
            }
        }
    }
    // Atrás desde los códigos nuevos cierra esa vista; desde la pantalla activa, vuelve a Ajustes.
    BackHandler(enabled = state.newCodes != null) { viewModel.onAction(ManageAction.CodesClosed) }
    TwoFactorActiveScreen(state = state, onAction = viewModel::onAction, onBack = onBack)
}

@Composable
fun TwoFactorActiveScreen(
    state: TwoFactorManageUiState,
    onAction: (ManageAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val newCodes = state.newCodes
    FlowScaffold(
        onBack = { if (newCodes != null) onAction(ManageAction.CodesClosed) else onBack() },
        modifier = modifier,
        toast = state.copiedSeconds?.let { stringResource(R.string.tf_copied, it) },
        bottom = {
            if (newCodes != null) {
                SecondaryButton(
                    text = stringResource(R.string.tf_copy_codes),
                    onClick = { onAction(ManageAction.CopyCodes) },
                    modifier = Modifier.fillMaxWidth(),
                )
                PrimaryButton(
                    text = stringResource(R.string.tf_codes_saved),
                    onClick = { onAction(ManageAction.CodesClosed) },
                    showArrow = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                SecondaryButton(
                    text = stringResource(R.string.tf_regenerate),
                    onClick = { onAction(ManageAction.RegenerateClicked) },
                    modifier = Modifier.fillMaxWidth(),
                )
                DangerButton(
                    text = stringResource(R.string.tf_disable),
                    onClick = { onAction(ManageAction.DisableClicked) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    ) {
        if (newCodes != null) {
            TwoWeightTitle(
                boldLine = stringResource(R.string.tf_new_codes_title_bold),
                regularLine = stringResource(R.string.tf_new_codes_title_regular),
                style = TitleStyle(),
            )
            Spacer(Modifier.height(16.dp))
            RecoveryCodesBody(codes = newCodes, notice = stringResource(R.string.tf_new_codes_notice))
        } else {
            TwoWeightTitle(
                boldLine = stringResource(R.string.tf_active_title_bold),
                regularLine = stringResource(R.string.tf_active_title_regular),
                style = TitleStyle(),
            )
            Spacer(Modifier.height(16.dp))
            ProtectedCard()
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.tf_active_codes_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    state.sheet?.let { purpose ->
        val regenerate = purpose == ReauthPurpose.Regenerate
        ReauthSheet(
            body = stringResource(if (regenerate) R.string.tf_reauth_body_regenerate else R.string.tf_reauth_body_disable),
            confirmLabel = stringResource(if (regenerate) R.string.tf_reauth_generate else R.string.tf_disable),
            danger = !regenerate,
            state = ReauthSheetState(
                code = state.code,
                showPasswordRequired = state.showPasswordRequired,
                showCodeIncomplete = state.showCodeIncomplete,
                credentialsWrong = state.credentialsWrong,
                otherError = when {
                    state.isLockedOut -> lockoutMessage(state.lockoutSecondsLeft)
                    state.genericError -> stringResource(R.string.auth_error_generic)
                    else -> null
                },
                isLoading = state.isLoading,
                focus = state.focusRequest,
            ),
            onCodeChange = { onAction(ManageAction.CodeChanged(it)) },
            onPasswordEmptinessChanged = { onAction(ManageAction.PasswordEmptinessChanged(it)) },
            onConfirm = { password -> onAction(ManageAction.Submit(password)) },
            onDismiss = { onAction(ManageAction.SheetDismissed) },
        )
    }
}

/** Tarjeta oscura «Protegida» con el escudo amarillo (Figma 218:4304). */
@Composable
private fun ProtectedCard() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.onSurface, RoundedCornerShape(20.dp))
            .padding(horizontal = 20.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.tf_protected_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.background,
            )
            Text(
                text = stringResource(R.string.tf_protected_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.background,
            )
        }
        Box(
            modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_shield_check_22),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
