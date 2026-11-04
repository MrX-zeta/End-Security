package com.luis.end_security.feature.account.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luis.end_security.R
import com.luis.end_security.core.designsystem.component.BottomNavTab
import com.luis.end_security.core.designsystem.component.DangerButton
import com.luis.end_security.core.designsystem.component.FieldErrorText
import com.luis.end_security.core.designsystem.component.LabeledTextField
import com.luis.end_security.core.designsystem.component.PasswordField
import com.luis.end_security.core.designsystem.component.SecondaryButton
import com.luis.end_security.core.designsystem.component.SheetIconDisc
import com.luis.end_security.core.designsystem.component.TwoWeightTitle
import com.luis.end_security.core.security.ExcludeFromAutofill
import com.luis.end_security.core.security.SecureScreen
import com.luis.end_security.feature.auth.domain.MasterPasswordPolicy
import com.luis.end_security.feature.auth.ui.lockoutMessage

/**
 * SCR-36 · Eliminar cuenta (Figma 47:2290; OP-28). Maneja una contraseña: sin capturas, sin autocompletar, y el campo vive solo
 * en un `TextFieldState` de esta pantalla (regla 6). Al tocar el botón **no** llama al servidor: abre la hoja de confirmación
 * (SCR-37, Figma 62:3233).
 */
