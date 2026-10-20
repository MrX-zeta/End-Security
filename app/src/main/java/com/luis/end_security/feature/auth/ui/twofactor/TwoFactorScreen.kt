package com.luis.end_security.feature.auth.ui.twofactor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luis.end_security.core.security.SecureScreen
import com.luis.end_security.R
import com.luis.end_security.core.designsystem.component.BackTopBar
import com.luis.end_security.core.designsystem.component.CodeField
import com.luis.end_security.core.designsystem.component.InlineErrorMessage
import com.luis.end_security.core.designsystem.component.NeumorphicDisc
import com.luis.end_security.core.designsystem.component.PrimaryButton
import com.luis.end_security.core.designsystem.component.PromptLink
import com.luis.end_security.core.designsystem.component.TwoWeightTitle
import com.luis.end_security.core.designsystem.theme.PreviewSurface
import com.luis.end_security.feature.auth.ui.lockoutMessage

private val ContentHorizontalPadding = 32.dp

@Composable
fun TwoFactorRoute(
    onBack: () -> Unit,
    onUseBackupCode: () -> Unit,
    onNavigateToVault: () -> Unit,
    onNavigateToLogin: () -> Unit,
    viewModel: TwoFactorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Los códigos no deben verse en Recientes ni en capturas (INV-15).
    SecureScreen()
    val currentOnNavigateToVault by rememberUpdatedState(onNavigateToVault)
    val currentOnNavigateToLogin by rememberUpdatedState(onNavigateToLogin)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                TwoFactorEffect.NavigateToVault -> currentOnNavigateToVault()
                TwoFactorEffect.NavigateToLogin -> currentOnNavigateToLogin()
            }
        }
    }
    // Sin SecureScreen: el código dura 30 s y el servidor no lo acepta dos veces (anti-replay).
    TwoFactorScreen(state = state, onAction = viewModel::onAction, onBack = onBack, onUseBackupCode = onUseBackupCode)
}

/** SCR-07 · Verificación en dos pasos al entrar. Tema claro (DEC-51). */
@Composable
fun TwoFactorScreen(
    state: TwoFactorUiState,
    onAction: (TwoFactorAction) -> Unit,
    onBack: () -> Unit,
    onUseBackupCode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val submit = {
        focusManager.clearFocus()
        onAction(TwoFactorAction.Submit)
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        BackTopBar(onBack = onBack)
        BoxWithConstraints(modifier = Modifier.weight(1f)) {
            val viewportHeight = maxHeight
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Column(
                    modifier = Modifier
                        .heightIn(min = viewportHeight)
                        .padding(horizontal = ContentHorizontalPadding),
                ) {
                    Spacer(Modifier.height(24.dp))
                    // Figma (nodo 36:377): disco de 104 con el teléfono de 38 en on-surface.
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        NeumorphicDisc(
                            icon = R.drawable.ic_phone_38,
                            contentDescription = null,
                            size = 104.dp,
                            iconSize = 38.dp,
                            iconTint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(Modifier.height(56.dp))
                    TwoWeightTitle(
                        boldLine = stringResource(R.string.two_factor_title_bold),
                        regularLine = stringResource(R.string.two_factor_title_regular),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.two_factor_body),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(24.dp))
                    CodeField(
                        value = state.code,
                        onValueChange = { onAction(TwoFactorAction.CodeChanged(it)) },
                        isError = state.wrongCode || state.isLockedOut || state.genericError,
                        onDone = { if (state.canSubmit) submit() else focusManager.clearFocus() },
                    )
                    // Errores que no están en Figma, bajo las casillas.
                    val error = when {
                        state.isLockedOut -> lockoutMessage(state.lockoutSecondsLeft)
                        state.wrongCode -> stringResource(R.string.two_factor_error_wrong_code)
                        state.genericError -> stringResource(R.string.auth_error_generic)
                        else -> null
                    }
                    if (error != null) {
                        Spacer(Modifier.height(12.dp))
                        InlineErrorMessage(text = error)
                    }
                    Spacer(Modifier.weight(1f).heightIn(min = 24.dp))
                    PrimaryButton(
                        text = stringResource(R.string.two_factor_submit),
                        onClick = submit,
                        enabled = state.canSubmit,
                        loading = state.isLoading,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(4.dp))
                    PromptLink(
                        prompt = stringResource(R.string.two_factor_no_phone),
                        action = stringResource(R.string.two_factor_use_backup_code),
                        onClick = onUseBackupCode,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun TwoFactorPreview(state: TwoFactorUiState) {
    PreviewSurface {
        TwoFactorScreen(state = state, onAction = {}, onBack = {}, onUseBackupCode = {})
    }
}

@Preview(name = "2FA · vacía", device = "spec:width=412dp,height=917dp")
@Composable
private fun TwoFactorEmptyPreview() = TwoFactorPreview(TwoFactorUiState())

@Preview(name = "2FA · completa", device = "spec:width=412dp,height=917dp")
@Composable
private fun TwoFactorCompletePreview() = TwoFactorPreview(TwoFactorUiState(code = "270915", canSubmit = true))

@Preview(name = "2FA · código incorrecto", device = "spec:width=412dp,height=917dp")
@Composable
private fun TwoFactorWrongCodePreview() = TwoFactorPreview(TwoFactorUiState(wrongCode = true))

@Preview(name = "2FA · bloqueo por intentos", device = "spec:width=412dp,height=917dp")
@Composable
private fun TwoFactorLockedOutPreview() = TwoFactorPreview(TwoFactorUiState(lockoutSecondsLeft = 15 * 60))
