package com.luis.end_security.feature.vault.ui.edit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luis.end_security.R
import com.luis.end_security.core.designsystem.component.BackButton
import com.luis.end_security.core.designsystem.component.BottomNavBar
import com.luis.end_security.core.designsystem.component.BottomNavTab
import com.luis.end_security.core.designsystem.component.DangerButton
import com.luis.end_security.core.designsystem.component.FieldActionButton
import com.luis.end_security.core.designsystem.component.InlineErrorMessage
import com.luis.end_security.core.designsystem.component.LabeledTextField
import com.luis.end_security.core.designsystem.component.PasswordField
import com.luis.end_security.core.designsystem.component.PrimaryButton
import com.luis.end_security.core.designsystem.component.SecondaryButton
import com.luis.end_security.core.designsystem.component.StrengthMeter
import com.luis.end_security.core.designsystem.theme.EndSecurityTheme
import com.luis.end_security.core.designsystem.theme.PreviewSurface
import com.luis.end_security.core.security.ExcludeFromAutofill
import com.luis.end_security.core.security.SecureScreen
import com.luis.end_security.feature.auth.ui.signup.PasswordStrengthLevel
import com.luis.end_security.feature.auth.ui.signup.StrengthColorRole
import com.luis.end_security.feature.vault.domain.ItemDraft

/** Los cinco campos de SCR-14. Viven aquí, en memoria, y nunca en `rememberSaveable` (regla no negociable 6). */
private class FormFields {
    val name = TextFieldState()
    val username = TextFieldState()
    val password = TextFieldState()
    val url = TextFieldState()
    val notes = TextFieldState()

    /** Lo que había al abrir, para saber si hay cambios sin guardar. Vive y muere con los campos. */
    var baseline: FormValues = FormValues.EMPTY

    fun hasChanges(): Boolean = baseline.differsFrom(toDraft())

    /** El elemento completo se arma solo al guardar o al preguntar si hay cambios. */
    fun toDraft() = ItemDraft(
        name = name.text.toString(),
        username = username.text.toString(),
        password = password.text.toString(),
        url = url.text.toString().ifBlank { null },
        notes = notes.text.toString().ifBlank { null },
    )

    fun apply(values: FormValues) {
        baseline = values
        name.setTextAndPlaceCursorAtEnd(values.name)
        username.setTextAndPlaceCursorAtEnd(values.username)
        password.setTextAndPlaceCursorAtEnd(values.password)
        url.setTextAndPlaceCursorAtEnd(values.url)
        notes.setTextAndPlaceCursorAtEnd(values.notes)
    }

    fun clear() {
        listOf(name, username, password, url, notes).forEach { it.clearText() }
        baseline = FormValues.EMPTY
    }
}

@Composable
fun ItemEditRoute(
    onClose: () -> Unit,
    onSaved: () -> Unit,
    onTabSelected: (BottomNavTab) -> Unit,
    viewModel: ItemEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val fields = remember { FormFields() }
    var passwordVisible by remember { mutableStateOf(false) }
    // «Guardar» espera a que los campos tengan los datos: si la actividad se recrea (al girar) los
    // campos nacen vacíos y guardar así borraría la contraseña guardada.
    var formReady by remember(fields) { mutableStateOf(state.isNew) }
    val currentOnClose by rememberUpdatedState(onClose)
    val currentOnSaved by rememberUpdatedState(onSaved)

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                ItemEditEffect.Saved -> currentOnSaved()
                ItemEditEffect.Close -> currentOnClose()
            }
        }
    }
    // Al editar, los campos arrancan con los datos del elemento; el VM suelta su copia al terminar.
    LaunchedEffect(fields) { viewModel.requestPrefill() }
    LaunchedEffect(state.prefill) {
        state.prefill?.let {
            fields.apply(it)
            formReady = true
            viewModel.onPrefillApplied()
        }
    }
    // El medidor sigue lo que se escribe en la contraseña; la contraseña no pasa por el estado.
    LaunchedEffect(fields) {
        snapshotFlow { fields.password.text.length }.collect {
            viewModel.onPasswordChanged(fields.password.text)
        }
    }
    LaunchedEffect(fields) {
        snapshotFlow { fields.name.text.toString() }.collect { viewModel.onNameChanged() }
    }
    // Contiene una contraseña: sin capturas, sin autocompletar, y se vacía al salir.
    SecureScreen()
    ExcludeFromAutofill()
    DisposableEffect(fields) { onDispose { fields.clear() } }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { passwordVisible = false }
    BackHandler { viewModel.onBack(fields.hasChanges()) }

    ItemEditScreen(
        state = state,
        fields = fields,
        passwordVisible = passwordVisible,
        onPasswordVisibleChange = { passwordVisible = it },
        formReady = formReady,
        onBack = { viewModel.onBack(fields.hasChanges()) },
        onSave = { viewModel.onSave(fields.toDraft()) },
        onTabSelected = onTabSelected,
        onDiscardConfirm = viewModel::onDiscardConfirm,
        onDiscardDismiss = viewModel::onDiscardDismiss,
    )
}