@Composable
fun DeleteAccountRoute(
    onBack: () -> Unit,
    onTabSelected: (BottomNavTab) -> Unit,
    viewModel: DeleteAccountViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SecureScreen()
    ExcludeFromAutofill()
    // Sin rememberSaveable ni rememberTextFieldState: escribirían la contraseña en el Bundle (regla 6).
    val passwordState = remember { TextFieldState() }
    var passwordVisible by remember { mutableStateOf(false) }
    val currentOnAction by rememberUpdatedState(viewModel::onAction)
    LaunchedEffect(passwordState) {
        snapshotFlow { passwordState.text.isEmpty() }.collect { currentOnAction(DeleteAccountAction.PasswordEmptinessChanged(it)) }
    }
    // Al salir y al pasar a segundo plano se vacía el campo y se vuelve a ocultar (DEC-26).
    DisposableEffect(passwordState) { onDispose { passwordState.clearText() } }
    // Con la eliminación en curso no se vacía al pasar a segundo plano (el texto ya se mandó); si termina con error mientras la
    // app sigue detenida, se vacía en cuanto termina para que no quede la contraseña en el campo.
    var stoppedWhileBusy by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (!state.isLoading) {
            passwordState.clearText()
            passwordVisible = false
        } else {
            stoppedWhileBusy = true
        }
    }
    LaunchedEffect(state.isLoading) {
        if (!state.isLoading && stoppedWhileBusy) {
            passwordState.clearText()
            passwordVisible = false
            stoppedWhileBusy = false
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { stoppedWhileBusy = false }
    // Atrás está deshabilitado mientras dura la eliminación; con la hoja abierta, cierra la hoja.
    BackHandler(enabled = state.isLoading) {}
    DeleteAccountScreen(
        state = state,
        passwordState = passwordState,
        passwordVisible = passwordVisible,
        onPasswordVisibleChange = { passwordVisible = it },
        onSubmit = { viewModel.onAction(DeleteAccountAction.Submit(passwordState.text)) },
        onConfirm = { viewModel.onAction(DeleteAccountAction.Confirm(passwordState.text)) },
        onAction = viewModel::onAction,
        onBack = { if (!state.isLoading) onBack() },
        onTabSelected = { tab -> if (!state.isLoading) onTabSelected(tab) },
    )
}

@Composable
fun DeleteAccountScreen(
    state: DeleteAccountUiState,
    passwordState: TextFieldState,
    passwordVisible: Boolean,
    onPasswordVisibleChange: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    onConfirm: () -> Unit,
    onAction: (DeleteAccountAction) -> Unit,
    onBack: () -> Unit,
    onTabSelected: (BottomNavTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val passwordFocus = remember { FocusRequester() }
    val codeFocus = remember { FocusRequester() }
    val passwordBring = remember { BringIntoViewRequester() }
    val codeBring = remember { BringIntoViewRequester() }
    // Con errores al tocar el botón, se desplaza al primer campo con error y se le da el foco (tarea 15).
    LaunchedEffect(state.focusRequest) {
        val request = state.focusRequest ?: return@LaunchedEffect
        val (focus, bring) = when (request.field) {
            DeleteField.Password -> passwordFocus to passwordBring
            DeleteField.Code -> codeFocus to codeBring
        }
        bring.bringIntoView()
        focus.requestFocus()
    }
    FlowScaffold(
        onBack = onBack,
        onTabSelected = onTabSelected,
        modifier = modifier,
        title = stringResource(R.string.delete_title),
        bottom = {
            // Siempre activo (tarea 15): lo que falta se explica al tocarlo.
            DangerButton(
                text = stringResource(R.string.delete_submit),
                onClick = onSubmit,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        TwoWeightTitle(
            boldLine = stringResource(R.string.delete_heading_bold),
            regularLine = stringResource(R.string.delete_heading_regular),
            style = TitleStyle(),
        )
        Spacer(Modifier.height(16.dp))
        ConsequencesCard()
        Spacer(Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PasswordField(
                state = passwordState,
                label = stringResource(R.string.delete_password_label),
                visible = passwordVisible,
                onVisibleChange = onPasswordVisibleChange,
                errorText = when {
                    state.showPasswordRequired -> stringResource(R.string.auth_error_password_required)
                    state.credentialsWrong -> stringResource(R.string.cp_wrong)
                    else -> null
                },
                modifier = Modifier.bringIntoViewRequester(passwordBring).focusRequester(passwordFocus),
                imeAction = if (state.twoFactorRequired == true) ImeAction.Next else ImeAction.Done,
                maxLength = MasterPasswordPolicy.MAX_FIELD_LENGTH,
            )
            // El código de la app solo con la 2FA activa (Figma lo muestra siempre; aquí solo cuando hace falta).
            if (state.twoFactorRequired == true) {
                LabeledTextField(
                    value = state.code,
                    onValueChange = { onAction(DeleteAccountAction.CodeChanged(it)) },
                    label = stringResource(R.string.tf_reauth_code_label),
                    leadingIcon = R.drawable.ic_shield_check_22,
                    placeholder = stringResource(R.string.tf_reauth_code_placeholder),
                    // Credenciales incorrectas marca los dos campos; el mensaje va una sola vez, bajo la contraseña.
                    isError = state.credentialsWrong,
                    errorText = if (state.showCodeIncomplete) stringResource(R.string.code_error_incomplete) else null,
                    modifier = Modifier.bringIntoViewRequester(codeBring).focusRequester(codeFocus),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                )
            }
        }
        // Errores de la eliminación que no son de un campo.
        val message = when {
            state.conflict -> stringResource(R.string.cp_conflict)
            state.isLockedOut -> lockoutMessage(state.lockoutSecondsLeft)
            state.genericError -> stringResource(R.string.auth_error_generic)
            else -> null
        }
        if (message != null) {
            Spacer(Modifier.height(12.dp))
            FieldErrorText(message)
        }
        Spacer(Modifier.height(16.dp))
    }
    state.confirmCount?.let { count ->
        DeleteConfirmSheet(
            count = count,
            loading = state.isLoading,
            error = state.sheetError,
            onConfirm = onConfirm,
            onDismiss = { onAction(DeleteAccountAction.SheetDismissed) },
        )
    }
}

/** Tarjeta roja con las tres consecuencias (Figma 47:2290). */
@Composable
private fun ConsequencesCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Consequence(R.drawable.ic_trash_20, stringResource(R.string.delete_consequence_passwords))
        Consequence(R.drawable.ic_log_out_20, stringResource(R.string.delete_consequence_sessions))
        Consequence(R.drawable.ic_user_20, stringResource(R.string.delete_consequence_history))
    }
}

@Composable
private fun Consequence(@DrawableRes icon: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp),
        )
        Text(text = text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeleteConfirmSheet(count: Int, loading: Boolean, error: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        // Con la eliminación en curso la hoja no se cierra ni con el gesto ni con Atrás.
        sheetGesturesEnabled = !loading,
        properties = ModalBottomSheetProperties(securePolicy = SecureFlagPolicy.SecureOn, shouldDismissOnBackPress = !loading),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        DeleteConfirmSheetContent(
            count = count,
            loading = loading,
            error = error,
            onConfirm = onConfirm,
            onCancel = onDismiss,
            modifier = Modifier.navigationBarsPadding(),
        )
    }
}

/** Hoja «¿Eliminar tu cuenta para siempre?» (Figma 62:3233; SCR-37). */
@Composable
fun DeleteConfirmSheetContent(
    count: Int,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    error: Boolean = false,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = 8.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SheetIconDisc(R.drawable.ic_trash_20, danger = true)
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.delete_sheet_title),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (count > 0) {
                pluralStringResource(R.plurals.delete_sheet_body, count, count)
            } else {
                stringResource(R.string.delete_sheet_body_empty)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (error) {
            Spacer(Modifier.height(12.dp))
            FieldErrorText(stringResource(R.string.auth_error_generic))
        }
        Spacer(Modifier.height(20.dp))
        DangerButton(
            text = stringResource(R.string.delete_sheet_confirm),
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
