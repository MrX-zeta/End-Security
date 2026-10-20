package com.luis.end_security.feature.auth.ui.signup

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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luis.end_security.R
import com.luis.end_security.core.designsystem.component.BackTopBar
import com.luis.end_security.core.designsystem.component.Checkbox
import com.luis.end_security.core.designsystem.component.LabeledTextField
import com.luis.end_security.core.designsystem.component.NoticeCard
import com.luis.end_security.core.designsystem.component.PasswordField
import com.luis.end_security.core.designsystem.component.InlineErrorMessage
import com.luis.end_security.core.designsystem.component.PrimaryButton
import com.luis.end_security.core.designsystem.component.StrengthMeter
import com.luis.end_security.core.designsystem.component.TwoWeightTitle
import com.luis.end_security.core.designsystem.theme.EndSecurityTheme
import com.luis.end_security.core.designsystem.theme.PreviewSurface
import com.luis.end_security.core.security.ExcludeFromAutofill
import com.luis.end_security.core.security.SecureScreen
import com.luis.end_security.feature.auth.domain.MasterPasswordPolicy
import com.luis.end_security.feature.settings.ui.legal.LegalDocument
import com.luis.end_security.feature.settings.ui.legal.LegalDocumentSheet
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce

// Espera tras la última tecla antes de recalcular fuerza y coincidencia.
private const val PasswordDebounceMillis = 150L
private val ContentHorizontalPadding = 32.dp

@OptIn(FlowPreview::class)
@Composable
fun SignUpRoute(
    onBack: () -> Unit,
    onNavigateToVerifyEmail: (email: String) -> Unit,
    viewModel: SignUpViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Las contraseñas solo viven aquí, en memoria: sin rememberSaveable ni
    // rememberTextFieldState, que las escribirían en el Bundle (regla no negociable 6).
    val passwordState = remember { TextFieldState() }
    val confirmationState = remember { TextFieldState() }
    var passwordVisible by remember { mutableStateOf(false) }
    // Documento legal abierto en la hoja inferior (sobre esta pantalla, sin perder lo escrito).
    var openedDocument by remember { mutableStateOf<LegalDocument?>(null) }
    val currentOnAction by rememberUpdatedState(viewModel::onAction)
    val currentOnNavigateToVerifyEmail by rememberUpdatedState(onNavigateToVerifyEmail)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is SignUpEffect.NavigateToVerifyEmail -> currentOnNavigateToVerifyEmail(effect.email)
            }
        }
    }

    SecureScreen()
    ExcludeFromAutofill()
    LaunchedEffect(passwordState, confirmationState) {
        snapshotFlow { passwordState.text to confirmationState.text }
            .debounce(PasswordDebounceMillis)
            .collect { (password, confirmation) ->
                currentOnAction(SignUpAction.PasswordsChanged(password, confirmation))
            }
    }
    // Al salir y al pasar a segundo plano se vacían los dos campos y se vuelve a ocultar (DEC-26).
    // El ViewModel se entera al momento: no debe quedarse con el puntaje de un texto que ya no está.
    DisposableEffect(passwordState, confirmationState) {
        onDispose {
            passwordState.clearText()
            confirmationState.clearText()
            currentOnAction(SignUpAction.PasswordsCleared)
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        passwordState.clearText()
        confirmationState.clearText()
        passwordVisible = false
        currentOnAction(SignUpAction.PasswordsCleared)
    }

    SignUpScreen(
        state = state,
        passwordState = passwordState,
        confirmationState = confirmationState,
        passwordVisible = passwordVisible,
        onPasswordVisibleChange = { passwordVisible = it },
        onConfirmationFocusLost = {
            // La coincidencia se evalúa con el texto actual, sin esperar el debounce.
            viewModel.onAction(SignUpAction.PasswordsChanged(passwordState.text, confirmationState.text))
            viewModel.onAction(SignUpAction.ConfirmationFocusLost)
        },
        onSubmit = {
            // La política se revisa sobre este mismo texto (no sobre el último cálculo del medidor).
            viewModel.onAction(SignUpAction.Submit(passwordState.text, confirmationState.text))
        },
        onAction = viewModel::onAction,
        onBack = onBack,
        openedDocument = openedDocument,
        onOpenLegalDocument = { openedDocument = it },
        onDismissLegalDocument = { openedDocument = null },
    )
}

