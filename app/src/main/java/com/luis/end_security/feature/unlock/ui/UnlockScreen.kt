package com.luis.end_security.feature.unlock.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luis.end_security.R
import com.luis.end_security.core.designsystem.component.BlobShape
import com.luis.end_security.core.designsystem.component.DangerButton
import com.luis.end_security.core.designsystem.component.OrganicBlob
import com.luis.end_security.core.designsystem.component.OutlinedIconButton
import com.luis.end_security.core.designsystem.component.PasswordField
import com.luis.end_security.core.designsystem.component.PrimaryButton
import com.luis.end_security.core.designsystem.component.PromptLink
import com.luis.end_security.core.designsystem.component.SecondaryButton
import com.luis.end_security.core.designsystem.component.TwoWeightTitle
import com.luis.end_security.core.designsystem.theme.PreviewSurface
import com.luis.end_security.core.security.ExcludeFromAutofill
import com.luis.end_security.core.security.SecureScreen
import com.luis.end_security.feature.auth.domain.MasterPasswordPolicy
import com.luis.end_security.feature.auth.ui.lockoutMessage

// Medidas de Figma (nodo 36:491). Como Iniciar sesión, pero el contenido empieza en y = 316
// del frame (276 bajo la barra de estado).
private val HeaderAreaHeight = 276.dp
private val ContentHorizontalPadding = 32.dp

@Composable
fun UnlockRoute(
    onNavigateToVault: () -> Unit,
    onNavigateToLogin: () -> Unit,
    viewModel: UnlockViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clearRequests by viewModel.clearRequests.collectAsStateWithLifecycle()
    // La contraseña maestra solo vive aquí, en memoria: sin rememberSaveable ni
    // rememberTextFieldState, que la escribirían en el Bundle (regla no negociable 6).
    val passwordState = remember { TextFieldState() }
    var passwordVisible by remember { mutableStateOf(false) }
    val currentOnAction by rememberUpdatedState(viewModel::onAction)
    val currentOnNavigateToVault by rememberUpdatedState(onNavigateToVault)
    val currentOnNavigateToLogin by rememberUpdatedState(onNavigateToLogin)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                UnlockEffect.NavigateToVault -> currentOnNavigateToVault()
                UnlockEffect.NavigateToLogin -> currentOnNavigateToLogin()
            }
        }
    }

    SecureScreen()
    ExcludeFromAutofill()
    LaunchedEffect(passwordState) {
        snapshotFlow { passwordState.text.isEmpty() }.collect { isEmpty ->
            currentOnAction(UnlockAction.PasswordEmptinessChanged(isEmpty))
        }
    }
    // Contraseña incorrecta o bloqueo: el ViewModel pide vaciar el campo.
    LaunchedEffect(clearRequests) {
        if (clearRequests > 0) passwordState.clearText()
    }
    // Se vacía al salir y al pasar a segundo plano (DEC-26).
    DisposableEffect(passwordState) {
        onDispose { passwordState.clearText() }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        passwordState.clearText()
        passwordVisible = false
    }
    // Atrás sale de la app sin cerrarla: debajo de Desbloquear nunca debe quedar una pantalla
    // con datos de la bóveda.
    val activity = LocalActivity.current
    BackHandler { activity?.moveTaskToBack(true) }

    UnlockScreen(
        state = state,
        passwordState = passwordState,
        passwordVisible = passwordVisible,
        onPasswordVisibleChange = { passwordVisible = it },
        onSubmit = { viewModel.onAction(UnlockAction.Submit(passwordState.text)) },
        onAction = viewModel::onAction,
    )
}

