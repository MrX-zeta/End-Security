package com.luis.end_security.feature.account.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luis.end_security.core.designsystem.component.Reveal
import com.luis.end_security.core.designsystem.component.RevealValue
import com.luis.end_security.R
import com.luis.end_security.core.designsystem.component.InterceptTabSelection
import com.luis.end_security.core.designsystem.component.FieldErrorText
import com.luis.end_security.core.designsystem.component.LabeledTextField
import com.luis.end_security.core.designsystem.component.PasswordField
import com.luis.end_security.core.designsystem.component.PrimaryButton
import com.luis.end_security.core.designsystem.component.RequirementLine
import com.luis.end_security.core.designsystem.component.RequirementState
import com.luis.end_security.core.designsystem.component.StrengthMeter
import com.luis.end_security.core.designsystem.component.onFocusLost
import com.luis.end_security.core.designsystem.theme.EndSecurityTheme
import com.luis.end_security.core.security.BiometricPromptTexts
import com.luis.end_security.core.security.ExcludeFromAutofill
import com.luis.end_security.core.security.SecureScreen
import com.luis.end_security.core.security.authenticateWithCipher
import com.luis.end_security.core.security.findFragmentActivity
import com.luis.end_security.feature.auth.domain.MasterPasswordPolicy
import com.luis.end_security.feature.auth.ui.lockoutMessage
import com.luis.end_security.feature.auth.ui.signup.PasswordStrengthLevel
import com.luis.end_security.feature.auth.ui.signup.StrengthColorRole
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce

private const val PasswordDebounceMillis = 150L

/**
 * Contraseña maestra (SCR-35, Figma 47:2098; OP-27): cambia la contraseña con la misma `vault_key`. Muestra y maneja
 * contraseñas: sin capturas, sin autocompletar, y los tres campos viven solo en `TextFieldState` de esta pantalla (regla 6).
 */
@OptIn(FlowPreview::class)
@Composable
fun ChangePasswordRoute(
    onBack: () -> Unit,
    onFinish: (biometricOff: Boolean) -> Unit,
    onNavigateToLogin: () -> Unit,
    viewModel: ChangePasswordViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SecureScreen()
    ExcludeFromAutofill()
    // Sin rememberSaveable ni rememberTextFieldState: escribirían las contraseñas en el Bundle (regla 6).
    val currentState = remember { TextFieldState() }
    val newState = remember { TextFieldState() }
    val confirmationState = remember { TextFieldState() }
    var currentVisible by remember { mutableStateOf(false) }
    var newVisible by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val currentOnAction by rememberUpdatedState(viewModel::onAction)
    val currentOnFinish by rememberUpdatedState(onFinish)
    val currentOnNavigateToLogin by rememberUpdatedState(onNavigateToLogin)
    val promptTexts = BiometricPromptTexts(
        title = stringResource(R.string.cp_biometric_prompt_title),
        subtitle = null,
        negativeButton = stringResource(R.string.biometric_cancel),
    )
    val currentTexts by rememberUpdatedState(promptTexts)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ChangePasswordEffect.Finish -> currentOnFinish(effect.biometricOff)
                ChangePasswordEffect.NavigateToLogin -> currentOnNavigateToLogin()
                is ChangePasswordEffect.ShowBiometricPrompt -> {
                    val activity = context.findFragmentActivity()
                    if (activity == null) {
                        currentOnAction(ChangePasswordAction.BiometricCancelled)
                    } else {
                        activity.authenticateWithCipher(
                            cipher = effect.cipher,
                            texts = currentTexts,
                            onSuccess = { cipher -> currentOnAction(ChangePasswordAction.BiometricAuthenticated(cipher)) },
                            onFailure = { currentOnAction(ChangePasswordAction.BiometricCancelled) },
                        )
                    }
                }
            }
        }
    }
    LaunchedEffect(currentState) {
        snapshotFlow { currentState.text.isEmpty() }.collect { currentOnAction(ChangePasswordAction.CurrentEmptinessChanged(it)) }
    }
    LaunchedEffect(newState, confirmationState) {
        snapshotFlow { newState.text to confirmationState.text }
            .debounce(PasswordDebounceMillis)
            .collect { (password, confirmation) -> currentOnAction(ChangePasswordAction.NewPasswordsChanged(password, confirmation)) }
    }
    // Al salir y al pasar a segundo plano se vacían los tres campos y se vuelven a ocultar (DEC-26).
    DisposableEffect(currentState, newState, confirmationState) {
        onDispose {
            currentState.clearText()
            newState.clearText()
            confirmationState.clearText()
            currentOnAction(ChangePasswordAction.NewPasswordsCleared)
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        // Con el cambio en curso no se vacía: el texto ya se mandó y la pantalla sigue hasta terminar.
        if (!state.isBusy) {
            currentState.clearText()
            newState.clearText()
            confirmationState.clearText()
            currentVisible = false
            newVisible = false
            currentOnAction(ChangePasswordAction.NewPasswordsCleared)
        }
    }
    // Atrás está deshabilitado mientras dura el cambio.
    BackHandler(enabled = state.isBusy) {}
    // Mientras trabaja, la barra inferior no cambia de pestaña.
    InterceptTabSelection { state.isBusy }
    ChangePasswordScreen(
        state = state,
        currentState = currentState,
        newState = newState,
        confirmationState = confirmationState,
        currentVisible = currentVisible,
        onCurrentVisibleChange = { currentVisible = it },
        newVisible = newVisible,
        onNewVisibleChange = { newVisible = it },
        onConfirmationFocusLost = {
            // La coincidencia se evalúa con el texto actual, sin esperar el debounce.
            viewModel.onAction(ChangePasswordAction.NewPasswordsChanged(newState.text, confirmationState.text))
            viewModel.onAction(ChangePasswordAction.ConfirmationFocusLost)
        },
        onSubmit = {
            viewModel.onAction(ChangePasswordAction.Submit(currentState.text, newState.text, confirmationState.text))
        },
        onAction = viewModel::onAction,
        onBack = { if (!state.isBusy) onBack() },
    )
}

