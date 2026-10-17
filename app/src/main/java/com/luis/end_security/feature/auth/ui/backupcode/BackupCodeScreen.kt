package com.luis.end_security.feature.auth.ui.backupcode

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luis.end_security.R
import com.luis.end_security.core.designsystem.component.BackTopBar
import com.luis.end_security.core.designsystem.component.LabeledTextField
import com.luis.end_security.core.designsystem.component.NeumorphicDisc
import com.luis.end_security.core.designsystem.component.PrimaryButton
import com.luis.end_security.core.designsystem.component.TwoWeightTitle
import com.luis.end_security.core.designsystem.theme.PreviewSurface
import com.luis.end_security.core.security.ExcludeFromAutofill
import com.luis.end_security.core.security.PasteOnlyTextToolbar
import com.luis.end_security.core.security.SecureScreen
import com.luis.end_security.feature.auth.ui.lockoutMessage

private val ContentHorizontalPadding = 32.dp

@Composable
fun BackupCodeRoute(
    onBack: () -> Unit,
    viewModel: BackupCodeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clearRequests by viewModel.clearRequests.collectAsStateWithLifecycle()
    // El código de respaldo es un secreto de larga vida hasta que se usa: igual que la contraseña,
    // vive solo aquí, en memoria (sin rememberSaveable ni rememberTextFieldState).
    val codeState = remember { TextFieldState() }
    val currentOnAction by rememberUpdatedState(viewModel::onAction)

    SecureScreen()
    ExcludeFromAutofill()
    LaunchedEffect(codeState) {
        snapshotFlow { codeState.text.length }.collect { length ->
            currentOnAction(BackupCodeAction.CodeLengthChanged(length))
        }
    }
    // Código inválido o bloqueo: el ViewModel pide vaciar el campo.
    LaunchedEffect(clearRequests) {
        if (clearRequests > 0) codeState.clearText()
    }
    // Se vacía al salir y al pasar a segundo plano (DEC-26).
    DisposableEffect(codeState) {
        onDispose { codeState.clearText() }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        codeState.clearText()
    }

    BackupCodeScreen(
        state = state,
        codeState = codeState,
        onSubmit = { viewModel.onAction(BackupCodeAction.Submit(codeState.text)) },
        onBack = onBack,
    )
}

/** SCR-08 · Código de respaldo. Tema claro (DEC-51). */
@Composable
fun BackupCodeScreen(
    state: BackupCodeUiState,
    codeState: TextFieldState,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val submit = {
        focusManager.clearFocus()
        onSubmit()
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
                    // Figma (nodo 36:449): disco de 104 con la llave de 38 en on-surface.
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        NeumorphicDisc(
                            icon = R.drawable.ic_key_38,
                            contentDescription = null,
                            size = 104.dp,
                            iconSize = 38.dp,
                            iconTint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(Modifier.height(56.dp))
                    TwoWeightTitle(
                        boldLine = stringResource(R.string.backup_code_title_bold),
                        regularLine = stringResource(R.string.backup_code_title_regular),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.backup_code_body),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(24.dp))
                    // Errores (no están en Figma) en la línea bajo el campo.
                    val error = when {
                        state.isLockedOut -> lockoutMessage(state.lockoutSecondsLeft)
                        state.invalidCode -> stringResource(R.string.backup_code_error_invalid)
                        else -> null
                    }
                    // Solo Pegar en el menú: el código no debe ir al portapapeles.
                    PasteOnlyTextToolbar {
                        LabeledTextField(
                            state = codeState,
                            label = stringResource(R.string.backup_code_label),
                            leadingIcon = R.drawable.ic_key_20,
                            supportingText = error,
                            isError = error != null,
                            inputTransformation = BackupCodeInputTransformation,
                            outputTransformation = BackupCodeOutputTransformation,
                            // Teclado de contraseña: los teclados no sugieren ni aprenden lo que se escribe
                            // ahí (con Ascii lo guardaban en su diccionario). Las mayúsculas las pone el filtro.
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                capitalization = KeyboardCapitalization.Characters,
                                autoCorrectEnabled = false,
                                imeAction = ImeAction.Done,
                            ),
                            onKeyboardAction = { if (state.canSubmit) submit() else focusManager.clearFocus() },
                        )
                    }
                    Spacer(Modifier.weight(1f).heightIn(min = 24.dp))
                    PrimaryButton(
                        text = stringResource(R.string.backup_code_submit),
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

@Composable
private fun BackupCodePreview(state: BackupCodeUiState, code: String = "") {
    val codeState = remember { TextFieldState(code) }
    PreviewSurface {
        BackupCodeScreen(state = state, codeState = codeState, onSubmit = {}, onBack = {})
    }
}

@Preview(name = "Código de respaldo · vacío", device = "spec:width=412dp,height=917dp")
@Composable
private fun BackupCodeEmptyPreview() = BackupCodePreview(BackupCodeUiState())

@Preview(name = "Código de respaldo · completo", device = "spec:width=412dp,height=917dp")
@Composable
private fun BackupCodeCompletePreview() = BackupCodePreview(BackupCodeUiState(canSubmit = true), code = "K7QD2M9X")

@Preview(name = "Código de respaldo · inválido", device = "spec:width=412dp,height=917dp")
@Composable
private fun BackupCodeInvalidPreview() = BackupCodePreview(BackupCodeUiState(invalidCode = true))

@Preview(name = "Código de respaldo · bloqueo por intentos", device = "spec:width=412dp,height=917dp")
@Composable
private fun BackupCodeLockedOutPreview() = BackupCodePreview(BackupCodeUiState(lockoutSecondsLeft = 9 * 60))
