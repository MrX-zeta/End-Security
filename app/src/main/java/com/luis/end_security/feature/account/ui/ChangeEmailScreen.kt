package com.luis.end_security.feature.account.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.luis.end_security.core.designsystem.component.StepSwap
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luis.end_security.R
import com.luis.end_security.core.designsystem.component.InterceptTabSelection
import com.luis.end_security.core.designsystem.component.isKeyboardVisible
import com.luis.end_security.core.designsystem.component.BackTopBar
import com.luis.end_security.core.designsystem.component.CodeField
import com.luis.end_security.core.designsystem.component.LabeledTextField
import com.luis.end_security.core.designsystem.component.NeumorphicDisc
import com.luis.end_security.core.designsystem.component.PrimaryButton
import com.luis.end_security.core.designsystem.component.bringIntoViewWhenKeyboardOpens
import com.luis.end_security.core.designsystem.component.ReadOnlyField
import com.luis.end_security.core.designsystem.component.TwoWeightTitle
import com.luis.end_security.core.designsystem.component.onFocusLost
import com.luis.end_security.core.security.ExcludeFromAutofill
import com.luis.end_security.core.security.SecureScreen
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role

/**
 * Cambiar el correo (OP-26; Figma 227:4534, 227:4726, 227:4802, 229:4852). Un solo destino con dos pasos: el correo nuevo y el
 * código. La contraseña maestra solo se escribe en la hoja de reautenticación (`TextFieldState`, regla 6); lo demás son correos y un
 * código de un solo uso. El paso del correo no lleva `SecureScreen`; el paso del código sí (tarea 22, paso 0), y la hoja oculta su contenido.
 */
@Composable
fun ChangeEmailRoute(
    onBack: () -> Unit,
    onChanged: (String) -> Unit,
    viewModel: ChangeEmailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnBack by rememberUpdatedState(onBack)
    val currentOnChanged by rememberUpdatedState(onChanged)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                ChangeEmailEffect.Leave -> currentOnBack()
                is ChangeEmailEffect.Changed -> currentOnChanged(effect.email)
            }
        }
    }
    // Atrás del sistema: del código vuelve al correo nuevo; con la solicitud en curso no hace nada.
    BackHandler(enabled = state.step == EmailStep.Code || state.isBusy || state.sheetOpen) { viewModel.onAction(ChangeEmailAction.Back) }
    // Mientras trabaja, la barra inferior no cambia de pestaña.
    InterceptTabSelection { state.isBusy }
    ChangeEmailScreen(
        state = state,
        onAction = viewModel::onAction,
    )
}