@Composable
fun ChangePasswordScreen(
    state: ChangePasswordUiState,
    currentState: TextFieldState,
    newState: TextFieldState,
    confirmationState: TextFieldState,
    currentVisible: Boolean,
    onCurrentVisibleChange: (Boolean) -> Unit,
    newVisible: Boolean,
    onNewVisibleChange: (Boolean) -> Unit,
    onConfirmationFocusLost: () -> Unit,
    onSubmit: () -> Unit,
    onAction: (ChangePasswordAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentFocus = remember { FocusRequester() }
    val codeFocus = remember { FocusRequester() }
    val newFocus = remember { FocusRequester() }
    val confirmationFocus = remember { FocusRequester() }
    val currentBring = remember { BringIntoViewRequester() }
    val codeBring = remember { BringIntoViewRequester() }
    val newBring = remember { BringIntoViewRequester() }
    val confirmationBring = remember { BringIntoViewRequester() }
    // Con errores al tocar el botón, se desplaza al primer campo con error y se le da el foco (tarea 15).
    LaunchedEffect(state.focusRequest) {
        val request = state.focusRequest ?: return@LaunchedEffect
        val (focus, bring) = when (request.field) {
            ChangeField.Current -> currentFocus to currentBring
            ChangeField.Code -> codeFocus to codeBring
            ChangeField.New -> newFocus to newBring
            ChangeField.Confirmation -> confirmationFocus to confirmationBring
        }
        bring.bringIntoView()
        focus.requestFocus()
    }
    FlowScaffold(
        onBack = onBack,
        modifier = modifier,
        title = stringResource(R.string.cp_title),
        bottom = {
            // Siempre activo (tarea 15): lo que falta se explica al tocarlo.
            PrimaryButton(
                text = stringResource(R.string.cp_submit),
                onClick = onSubmit,
                enabled = !state.isLockedOut,
                loading = state.isLoading,
                showArrow = false,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        NoticeBanner(stringResource(R.string.cp_notice))
        Spacer(Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PasswordField(
                state = currentState,
                label = stringResource(R.string.cp_current_label),
                visible = currentVisible,
                onVisibleChange = onCurrentVisibleChange,
                errorText = when {
                    state.showCurrentRequired -> stringResource(R.string.auth_error_password_required)
                    state.credentialsWrong -> stringResource(R.string.cp_wrong)
                    else -> null
                },
                modifier = Modifier.bringIntoViewRequester(currentBring).focusRequester(currentFocus),
                imeAction = ImeAction.Next,
                maxLength = MasterPasswordPolicy.MAX_FIELD_LENGTH,
            )
            // El código de la app solo con la 2FA activa (no está en Figma: mismo estilo que la hoja de reautenticación).
            if (state.twoFactorRequired == true) {
                LabeledTextField(
                    value = state.code,
                    onValueChange = { onAction(ChangePasswordAction.CodeChanged(it)) },
                    label = stringResource(R.string.tf_reauth_code_label),
                    leadingIcon = R.drawable.ic_shield_check_22,
                    placeholder = stringResource(R.string.tf_reauth_code_placeholder),
                    // Credenciales incorrectas marca los dos campos; el mensaje va una sola vez, bajo la contraseña actual.
                    isError = state.credentialsWrong,
                    errorText = if (state.showCodeIncomplete) stringResource(R.string.code_error_incomplete) else null,
                    modifier = Modifier.bringIntoViewRequester(codeBring).focusRequester(codeFocus),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Next),
                )
            }
            PasswordField(
                state = newState,
                label = stringResource(R.string.cp_new_label),
                visible = newVisible,
                onVisibleChange = onNewVisibleChange,
                errorText = when {
                    state.showNewRequired -> stringResource(R.string.auth_error_password_required)
                    state.showSameAsCurrent -> stringResource(R.string.cp_same)
                    state.breach == BreachCheck.Found -> stringResource(R.string.cp_breach_found)
                    state.showWeak -> stringResource(R.string.signup_weak_password)
                    else -> null
                },
                modifier = Modifier.bringIntoViewRequester(newBring).focusRequester(newFocus),
                imeAction = ImeAction.Next,
                maxLength = MasterPasswordPolicy.MAX_FIELD_LENGTH,
            )
            NewPasswordStrength(state)
            PasswordField(
                state = confirmationState,
                label = stringResource(R.string.cp_confirm_label),
                visible = false,
                onVisibleChange = null,
                errorText = if (state.showMismatch) stringResource(R.string.signup_mismatch) else null,
                modifier = Modifier
                    .bringIntoViewRequester(confirmationBring)
                    .focusRequester(confirmationFocus)
                    .onFocusLost(onConfirmationFocusLost),
                imeAction = ImeAction.Done,
                maxLength = MasterPasswordPolicy.MAX_FIELD_LENGTH,
            )
        }
        // La lista de requisitos se despliega al empezar a escribir y se pliega con caracteres prohibidos.
        Reveal(visible = state.newTyped && !state.hasForbiddenCharacters) {
            Column {
                Spacer(Modifier.height(12.dp))
                Requirements(state)
            }
        }
        // Errores del cambio que no son de un campo.
        val message = when {
            state.conflict -> stringResource(R.string.cp_conflict)
            state.isLockedOut -> lockoutMessage(state.lockoutSecondsLeft)
            state.genericError -> stringResource(R.string.auth_error_generic)
            else -> null
        }
        RevealValue(message) { text ->
            Column {
                Spacer(Modifier.height(12.dp))
                FieldErrorText(text)
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** Aviso oscuro de arriba (Figma 47:2098). */
@Composable
private fun NoticeBanner(text: String) {
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

/** Medidor bajo «Nueva contraseña» (DEC-31), igual que en Crear cuenta. */
@Composable
private fun NewPasswordStrength(state: ChangePasswordUiState) {
    val level = state.strengthScore?.let { PasswordStrengthLevel.fromScore(it) }
    val levelColor = when (level?.colorRole) {
        StrengthColorRole.Error -> MaterialTheme.colorScheme.error
        StrengthColorRole.OnSurfaceVariant -> MaterialTheme.colorScheme.onSurfaceVariant
        StrengthColorRole.Success -> EndSecurityTheme.extendedColors.success
        null -> MaterialTheme.colorScheme.outline
    }
    val label = when {
        state.hasForbiddenCharacters -> stringResource(R.string.signup_forbidden_characters)
        state.exceedsMaxLength -> stringResource(R.string.signup_max_length)
        level == null -> null
        else -> stringResource(level.label)
    }
    StrengthMeter(
        filledBars = level?.filledBars ?: 0,
        color = levelColor,
        label = label,
        labelColor = if (state.hasForbiddenCharacters) MaterialTheme.colorScheme.error else levelColor,
    )
}

/**
 * Lista de requisitos (Figma 47:2098): los de Crear cuenta más «No aparece en filtraciones», que se revisa **al tocar el
 * botón** (antes va en gris) y, sin conexión, dice «No se pudo revisar (sin conexión)» sin bloquear.
 */
@Composable
private fun Requirements(state: ChangePasswordUiState) {
    // La confirmación se evalúa en cuanto tiene el mismo largo o más; antes, la línea va en gris neutro.
    val confirmationReady = state.confirmationLength >= state.passwordLength && state.confirmationLength > 0
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        RequirementLine(
            text = stringResource(R.string.signup_req_length),
            state = if (state.meetsLength) RequirementState.Met else RequirementState.Unmet,
        )
        RequirementLine(
            text = stringResource(R.string.signup_req_upper),
            state = if (state.hasUppercase) RequirementState.Met else RequirementState.Unmet,
        )
        RequirementLine(
            text = stringResource(R.string.signup_req_lower),
            state = if (state.hasLowercase) RequirementState.Met else RequirementState.Unmet,
        )
        RequirementLine(
            text = stringResource(R.string.signup_req_digit),
            state = if (state.hasDigit) RequirementState.Met else RequirementState.Unmet,
        )
        RequirementLine(
            text = stringResource(R.string.signup_req_match),
            state = when {
                !confirmationReady -> RequirementState.Neutral
                state.confirmationMatches -> RequirementState.Met
                else -> RequirementState.Unmet
            },
        )
        RequirementLine(
            text = stringResource(if (state.breach == BreachCheck.Offline) R.string.cp_req_breach_offline else R.string.cp_req_breach),
            state = when (state.breach) {
                BreachCheck.Clean -> RequirementState.Met
                BreachCheck.Found -> RequirementState.Unmet
                BreachCheck.Pending, BreachCheck.Offline -> RequirementState.Neutral
            },
        )
    }
}