@Composable
private fun ItemEditScreen(
    state: ItemEditUiState,
    fields: FormFields,
    passwordVisible: Boolean,
    onPasswordVisibleChange: (Boolean) -> Unit,
    formReady: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onTabSelected: (BottomNavTab) -> Unit,
    onDiscardConfirm: () -> Unit,
    onDiscardDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    // Con el teclado abierto, la barra de abajo estorba: se oculta.
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding(),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .statusBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BackButton(onClick = onBack)
                Text(
                    text = stringResource(if (state.isNew) R.string.item_form_new_title else R.string.item_form_edit_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .padding(start = 14.dp)
                        .semantics { heading() },
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Spacer(Modifier.height(4.dp))
                LabeledTextField(
                    state = fields.name,
                    label = stringResource(R.string.item_form_name),
                    leadingIcon = R.drawable.ic_file_text_20,
                    supportingText = if (state.nameError) stringResource(R.string.item_form_name_required) else null,
                    isError = state.nameError,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Next,
                    ),
                )
                LabeledTextField(
                    state = fields.username,
                    label = stringResource(R.string.item_form_username),
                    leadingIcon = R.drawable.ic_user_20,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next,
                    ),
                )
                PasswordFieldWithMeter(
                    fields = fields,
                    visible = passwordVisible,
                    onVisibleChange = onPasswordVisibleChange,
                    strengthScore = state.strengthScore,
                )
                LabeledTextField(
                    state = fields.url,
                    label = stringResource(R.string.item_form_site),
                    leadingIcon = R.drawable.ic_globe_20,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next,
                    ),
                )
                LabeledTextField(
                    state = fields.notes,
                    label = stringResource(R.string.item_form_notes),
                    leadingIcon = R.drawable.ic_file_text_20,
                    placeholder = stringResource(R.string.item_form_optional),
                    // Las notas pueden llevar secretos: sin corrector que las aprenda.
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
                    onKeyboardAction = { focusManager.clearFocus() },
                )
                Spacer(Modifier.height(8.dp))
            }
            Column(
                modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 8.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (state.error) {
                    SaveError.Generic -> InlineErrorMessage(text = stringResource(R.string.item_form_error_generic))
                    SaveError.Conflict -> InlineErrorMessage(text = stringResource(R.string.item_form_error_conflict))
                    null -> Unit
                }
                PrimaryButton(
                    text = stringResource(R.string.item_form_save),
                    onClick = {
                        focusManager.clearFocus()
                        onSave()
                    },
                    showArrow = false,
                    enabled = formReady,
                    loading = state.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (!imeVisible) BottomNavBar(selected = BottomNavTab.Vault, onTabSelected = onTabSelected)
    }
    if (state.showDiscardSheet) {
        DiscardSheet(onConfirm = onDiscardConfirm, onDismiss = onDiscardDismiss)
    }
}

/** Contraseña con el ojo, el botón «Generar» (aún deshabilitado) y el medidor de fuerza debajo. */
@Composable
private fun PasswordFieldWithMeter(
    fields: FormFields,
    visible: Boolean,
    onVisibleChange: (Boolean) -> Unit,
    strengthScore: Int?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PasswordField(
            state = fields.password,
            label = stringResource(R.string.item_form_password),
            visible = visible,
            onVisibleChange = onVisibleChange,
            imeAction = ImeAction.Next,
            extraAction = {
                // TODO(tarea del Generador): genera una contraseña y la pone en el campo.
                FieldActionButton(
                    icon = R.drawable.ic_dice_22,
                    contentDescription = stringResource(R.string.item_form_generate),
                    onClick = {},
                    enabled = false,
                )
            },
        )
        val level = strengthScore?.let { PasswordStrengthLevel.fromScore(it) }
        val color = when (level?.colorRole) {
            StrengthColorRole.Error -> MaterialTheme.colorScheme.error
            StrengthColorRole.OnSurfaceVariant -> MaterialTheme.colorScheme.onSurfaceVariant
            StrengthColorRole.Success -> EndSecurityTheme.extendedColors.success
            null -> MaterialTheme.colorScheme.outline
        }
        StrengthMeter(
            filledBars = level?.filledBars ?: 0,
            color = color,
            label = level?.let { stringResource(it.label) },
        )
    }
}

/** «¿Descartar cambios?» (no está en Figma; mismos botones que «Eliminar»/«Cancelar», DEC-56). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiscardSheet(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        properties = ModalBottomSheetProperties(securePolicy = SecureFlagPolicy.SecureOn),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        SecureScreen()
        DiscardContent(onConfirm = onConfirm, onDismiss = onDismiss, modifier = Modifier.navigationBarsPadding())
    }
}

@Composable
private fun DiscardContent(onConfirm: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(start = 32.dp, end = 32.dp, top = 8.dp, bottom = 16.dp)) {
        Text(
            text = stringResource(R.string.item_discard_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.item_discard_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        DangerButton(
            text = stringResource(R.string.item_discard_confirm),
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        SecondaryButton(
            text = stringResource(R.string.item_discard_keep),
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview(name = "Nueva contraseña", device = "spec:width=412dp,height=917dp")
@Composable
private fun ItemEditPreview() {
    val fields = remember {
        FormFields().apply {
            name.setTextAndPlaceCursorAtEnd("Netflix")
            username.setTextAndPlaceCursorAtEnd("luis.ruiz@gmail.com")
            password.setTextAndPlaceCursorAtEnd("Tortuga-Violeta-29")
            url.setTextAndPlaceCursorAtEnd("netflix.com")
        }
    }
    PreviewSurface {
        ItemEditScreen(
            state = ItemEditUiState(isNew = true, status = EditStatus.Ready, strengthScore = 4),
            fields = fields,
            passwordVisible = false,
            onPasswordVisibleChange = {},
            formReady = true,
            onBack = {},
            onSave = {},
            onTabSelected = {},
            onDiscardConfirm = {},
            onDiscardDismiss = {},
        )
    }
}

@Preview(name = "Descartar cambios", widthDp = 412)
@Composable
private fun DiscardPreview() {
    PreviewSurface {
        DiscardContent(onConfirm = {}, onDismiss = {})
    }
}