@Composable
fun ChangeEmailScreen(
    state: ChangeEmailUiState,
    onAction: (ChangeEmailAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Correo nuevo → código: la misma transición de avanzar y regresar de la navegación (60 dp y fundido, mismo camino al volver).
    StepSwap(step = state.step.ordinal, modifier = modifier) { index ->
        when (EmailStep.entries[index]) {
            EmailStep.Enter -> EnterEmailScreen(state, onAction, Modifier)
            EmailStep.Code -> EmailCodeScreen(state, onAction, Modifier)
        }
    }
}

// ---------------------------------------------------------------- Correo nuevo

@Composable
private fun EnterEmailScreen(
    state: ChangeEmailUiState,
    onAction: (ChangeEmailAction) -> Unit,
    modifier: Modifier,
) {
    val emailFocus = remember { FocusRequester() }
    LaunchedEffect(state.codeFocusRequest) {
        if (state.codeFocusRequest > 0) emailFocus.requestFocus()
    }
    FlowScaffold(
        onBack = { onAction(ChangeEmailAction.Back) },
        modifier = modifier,
        title = stringResource(R.string.email_title),
        bottom = {
            // Siempre activo (tarea 15): lo que falta se explica al tocarlo.
            PrimaryButton(
                text = stringResource(R.string.email_continue),
                onClick = { onAction(ChangeEmailAction.Continue) },
                showArrow = false,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        EmailNoticeBanner(stringResource(R.string.email_notice))
        Spacer(Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Solo lectura y atenuado (Figma 227:4534).
            ReadOnlyField(
                label = stringResource(R.string.email_current_label),
                displayText = state.currentEmail,
                leadingIcon = R.drawable.ic_mail_20,
                // Atenuado: es de solo lectura (Figma 227:4534).
                modifier = Modifier.alpha(0.6f),
            )
            LabeledTextField(
                value = state.newEmail,
                onValueChange = { onAction(ChangeEmailAction.NewEmailChanged(it)) },
                label = stringResource(R.string.email_new_label),
                leadingIcon = R.drawable.ic_mail_20,
                errorText = when {
                    state.showFormatError -> stringResource(R.string.auth_error_email_invalid)
                    state.showSameAsCurrent -> stringResource(R.string.email_same)
                    else -> null
                },
                modifier = Modifier.focusRequester(emailFocus).onFocusLost { onAction(ChangeEmailAction.NewEmailFocusLost) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onAction(ChangeEmailAction.Continue) }),
            )
            // Espera de DEC-63: gris, con reloj; no es un error de la persona (Figma 227:4726).
            state.wait?.let { WaitLine(emailWaitText(it)) }
            if (state.genericError) {
                Text(
                    text = stringResource(R.string.auth_error_generic),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }
    if (state.sheetOpen) {
        EmailReauthSheet(state, onAction)
    }
}

@Composable
private fun EmailNoticeBanner(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.onSurface, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_info_20),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Text(text = text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.background)
    }
}

@Composable
private fun WaitLine(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_clock_16),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Text(text = text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun emailWaitText(wait: EmailWait): String = when (wait) {
    is EmailWait.Hours -> pluralStringResource(R.plurals.email_wait_hours, wait.count, wait.count)
    is EmailWait.Minutes -> pluralStringResource(R.plurals.email_wait_minutes, wait.count, wait.count)
    is EmailWait.Seconds -> pluralStringResource(R.plurals.email_wait_seconds, wait.count, wait.count)
}

/** La hoja de reautenticación de siempre (Figma 218:4421); sin el campo del código si la cuenta no tiene 2FA. */
@Composable
private fun EmailReauthSheet(state: ChangeEmailUiState, onAction: (ChangeEmailAction) -> Unit) {
    val required = state.twoFactorRequired == true
    ReauthSheet(
        body = stringResource(if (required) R.string.email_reauth_body else R.string.email_reauth_body_no_totp),
        confirmLabel = stringResource(R.string.email_reauth_send),
        danger = false,
        showCode = required,
        state = ReauthSheetState(
            code = state.sheetCode,
            showPasswordRequired = state.showPasswordRequired,
            showCodeIncomplete = state.showCodeIncomplete,
            credentialsWrong = state.credentialsWrong,
            otherError = when {
                state.sheetConflict -> stringResource(R.string.cp_conflict)
                state.sheetGenericError -> stringResource(R.string.auth_error_generic)
                else -> null
            },
            isLoading = state.isRequesting,
            focus = state.focusRequest,
        ),
        onCodeChange = { onAction(ChangeEmailAction.SheetCodeChanged(it)) },
        onPasswordEmptinessChanged = { onAction(ChangeEmailAction.PasswordEmptinessChanged(it)) },
        onConfirm = { password -> onAction(ChangeEmailAction.SendCode(password)) },
        onDismiss = { onAction(ChangeEmailAction.SheetDismissed) },
    )
}

// ---------------------------------------------------------------- Código

/** Mismo diseño que Verificar correo (Figma 227:4802), con los textos del cambio de correo. */
@Composable
private fun EmailCodeScreen(state: ChangeEmailUiState, onAction: (ChangeEmailAction) -> Unit, modifier: Modifier) {
    // El código de un solo uso y el correo nuevo no se capturan ni salen en la vista de apps recientes (regla 7).
    SecureScreen()
    val focusManager = LocalFocusManager.current
    val submit = {
        focusManager.clearFocus()
        onAction(ChangeEmailAction.ConfirmCode)
    }
    val codeFocus = remember { FocusRequester() }
    LaunchedEffect(state.codeFocusRequest) {
        if (state.codeFocusRequest > 0) codeFocus.requestFocus()
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        BackTopBar(onBack = { onAction(ChangeEmailAction.Back) })
        BoxWithConstraints(modifier = Modifier.weight(1f)) {
            val viewportHeight = maxHeight
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Column(modifier = Modifier.heightIn(min = viewportHeight).padding(horizontal = 32.dp)) {
                    // Con el teclado abierto se oculta el disco decorativo: así el campo y el botón caben a la vista (tarea 23).
                    if (isKeyboardVisible()) {
                        Spacer(Modifier.height(8.dp))
                    } else {
                        Spacer(Modifier.height(24.dp))
                        androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            NeumorphicDisc(
                                icon = R.drawable.ic_mail_38,
                                contentDescription = null,
                                size = 104.dp,
                                iconSize = 38.dp,
                                iconTint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Spacer(Modifier.height(56.dp))
                    }
                    TwoWeightTitle(
                        boldLine = stringResource(R.string.verify_title_bold),
                        regularLine = stringResource(R.string.verify_title_regular),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.email_code_body, state.newEmail),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(28.dp))
                    CodeField(
                        value = state.code,
                        onValueChange = { onAction(ChangeEmailAction.CodeChanged(it)) },
                        errorText = state.codeError?.let { codeErrorMessage(it) }
                            ?: if (state.showCodeIncompleteInline) stringResource(R.string.code_error_incomplete) else null,
                        modifier = Modifier.focusRequester(codeFocus),
                        onDone = { submit() },
                    )
                    Spacer(Modifier.height(18.dp))
                    // «¿No te llegó? Pide otro código»: vuelve al correo nuevo ya escrito (sustituye la espera de Figma).
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.email_code_resend_before),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(R.string.email_code_resend_link),
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .clickable(role = Role.Button) { onAction(ChangeEmailAction.RequestAnotherCode) }
                                .padding(horizontal = 4.dp, vertical = 14.dp),
                        )
                    }
                    // Solo en depuración con el backend falso (E1): nunca va a logcat.
                    state.debugCode?.let { code ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.verify_debug_code, code),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.weight(1f).heightIn(min = 24.dp))
                    PrimaryButton(
                        text = stringResource(R.string.email_code_submit),
                        onClick = submit,
                        enabled = state.codeError != EmailCodeError.Expired,
                        loading = state.isConfirming,
                        modifier = Modifier.fillMaxWidth().bringIntoViewWhenKeyboardOpens(),
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun codeErrorMessage(error: EmailCodeError): String = when (error) {
    is EmailCodeError.WrongCode -> pluralStringResource(R.plurals.verify_error_wrong_code, error.attemptsLeft, error.attemptsLeft)
    EmailCodeError.Expired -> stringResource(R.string.verify_error_expired)
    EmailCodeError.Generic -> stringResource(R.string.auth_error_generic)
    is EmailCodeError.RateLimited -> stringResource(R.string.auth_lockout, error.minutes)
}
