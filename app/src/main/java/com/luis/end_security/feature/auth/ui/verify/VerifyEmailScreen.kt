package com.luis.end_security.feature.auth.ui.verify

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luis.end_security.R
import com.luis.end_security.core.designsystem.component.BackTopBar
import com.luis.end_security.core.designsystem.component.CodeField
import com.luis.end_security.core.designsystem.component.NeumorphicDisc
import com.luis.end_security.core.designsystem.component.PrimaryButton
import com.luis.end_security.core.designsystem.component.TextLink
import com.luis.end_security.core.designsystem.component.TwoWeightTitle
import com.luis.end_security.core.designsystem.theme.PreviewSurface

private val ContentHorizontalPadding = 32.dp

@Composable
fun VerifyEmailRoute(
    onBack: () -> Unit,
    viewModel: VerifyEmailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Sin SecureScreen ni ExcludeFromAutofill: el código es de un solo uso y el servidor limita
    // los intentos (tarea 04). Aun así no va en toString() ni en logs.
    VerifyEmailScreen(state = state, onAction = viewModel::onAction, onBack = onBack)
}

/** SCR-06 · Verificar correo. Tema claro (DEC-51). */
@Composable
fun VerifyEmailScreen(
    state: VerifyEmailUiState,
    onAction: (VerifyEmailAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val submit = {
        focusManager.clearFocus()
        onAction(VerifyEmailAction.Submit)
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
        // El botón queda abajo cuando todo cabe y se desplaza con el contenido cuando no.
        BoxWithConstraints(modifier = Modifier.weight(1f)) {
            val viewportHeight = maxHeight
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Column(
                    modifier = Modifier
                        .heightIn(min = viewportHeight)
                        .padding(horizontal = ContentHorizontalPadding),
                ) {
                    Spacer(Modifier.height(24.dp))
                    // Figma (nodo 36:304): disco de 104 con el sobre de 38 en on-surface.
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        NeumorphicDisc(
                            icon = R.drawable.ic_mail_38,
                            contentDescription = null,
                            size = 104.dp,
                            iconSize = 38.dp,
                            iconTint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(Modifier.height(56.dp))
                    TwoWeightTitle(
                        boldLine = stringResource(R.string.verify_title_bold),
                        regularLine = stringResource(R.string.verify_title_regular),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.verify_body, state.email),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(28.dp))
                    CodeField(
                        value = state.code,
                        onValueChange = { onAction(VerifyEmailAction.CodeChanged(it)) },
                        isError = state.error != null,
                        onDone = { if (state.canSubmit) submit() else focusManager.clearFocus() },
                    )
                    state.error?.let { error ->
                        Spacer(Modifier.height(12.dp))
                        ErrorLine(error)
                    }
                    Spacer(Modifier.height(18.dp))
                    ResendLine(state = state, onResend = { onAction(VerifyEmailAction.Resend) })
                    Spacer(Modifier.weight(1f).heightIn(min = 24.dp))
                    PrimaryButton(
                        text = stringResource(R.string.verify_submit),
                        onClick = submit,
                        enabled = state.canSubmit,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

/** Error bajo las casillas (no está en Figma): ícono de información y texto en `error`. */
@Composable
private fun ErrorLine(error: VerifyEmailError) {
    val text = when (error) {
        is VerifyEmailError.WrongCode ->
            pluralStringResource(R.plurals.verify_error_wrong_code, error.attemptsLeft, error.attemptsLeft)
        VerifyEmailError.Expired -> stringResource(R.string.verify_error_expired)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_info_16),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(16.dp),
        )
        Text(text = text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

/** «Puedes pedir otro código en m:ss» durante la espera; después, «Reenviar código». */
@Composable
private fun ResendLine(state: VerifyEmailUiState, onResend: () -> Unit) {
    if (state.canResend) {
        TextLink(
            text = stringResource(R.string.verify_resend),
            onClick = onResend,
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_clock_16),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = stringResource(R.string.verify_resend_wait, formatCountdown(state.resendSecondsLeft)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val PreviewState = VerifyEmailUiState(email = "ana.lopez@correo.com", resendSecondsLeft = 45)

@Composable
private fun VerifyPreview(state: VerifyEmailUiState) {
    PreviewSurface {
        VerifyEmailScreen(state = state, onAction = {}, onBack = {})
    }
}

@Preview(name = "Verificar · vacía", device = "spec:width=412dp,height=917dp")
@Composable
private fun VerifyEmptyPreview() = VerifyPreview(PreviewState)

@Preview(name = "Verificar · a medias", device = "spec:width=412dp,height=917dp")
@Composable
private fun VerifyPartialPreview() = VerifyPreview(PreviewState.copy(code = "481"))

@Preview(name = "Verificar · completa", device = "spec:width=412dp,height=917dp")
@Composable
private fun VerifyCompletePreview() = VerifyPreview(PreviewState.copy(code = "481920", canSubmit = true))

@Preview(name = "Verificar · código incorrecto", device = "spec:width=412dp,height=917dp")
@Composable
private fun VerifyWrongCodePreview() = VerifyPreview(PreviewState.copy(error = VerifyEmailError.WrongCode(attemptsLeft = 3)))

@Preview(name = "Verificar · código vencido", device = "spec:width=412dp,height=917dp")
@Composable
private fun VerifyExpiredPreview() = VerifyPreview(PreviewState.copy(error = VerifyEmailError.Expired))

@Preview(name = "Verificar · reenviar código", device = "spec:width=412dp,height=917dp")
@Composable
private fun VerifyResendPreview() = VerifyPreview(PreviewState.copy(resendSecondsLeft = 0))

@Preview(name = "Verificar · pantalla baja", device = "spec:width=360dp,height=740dp")
@Composable
private fun VerifySmallScreenPreview() = VerifyPreview(PreviewState.copy(code = "4819"))