/** SCR-09 · Desbloquear. Tema claro (DEC-51), sin flecha de atrás. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UnlockScreen(
    state: UnlockUiState,
    passwordState: TextFieldState,
    passwordVisible: Boolean,
    onPasswordVisibleChange: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    onAction: (UnlockAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    // Con el teclado abierto, el campo y «Desbloquear» se desplazan hasta quedar a la vista.
    val formRequester = remember { BringIntoViewRequester() }
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(imeBottom) {
        if (imeBottom > 0) formRequester.bringIntoView()
    }
    val submit = {
        focusManager.clearFocus()
        onSubmit()
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        val viewportHeight = maxHeight
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .heightIn(min = viewportHeight),
        ) {
            UnlockHeader()
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = ContentHorizontalPadding),
            ) {
                TwoWeightTitle(
                    boldLine = stringResource(R.string.unlock_title_bold),
                    regularLine = stringResource(R.string.unlock_title_regular),
                    style = MaterialTheme.typography.displaySmall,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.unlock_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(28.dp))
                Column(modifier = Modifier.bringIntoViewRequester(formRequester)) {
                    PasswordField(
                        state = passwordState,
                        label = stringResource(R.string.unlock_password_label),
                        visible = passwordVisible,
                        onVisibleChange = onPasswordVisibleChange,
                        // Figma no tiene línea de ayuda; solo aparece con error. Bloqueo > incorrecta.
                        supportingText = when {
                            state.isLockedOut -> lockoutMessage(state.lockoutSecondsLeft)
                            state.wrongPassword -> stringResource(R.string.unlock_error_wrong_password)
                            else -> null
                        },
                        isError = state.isLockedOut || state.wrongPassword,
                        imeAction = ImeAction.Done,
                        onKeyboardAction = { if (state.canSubmit) submit() else focusManager.clearFocus() },
                        maxLength = MasterPasswordPolicy.MAX_FIELD_LENGTH,
                    )
                    Spacer(Modifier.height(28.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (state.biometricAvailable) {
                            OutlinedIconButton(
                                icon = R.drawable.ic_fingerprint_22,
                                contentDescription = stringResource(R.string.unlock_biometric),
                                onClick = { onAction(UnlockAction.BiometricClicked) },
                                size = 56.dp,
                            )
                        }
                        PrimaryButton(
                            text = stringResource(R.string.unlock_submit),
                            onClick = submit,
                            enabled = state.canSubmit,
                            loading = state.isLoading,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.weight(1f).heightIn(min = 24.dp))
                PromptLink(
                    prompt = state.displayName?.let { stringResource(R.string.unlock_not_you, it) }
                        ?: stringResource(R.string.unlock_not_you_unknown),
                    action = stringResource(R.string.unlock_sign_out),
                    onClick = { onAction(UnlockAction.SignOutClicked) },
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (state.showSignOutConfirmation) {
        SignOutConfirmationSheet(
            onConfirm = { onAction(UnlockAction.SignOutConfirmed) },
            onDismiss = { onAction(UnlockAction.SignOutDismissed) },
        )
    }
}

/** Marca y manchas de arriba a la derecha, en la misma posición que en Iniciar sesión. */
@Composable
private fun UnlockHeader() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(HeaderAreaHeight),
    ) {
        OrganicBlob(
            shape = BlobShape.B,
            grain = true,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 68.dp, y = 24.dp)
                .requiredSize(width = 230.dp, height = 210.dp),
        )
        OrganicBlob(
            shape = BlobShape.C,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-72).dp, y = 156.dp)
                .requiredSize(width = 108.dp, height = 94.dp),
        )
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 28.dp, top = 22.dp),
        )
    }
}

/** Confirmación de «Cerrar sesión» en hoja inferior (no está en Figma; mismos botones que DEC-56). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SignOutConfirmationSheet(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        // Explícito para no depender de que la ventana de la hoja herede FLAG_SECURE.
        properties = ModalBottomSheetProperties(securePolicy = SecureFlagPolicy.SecureOn),
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        SignOutConfirmationContent(
            onConfirm = onConfirm,
            onDismiss = onDismiss,
            modifier = Modifier.navigationBarsPadding(),
        )
    }
}

@Composable
private fun SignOutConfirmationContent(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(start = 32.dp, end = 32.dp, top = 8.dp, bottom = 16.dp)) {
        Text(
            text = stringResource(R.string.unlock_sign_out_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.unlock_sign_out_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        DangerButton(
            text = stringResource(R.string.unlock_sign_out_confirm),
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        SecondaryButton(
            text = stringResource(R.string.unlock_sign_out_cancel),
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun UnlockPreviewContent(state: UnlockUiState, password: String = "") {
    val passwordState = remember { TextFieldState(password) }
    PreviewSurface {
        UnlockScreen(
            state = state,
            passwordState = passwordState,
            passwordVisible = false,
            onPasswordVisibleChange = {},
            onSubmit = {},
            onAction = {},
        )
    }
}

private val PreviewState = UnlockUiState(displayName = "Ana", biometricAvailable = true)

@Preview(name = "Desbloquear · con huella", device = "spec:width=412dp,height=917dp")
@Composable
private fun UnlockWithBiometricPreview() =
    UnlockPreviewContent(PreviewState.copy(canSubmit = true), password = "contraseña de ejemplo")

@Preview(name = "Desbloquear · sin huella", device = "spec:width=412dp,height=917dp")
@Composable
private fun UnlockWithoutBiometricPreview() = UnlockPreviewContent(PreviewState.copy(biometricAvailable = false))

@Preview(name = "Desbloquear · contraseña incorrecta", device = "spec:width=412dp,height=917dp")
@Composable
private fun UnlockWrongPasswordPreview() = UnlockPreviewContent(PreviewState.copy(wrongPassword = true))

@Preview(name = "Desbloquear · bloqueo por intentos", device = "spec:width=412dp,height=917dp")
@Composable
private fun UnlockLockedOutPreview() = UnlockPreviewContent(PreviewState.copy(lockoutSecondsLeft = 14 * 60))

@Preview(name = "Desbloquear · pantalla baja", device = "spec:width=360dp,height=740dp")
@Composable
private fun UnlockSmallScreenPreview() =
    UnlockPreviewContent(PreviewState.copy(canSubmit = true), password = "contraseña de ejemplo")

// La hoja real abre su propia ventana, que la vista previa no dibuja: se muestra su contenido.
@Preview(name = "Desbloquear · hoja de cerrar sesión", widthDp = 412)
@Composable
private fun SignOutConfirmationPreview() {
    PreviewSurface {
        SignOutConfirmationContent(onConfirm = {}, onDismiss = {})
    }
}
