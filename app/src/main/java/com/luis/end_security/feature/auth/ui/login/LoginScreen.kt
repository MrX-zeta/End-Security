package com.luis.end_security.feature.auth.ui.login

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luis.end_security.R
import com.luis.end_security.core.designsystem.component.BlobShape
import com.luis.end_security.core.designsystem.component.LabeledTextField
import com.luis.end_security.core.designsystem.component.OrganicBlob
import com.luis.end_security.core.designsystem.component.PasswordField
import com.luis.end_security.core.designsystem.component.PrimaryButton
import com.luis.end_security.core.designsystem.component.TwoWeightTitle
import com.luis.end_security.core.designsystem.theme.PreviewSurface
import com.luis.end_security.core.security.ExcludeFromAutofill
import com.luis.end_security.core.security.SecureScreen
import com.luis.end_security.feature.auth.domain.MasterPasswordPolicy

// Medidas de Figma (nodo 19:144). La cabecera ocupa hasta y = 300 del frame (260 bajo la
// barra de estado) y deja espacio a las manchas de arriba a la derecha.
private val HeaderAreaHeight = 260.dp
private val ContentHorizontalPadding = 32.dp

@Composable
fun LoginRoute(
    onCreateAccount: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // La contraseña maestra solo vive aquí, en memoria: sin rememberSaveable ni
    // rememberTextFieldState, que la escribirían en el Bundle (regla no negociable 6).
    val passwordState = remember { TextFieldState() }
    var passwordVisible by remember { mutableStateOf(false) }
    val currentOnAction by rememberUpdatedState(viewModel::onAction)

    SecureScreen()
    ExcludeFromAutofill()
    LaunchedEffect(passwordState) {
        snapshotFlow { passwordState.text.isEmpty() }.collect { isEmpty ->
            currentOnAction(LoginAction.PasswordEmptinessChanged(isEmpty))
        }
    }
    // Al salir de la pantalla se vacía el campo. TextFieldState no permite sobrescribir su
    // memoria; cuando exista el envío, la copia en CharArray sí se llenará de ceros.
    DisposableEffect(passwordState) {
        onDispose { passwordState.clearText() }
    }
    // Lo mismo al pasar a segundo plano (DEC-26): quien tome el teléfono y vuelva a la app no
    // encuentra la contraseña escrita ni a la vista.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        passwordState.clearText()
        passwordVisible = false
    }

    LoginScreen(
        state = state,
        passwordState = passwordState,
        passwordVisible = passwordVisible,
        onPasswordVisibleChange = { passwordVisible = it },
        onAction = viewModel::onAction,
        onCreateAccount = onCreateAccount,
    )
}

/** SCR-04 · Iniciar sesión. Tema claro (DEC-51). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LoginScreen(
    state: LoginUiState,
    passwordState: TextFieldState,
    passwordVisible: Boolean,
    onPasswordVisibleChange: (Boolean) -> Unit,
    onAction: (LoginAction) -> Unit,
    onCreateAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    // Con el teclado abierto, los campos y «Entrar» se desplazan hasta quedar a la vista.
    // Se repite con cada altura del teclado para que el último ajuste sea con la altura final.
    val formRequester = remember { BringIntoViewRequester() }
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(imeBottom) {
        if (imeBottom > 0) formRequester.bringIntoView()
    }
    val submit = {
        focusManager.clearFocus()
        onAction(LoginAction.Submit)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState()),
    ) {
        LoginHeader()
        Column(modifier = Modifier.padding(horizontal = ContentHorizontalPadding)) {
            TwoWeightTitle(
                boldLine = stringResource(R.string.login_title_bold),
                regularLine = stringResource(R.string.login_title_regular),
                style = MaterialTheme.typography.displaySmall,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.login_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(28.dp))
            Column(modifier = Modifier.bringIntoViewRequester(formRequester)) {
                LabeledTextField(
                    value = state.email,
                    onValueChange = { onAction(LoginAction.EmailChanged(it)) },
                    label = stringResource(R.string.login_email_label),
                    leadingIcon = R.drawable.ic_mail_20,
                    contentType = ContentType.EmailAddress,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Next,
                    ),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                )
                Spacer(Modifier.height(20.dp))
                PasswordField(
                    state = passwordState,
                    label = stringResource(R.string.login_password_label),
                    visible = passwordVisible,
                    onVisibleChange = onPasswordVisibleChange,
                    supportingText = stringResource(
                        if (state.error != null) R.string.login_error_invalid_credentials else R.string.login_password_help,
                    ),
                    isError = state.error != null,
                    imeAction = ImeAction.Done,
                    onKeyboardAction = { if (state.canSubmit) submit() else focusManager.clearFocus() },
                    maxLength = MasterPasswordPolicy.MAX_FIELD_LENGTH,
                )
                Spacer(Modifier.height(32.dp))
                PrimaryButton(
                    text = stringResource(R.string.login_submit),
                    onClick = submit,
                    enabled = state.canSubmit,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(8.dp))
            CreateAccountLink(onClick = onCreateAccount)
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Marca y manchas de arriba a la derecha (la grande con grano y una pequeña `primary`). */
@Composable
private fun LoginHeader() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(HeaderAreaHeight),
    ) {
        // En Figma la mancha grande sale 68 por la derecha y la pequeña queda a 72 del borde.
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

/** «¿Primera vez? Crea tu cuenta»: toda la línea es el botón (48 dp de alto) hacia SCR-05. */
@Composable
private fun CreateAccountLink(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.login_first_time),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.login_create_account),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textDecoration = TextDecoration.Underline,
        )
    }
}

@Composable
private fun LoginPreviewContent(
    state: LoginUiState,
    password: String = "",
    passwordVisible: Boolean = false,
) {
    val passwordState = remember { TextFieldState(password) }
    PreviewSurface {
        LoginScreen(
            state = state,
            passwordState = passwordState,
            passwordVisible = passwordVisible,
            onPasswordVisibleChange = {},
            onAction = {},
            onCreateAccount = {},
        )
    }
}

private val FilledState = LoginUiState(email = "ana.lopez@correo.com", isEmailValid = true, canSubmit = true)

@Preview(name = "Iniciar sesión · vacía", device = "spec:width=412dp,height=917dp")
@Composable
private fun LoginEmptyPreview() {
    LoginPreviewContent(state = LoginUiState())
}

@Preview(name = "Iniciar sesión · llena", device = "spec:width=412dp,height=917dp")
@Composable
private fun LoginFilledPreview() {
    LoginPreviewContent(state = FilledState, password = "contraseña de ejemplo")
}

@Preview(name = "Iniciar sesión · contraseña visible", device = "spec:width=412dp,height=917dp")
@Composable
private fun LoginPasswordVisiblePreview() {
    LoginPreviewContent(state = FilledState, password = "contraseña de ejemplo", passwordVisible = true)
}

@Preview(name = "Iniciar sesión · error", device = "spec:width=412dp,height=917dp")
@Composable
private fun LoginErrorPreview() {
    LoginPreviewContent(
        state = FilledState.copy(error = LoginError.InvalidCredentials),
        password = "contraseña de ejemplo",
    )
}

@Preview(name = "Iniciar sesión · pantalla baja", device = "spec:width=360dp,height=740dp")
@Composable
private fun LoginSmallScreenPreview() {
    LoginPreviewContent(state = FilledState, password = "contraseña de ejemplo")
}