/** SCR-05 · Crear cuenta. Tema claro (DEC-51). Solo interfaz: el envío llega con OP-11. */
@Composable
fun SignUpScreen(
    state: SignUpUiState,
    passwordState: TextFieldState,
    confirmationState: TextFieldState,
    passwordVisible: Boolean,
    onPasswordVisibleChange: (Boolean) -> Unit,
    onConfirmationFocusLost: () -> Unit,
    onSubmit: () -> Unit,
    onAction: (SignUpAction) -> Unit,
    onBack: () -> Unit,
    openedDocument: LegalDocument?,
    onOpenLegalDocument: (LegalDocument) -> Unit,
    onDismissLegalDocument: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
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
                    Spacer(Modifier.height(8.dp))
                    TwoWeightTitle(
                        boldLine = stringResource(R.string.signup_title_bold),
                        regularLine = stringResource(R.string.signup_title_regular),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.signup_subtitle),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(22.dp))
                    LabeledTextField(
                        value = state.email,
                        onValueChange = { onAction(SignUpAction.EmailChanged(it)) },
                        label = stringResource(R.string.signup_email_label),
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
                    Spacer(Modifier.height(14.dp))
                    PasswordField(
                        state = passwordState,
                        label = stringResource(R.string.signup_password_label),
                        visible = passwordVisible,
                        onVisibleChange = onPasswordVisibleChange,
                        imeAction = ImeAction.Next,
                        onKeyboardAction = { focusManager.moveFocus(FocusDirection.Down) },
                        maxLength = MasterPasswordPolicy.MAX_FIELD_LENGTH,
                    )
                    Spacer(Modifier.height(10.dp))
                    PasswordStrength(state)
                    Spacer(Modifier.height(14.dp))
                    ConfirmationField(
                        state = confirmationState,
                        showMismatchError = state.showMismatchError,
                        onFocusLost = onConfirmationFocusLost,
                        onDone = {
                            focusManager.clearFocus()
                            onSubmit()
                        },
                    )
                    Spacer(Modifier.height(16.dp))
                    NoticeCard(text = stringResource(R.string.signup_notice))
                    Spacer(Modifier.height(12.dp))
                    TermsRow(
                        accepted = state.termsAccepted,
                        onAcceptedChange = { onAction(SignUpAction.TermsAcceptedChanged(it)) },
                        onOpenLegalDocument = onOpenLegalDocument,
                    )
                    Spacer(Modifier.weight(1f).heightIn(min = 24.dp))
                    state.submitError?.let { error ->
                        InlineErrorMessage(
                            text = when (error) {
                                SignUpSubmitError.Generic -> stringResource(R.string.auth_error_generic)
                                is SignUpSubmitError.RateLimited -> stringResource(R.string.auth_lockout, error.minutes)
                            },
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    PrimaryButton(
                        text = stringResource(R.string.signup_submit),
                        onClick = {
                            focusManager.clearFocus()
                            onSubmit()
                        },
                        enabled = state.canSubmit,
                        loading = state.isLoading,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
    if (openedDocument != null) {
        LegalDocumentSheet(document = openedDocument, onDismiss = onDismissLegalDocument)
    }
}

/** Medidor bajo la contraseña (DEC-31). Sin etiqueta con el campo vacío. */
@Composable
private fun PasswordStrength(state: SignUpUiState) {
    val score = state.strengthScore
    val level = score?.let { PasswordStrengthLevel.fromScore(it) }
    val levelColor = level?.colorRole?.toColor() ?: MaterialTheme.colorScheme.outline
    val label = when {
        state.hasForbiddenCharacters -> stringResource(R.string.signup_forbidden_characters)
        state.exceedsMaxLength -> stringResource(R.string.signup_max_length)
        level == null -> null
        !state.meetsLength -> stringResource(R.string.signup_min_length)
        else -> stringResource(level.label)
    }
    // Los avisos de longitud son información neutra; las barras siguen mostrando el puntaje.
    val lengthNotice = state.exceedsMaxLength || (level != null && !state.meetsLength)
    // Los caracteres invisibles impiden crear la cuenta: va en error, no como aviso neutro.
    val labelColor = when {
        state.hasForbiddenCharacters -> MaterialTheme.colorScheme.error
        lengthNotice -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> levelColor
    }
    StrengthMeter(
        filledBars = level?.filledBars ?: 0,
        color = levelColor,
        label = label,
        labelColor = labelColor,
    )
}

@Composable
private fun StrengthColorRole.toColor(): Color = when (this) {
    StrengthColorRole.Error -> MaterialTheme.colorScheme.error
    StrengthColorRole.OnSurfaceVariant -> MaterialTheme.colorScheme.onSurfaceVariant
    StrengthColorRole.Success -> EndSecurityTheme.extendedColors.success
}

@Composable
private fun ConfirmationField(
    state: TextFieldState,
    showMismatchError: Boolean,
    onFocusLost: () -> Unit,
    onDone: () -> Unit,
) {
    var hadFocus by remember { mutableStateOf(false) }
    PasswordField(
        state = state,
        label = stringResource(R.string.signup_confirm_label),
        visible = false,
        onVisibleChange = null,
        supportingText = if (showMismatchError) stringResource(R.string.signup_mismatch) else null,
        isError = showMismatchError,
        imeAction = ImeAction.Done,
        onKeyboardAction = { onDone() },
        maxLength = MasterPasswordPolicy.MAX_FIELD_LENGTH,
        modifier = Modifier.onFocusChanged { focus ->
            if (hadFocus && !focus.hasFocus) onFocusLost()
            hadFocus = focus.hasFocus
        },
    )
}

/**
 * «Acepto los Términos y el Aviso de privacidad» (PEND-12). La casilla tiene 48 dp de área y
 * los dos enlaces abren su documento. La casilla no se marca sola al volver.
 */
@Composable
private fun TermsRow(
    accepted: Boolean,
    onAcceptedChange: (Boolean) -> Unit,
    onOpenLegalDocument: (LegalDocument) -> Unit,
) {
    val termsLabel = stringResource(R.string.legal_terms_title)
    val privacyLabel = stringResource(R.string.legal_privacy_title)
    val sentence = stringResource(R.string.signup_terms_sentence, termsLabel, privacyLabel)
    val linkStyles = TextLinkStyles(
        style = SpanStyle(
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textDecoration = TextDecoration.Underline,
        ),
    )
    val text = remember(sentence, linkStyles) {
        sentence.withLinks(
            termsLabel to LinkAnnotation.Clickable("terms", linkStyles) { onOpenLegalDocument(LegalDocument.Terms) },
            privacyLabel to LinkAnnotation.Clickable("privacy", linkStyles) { onOpenLegalDocument(LegalDocument.Privacy) },
        )
    }
    // La casilla mide 48 de área pero se dibuja de 24: se corre 12 para alinear su borde
    // visible con el contenido, como en Figma.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .offset(x = (-12).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = accepted,
            onCheckedChange = onAcceptedChange,
            label = sentence,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Marca como enlace cada fragmento indicado dentro del texto. */
private fun String.withLinks(vararg links: Pair<String, LinkAnnotation>): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    val sorted = links.mapNotNull { (fragment, link) ->
        indexOf(fragment).takeIf { it >= 0 }?.let { Triple(it, fragment, link) }
    }.sortedBy { it.first }
    sorted.forEach { (start, fragment, link) ->
        append(this@withLinks.substring(cursor, start))
        withLink(link) { append(fragment) }
        cursor = start + fragment.length
    }
    append(this@withLinks.substring(cursor))
}

@Composable
private fun SignUpPreviewContent(
    state: SignUpUiState,
    password: String = "",
    confirmation: String = "",
    openedDocument: LegalDocument? = null,
) {
    val passwordState = remember { TextFieldState(password) }
    val confirmationState = remember { TextFieldState(confirmation) }
    PreviewSurface {
        SignUpScreen(
            state = state,
            passwordState = passwordState,
            confirmationState = confirmationState,
            passwordVisible = false,
            onPasswordVisibleChange = {},
            onConfirmationFocusLost = {},
            onSubmit = {},
            onAction = {},
            onBack = {},
            openedDocument = openedDocument,
            onOpenLegalDocument = {},
            onDismissLegalDocument = {},
        )
    }
}

private val ValidEmail = SignUpUiState(email = "ana.lopez@correo.com", isEmailValid = true)

@Preview(name = "Crear cuenta · vacía", device = "spec:width=412dp,height=917dp")
@Composable
private fun SignUpEmptyPreview() {
    SignUpPreviewContent(state = SignUpUiState())
}

@Preview(name = "Crear cuenta · débil", device = "spec:width=412dp,height=917dp")
@Composable
private fun SignUpWeakPreview() {
    SignUpPreviewContent(
        state = ValidEmail.copy(strengthScore = 1, meetsLength = false),
        password = "contraseña",
    )
}

@Preview(name = "Crear cuenta · regular", device = "spec:width=412dp,height=917dp")
@Composable
private fun SignUpFairPreview() {
    SignUpPreviewContent(
        state = ValidEmail.copy(strengthScore = 2, meetsLength = true),
        password = "contraseña2026",
    )
}

@Preview(name = "Crear cuenta · fuerte y todo válido", device = "spec:width=412dp,height=917dp")
@Composable
private fun SignUpStrongPreview() {
    SignUpPreviewContent(
        state = ValidEmail.copy(
            strengthScore = 3,
            meetsLength = true,
            confirmationMatches = true,
            termsAccepted = true,
            canSubmit = true,
        ),
        password = "frase de ejemplo larga",
        confirmation = "frase de ejemplo larga",
    )
}

@Preview(name = "Crear cuenta · no coinciden", device = "spec:width=412dp,height=917dp")
@Composable
private fun SignUpMismatchPreview() {
    SignUpPreviewContent(
        state = ValidEmail.copy(strengthScore = 4, meetsLength = true, showMismatchError = true),
        password = "frase de ejemplo larga",
        confirmation = "frase de ejemplo",
    )
}

@Preview(name = "Crear cuenta · pantalla baja", device = "spec:width=360dp,height=740dp")
@Composable
private fun SignUpSmallScreenPreview() {
    SignUpPreviewContent(
        state = ValidEmail.copy(strengthScore = 3, meetsLength = true, termsAccepted = true),
        password = "frase de ejemplo larga",
    )
}

@Preview(name = "Crear cuenta · hoja de Términos", device = "spec:width=412dp,height=917dp")
@Composable
private fun SignUpLegalSheetPreview() {
    SignUpPreviewContent(
        state = ValidEmail.copy(strengthScore = 3, meetsLength = true),
        password = "frase de ejemplo larga",
        openedDocument = LegalDocument.Terms,
    )
}
