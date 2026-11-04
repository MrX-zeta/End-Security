package com.luis.end_security.feature.account.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.SecureFlagPolicy
import com.luis.end_security.R
import com.luis.end_security.core.designsystem.component.DangerButton
import com.luis.end_security.core.designsystem.component.LabeledTextField
import com.luis.end_security.core.designsystem.component.PasswordField
import com.luis.end_security.core.designsystem.component.PrimaryButton
import com.luis.end_security.core.designsystem.component.SecondaryButton
import com.luis.end_security.core.designsystem.component.SheetIconDisc
import com.luis.end_security.core.security.ExcludeFromAutofill
import com.luis.end_security.feature.auth.domain.MasterPasswordPolicy

/** Todo lo que `ReauthSheet` muestra además de sus textos (Figma 218:4421). */
data class ReauthSheetState(
    val code: String = "",
    /** Mensaje bajo «Contraseña maestra» (falta). */
    val showPasswordRequired: Boolean = false,
    val showCodeIncomplete: Boolean = false,
    /** `reauth_required`: se marcan los dos campos con «Contraseña o código incorrectos.» */
    val credentialsWrong: Boolean = false,
    /** Otro mensaje bajo el código (error genérico o bloqueo por intentos). */
    val otherError: String? = null,
    val isLoading: Boolean = false,
    val focus: ReauthFocus? = null,
)

/**
 * Hoja «Confirma que eres tú» (reautenticación, Figma 218:4421): contraseña maestra y código de 6 dígitos de la app de
 * autenticación. Es reutilizable: la usan Desactivar y Códigos nuevos (T17) y la usarán Contraseña maestra (T18) y
 * Eliminar cuenta (T19). La contraseña vive en un `TextFieldState` de esta hoja, nunca en el ViewModel (regla 6): el
 * ViewModel solo recibe «está vacía» y, al confirmar, el texto de [passwordState] en [onConfirm].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReauthSheet(
    body: String,
    confirmLabel: String,
    danger: Boolean,
    state: ReauthSheetState,
    onCodeChange: (String) -> Unit,
    onPasswordEmptinessChanged: (Boolean) -> Unit,
    onConfirm: (CharSequence) -> Unit,
    onDismiss: () -> Unit,
    /** `false` si la cuenta no tiene 2FA (Cambiar correo): no hay código de la app que pedir. */
    showCode: Boolean = true,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        // Pide la contraseña maestra: sin capturas ni vista previa.
        properties = ModalBottomSheetProperties(securePolicy = SecureFlagPolicy.SecureOn),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        ExcludeFromAutofill()
        // Sin rememberSaveable ni rememberTextFieldState: escribirían la contraseña en el Bundle (regla 6).
        val passwordState = remember { TextFieldState() }
        LaunchedEffect(passwordState) {
            snapshotFlow { passwordState.text.isEmpty() }.collect { onPasswordEmptinessChanged(it) }
        }
        ReauthSheetContent(
            body = body,
            confirmLabel = confirmLabel,
            danger = danger,
            state = state,
            passwordState = passwordState,
            onCodeChange = onCodeChange,
            showCode = showCode,
            onConfirm = { onConfirm(passwordState.text) },
            onCancel = onDismiss,
            modifier = Modifier.navigationBarsPadding().imePadding(),
        )
    }
}

@Composable
fun ReauthSheetContent(
    body: String,
    confirmLabel: String,
    danger: Boolean,
    state: ReauthSheetState,
    passwordState: TextFieldState,
    onCodeChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    showCode: Boolean = true,
) {
    var passwordVisible by remember { mutableStateOf(false) }
    val passwordFocus = remember { FocusRequester() }
    val codeFocus = remember { FocusRequester() }
    LaunchedEffect(state.focus) {
        when (state.focus?.field) {
            ReauthField.Password -> passwordFocus.requestFocus()
            ReauthField.Code -> codeFocus.requestFocus()
            null -> Unit
        }
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 32.dp, end = 32.dp, top = 8.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SheetIconDisc(R.drawable.ic_shield_check_22, danger = true)
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.tf_reauth_title),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PasswordField(
                state = passwordState,
                label = stringResource(R.string.tf_reauth_password_label),
                visible = passwordVisible,
                onVisibleChange = { passwordVisible = it },
                errorText = when {
                    state.showPasswordRequired -> stringResource(R.string.auth_error_password_required)
                    // Sin campo de código, los mensajes que irían bajo él van bajo la contraseña.
                    !showCode && state.credentialsWrong -> stringResource(R.string.tf_reauth_wrong)
                    !showCode -> state.otherError
                    else -> null
                },
                isError = state.credentialsWrong,
                modifier = Modifier.focusRequester(passwordFocus),
                imeAction = ImeAction.Next,
                maxLength = MasterPasswordPolicy.MAX_FIELD_LENGTH,
            )
            if (showCode) LabeledTextField(
                value = state.code,
                onValueChange = onCodeChange,
                label = stringResource(R.string.tf_reauth_code_label),
                leadingIcon = R.drawable.ic_shield_check_22,
                placeholder = stringResource(R.string.tf_reauth_code_placeholder),
                // El mensaje de credenciales va una sola vez, bajo el código; los dos campos quedan marcados.
                errorText = when {
                    state.credentialsWrong -> stringResource(R.string.tf_reauth_wrong)
                    state.showCodeIncomplete -> stringResource(R.string.code_error_incomplete)
                    else -> state.otherError
                },
                modifier = Modifier.focusRequester(codeFocus),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onConfirm() }),
            )
        }
        Spacer(Modifier.height(20.dp))
        if (danger) {
            DangerButton(text = confirmLabel, onClick = onConfirm, loading = state.isLoading, modifier = Modifier.fillMaxWidth())
        } else {
            PrimaryButton(text = confirmLabel, onClick = onConfirm, loading = state.isLoading, showArrow = false, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(12.dp))
        SecondaryButton(text = stringResource(R.string.tf_reauth_cancel), onClick = onCancel, modifier = Modifier.fillMaxWidth())
    }
}
