package com.luis.end_security.feature.settings.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import com.luis.end_security.core.designsystem.component.FadeOnChange
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luis.end_security.R
import com.luis.end_security.core.designsystem.component.HostedMessage
import com.luis.end_security.core.designsystem.component.FieldErrorText
import com.luis.end_security.core.designsystem.component.LetterTile
import com.luis.end_security.core.designsystem.component.ListCard
import com.luis.end_security.core.designsystem.component.ListRowDivider
import com.luis.end_security.core.designsystem.component.ListRowIcon
import com.luis.end_security.core.designsystem.component.SheetIconDisc
import com.luis.end_security.core.designsystem.component.SignOutBottomSheet
import com.luis.end_security.core.designsystem.component.SignOutSheet
import com.luis.end_security.core.designsystem.component.initialOf
import com.luis.end_security.core.session.AutoLockSettings
import com.luis.end_security.core.security.BiometricPromptFailure
import com.luis.end_security.core.security.BiometricPromptTexts
import com.luis.end_security.core.security.authenticateWithCipher
import com.luis.end_security.core.security.findFragmentActivity
import com.luis.end_security.feature.settings.ui.legal.LegalDocument
import kotlinx.coroutines.delay

/** SCR-29 · Ajustes (pestaña). No muestra secretos: no lleva `SecureScreen`. */
@Composable
fun SettingsRoute(
    onNavigateToLogin: () -> Unit,
    onOpenTwoFactorSetup: () -> Unit,
    onOpenTwoFactorActive: () -> Unit,
    onOpenChangePassword: () -> Unit = {},
    onOpenDevices: () -> Unit = {},
    onOpenActivity: () -> Unit = {},
    onOpenDeleteAccount: () -> Unit = {},
    onOpenChangeEmail: () -> Unit = {},
    onOpenLegalDocument: (LegalDocument) -> Unit = {},
    onOpenLicenses: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    /** El correo nuevo al volver de cambiarlo (vacío = nada). */
    emailChanged: String = "",
    onEmailChangedShown: () -> Unit = {},
    twoFactorDisabled: Boolean = false,
    onTwoFactorDisabledShown: () -> Unit = {},
    /** 0 nada; [com.luis.end_security.navigation.Routes.PASSWORD_CHANGED] o `…_BIOMETRIC_OFF` al volver de cambiar la contraseña. */
    passwordChanged: Int = 0,
    onPasswordChangedShown: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val currentOpenSetup by rememberUpdatedState(onOpenTwoFactorSetup)
    val currentOpenActive by rememberUpdatedState(onOpenTwoFactorActive)
    val currentOpenDelete by rememberUpdatedState(onOpenDeleteAccount)
    val currentOpenEmail by rememberUpdatedState(onOpenChangeEmail)
    LaunchedEffect(emailChanged) {
        if (emailChanged.isNotEmpty()) {
            viewModel.onAction(SettingsAction.EmailChanged(emailChanged))
            onEmailChangedShown()
        }
    }
    // Al volver de «Desactivar» con éxito (resultado de la pantalla anterior): aviso y recarga.
    LaunchedEffect(passwordChanged) {
        if (passwordChanged != 0) {
            // 1 = cambiada; 2 = cambiada y la huella quedó desactivada.
            viewModel.onAction(SettingsAction.PasswordChanged(biometricOff = passwordChanged == 2))
            onPasswordChangedShown()
        }
    }
    LaunchedEffect(twoFactorDisabled) {
        if (twoFactorDisabled) {
            viewModel.onAction(SettingsAction.TwoFactorDisabled)
            onTwoFactorDisabledShown()
        }
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val currentOnNavigateToLogin by rememberUpdatedState(onNavigateToLogin)
    val promptTexts = BiometricPromptTexts(
        title = stringResource(R.string.biometric_enable_title),
        subtitle = stringResource(R.string.biometric_enable_subtitle),
        negativeButton = stringResource(R.string.biometric_cancel),
    )
    val currentTexts by rememberUpdatedState(promptTexts)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is SettingsEffect.ShowBiometricPrompt -> {
                    val activity = context.findFragmentActivity()
                    if (activity == null) {
                        viewModel.onAction(SettingsAction.BiometricCancelled)
                    } else {
                        activity.authenticateWithCipher(
                            cipher = effect.cipher,
                            texts = currentTexts,
                            onSuccess = { cipher -> viewModel.onAction(SettingsAction.BiometricAuthenticated(cipher)) },
                            onFailure = { _: BiometricPromptFailure -> viewModel.onAction(SettingsAction.BiometricCancelled) },
                        )
                    }
                }
                SettingsEffect.NavigateToLogin -> currentOnNavigateToLogin()
                SettingsEffect.OpenTwoFactorSetup -> currentOpenSetup()
                SettingsEffect.OpenTwoFactorActive -> currentOpenActive()
                SettingsEffect.OpenDeleteAccount -> currentOpenDelete()
                SettingsEffect.OpenChangeEmail -> currentOpenEmail()
            }
        }
    }
    // Tras registrar una huella en los ajustes del teléfono, el aviso se quita solo.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onAction(SettingsAction.Resumed) }
    SettingsScreen(
        state = state,
        onAction = viewModel::onAction,
        onOpenChangePassword = onOpenChangePassword,
        onOpenDevices = onOpenDevices,
        onOpenActivity = onOpenActivity,
        onOpenLegalDocument = onOpenLegalDocument,
        onOpenLicenses = onOpenLicenses,
        onOpenAbout = onOpenAbout,
    )
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    modifier: Modifier = Modifier,
    onOpenChangePassword: () -> Unit = {},
    onOpenDevices: () -> Unit = {},
    onOpenActivity: () -> Unit = {},
    onOpenLegalDocument: (LegalDocument) -> Unit = {},
    onOpenLicenses: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
) {
    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(modifier = Modifier.weight(1f)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 32.dp)
                    .padding(top = 32.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // «Ajustes y seguridad» en una línea: «Ajustes» en negrita y el resto normal (Figma 47:1303).
                val titleBold = stringResource(R.string.settings_title_bold)
                val titleRegular = stringResource(R.string.settings_title_regular)
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(titleBold) }
                        append(' ')
                        append(titleRegular)
                    },
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Normal, lineHeight = 35.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(4.dp))
                val profileLabel = stringResource(R.string.settings_profile_change_email)
                state.profile?.let { profile ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
                            .clickable(onClickLabel = profileLabel, role = Role.Button) { onAction(SettingsAction.ProfileClicked) }
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LetterTile(letter = initialOf(profile.title), size = 48.dp, cornerRadius = 14.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = profile.title,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            profile.subtitle?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Icon(
                            painter = painterResource(R.drawable.ic_chevron_right_20),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                SectionLabel(stringResource(R.string.settings_section_security))
                ListCard {
                    SettingsRow(
                        icon = R.drawable.ic_shield_check_22,
                        label = stringResource(R.string.settings_two_factor),
                        value = state.twoFactorEnabled?.let {
                            stringResource(if (it) R.string.settings_two_factor_active else R.string.settings_two_factor_inactive)
                        },
                        onClick = { onAction(SettingsAction.TwoFactorClicked) },
                    )
                    if (state.biometricRowVisible) {
                        ListRowDivider()
                        BiometricRow(state = state, onToggle = { onAction(SettingsAction.BiometricToggled(it)) })
                        if (state.biometricNeedsEnrollment) {
                            FieldErrorText(
                                text = stringResource(R.string.settings_biometric_needs_enrollment),
                                modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 8.dp),
                            )
                        }
                    }
                    ListRowDivider()
                    SettingsRow(
                        icon = R.drawable.ic_clock_20,
                        label = stringResource(R.string.settings_autolock),
                        value = autoLockValue(state.autolockMinutes),
                        onClick = { onAction(SettingsAction.AutoLockClicked) },
                    )
                    ListRowDivider()
                    SettingsRow(
                        icon = R.drawable.ic_key_20,
                        label = stringResource(R.string.settings_master_password),
                        value = null,
                        onClick = onOpenChangePassword,
                    )
                }
                SectionLabel(stringResource(R.string.settings_section_account))
                ListCard {
                    SettingsRow(
                        icon = R.drawable.ic_smartphone_20,
                        label = stringResource(R.string.settings_devices),
                        value = state.deviceCount?.toString(),
                        onClick = onOpenDevices,
                    )
                    ListRowDivider()
                    SettingsRow(
                        icon = R.drawable.ic_history_20,
                        label = stringResource(R.string.settings_activity),
                        value = null,
                        onClick = onOpenActivity,
                    )
                    ListRowDivider()
                    MarketingRow(state = state, onToggle = { onAction(SettingsAction.MarketingToggled(it)) })
                    ListRowDivider()
                    SettingsRow(
                        icon = R.drawable.ic_log_out_20,
                        label = stringResource(R.string.settings_sign_out),
                        value = null,
                        onClick = { onAction(SettingsAction.SignOutClicked) },
                    )
                    ListRowDivider()
                    SettingsRow(
                        icon = R.drawable.ic_trash_20,
                        label = stringResource(R.string.settings_delete_account),
                        value = null,
                        danger = true,
                        onClick = { onAction(SettingsAction.DeleteAccountClicked) },
                    )
                }
                SectionLabel(stringResource(R.string.settings_section_legal))
                ListCard {
                    SettingsRow(
                        icon = R.drawable.ic_file_text_20,
                        label = stringResource(R.string.settings_legal_terms),
                        value = null,
                        onClick = { onOpenLegalDocument(LegalDocument.Terms) },
                    )
                    ListRowDivider()
                    SettingsRow(
                        icon = R.drawable.ic_shield_check_22,
                        label = stringResource(R.string.settings_legal_privacy),
                        value = null,
                        onClick = { onOpenLegalDocument(LegalDocument.Privacy) },
                    )
                    ListRowDivider()
                    SettingsRow(
                        icon = R.drawable.ic_globe_20,
                        label = stringResource(R.string.settings_legal_licenses),
                        value = null,
                        onClick = onOpenLicenses,
                    )
                    ListRowDivider()
                    SettingsRow(
                        icon = R.drawable.ic_info_20,
                        label = stringResource(R.string.settings_legal_about),
                        value = null,
                        onClick = onOpenAbout,
                    )
                }
            }
            NoticeBar(notice = state.notice, modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 32.dp, vertical = 8.dp))
        }
    }
    if (state.showAutoLockSheet) {
        AutoLockSheet(
            selected = state.autolockMinutes,
            onSelect = { onAction(SettingsAction.AutoLockSelected(it)) },
            onDismiss = { onAction(SettingsAction.AutoLockDismissed) },
        )
    }
    state.signOutSheet?.let { sheet ->
        SignOutBottomSheet(
            sheet = sheet,
            loading = state.isSigningOut,
            onConfirm = { onAction(SettingsAction.SignOutConfirmed) },
            onDismiss = { onAction(SettingsAction.SignOutDismissed) },
        )
    }
}

/** «Disponible pronto» un momento tras tocar una fila de una tarea futura. */
@Composable
private fun NoticeBar(notice: SettingsNotice?, modifier: Modifier = Modifier) {
    var shown by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<SettingsNotice?>(null) }
    LaunchedEffect(notice) {
        if (notice != null) {
            shown = notice
            delay(4_000)
            if (shown == notice) shown = null
        }
    }
    shown?.let {
        HostedMessage(
            text = (if (it.kind == NoticeKind.EmailChanged) stringResource(R.string.settings_email_changed, it.argument.orEmpty()) else null) ?: stringResource(
                when (it.kind) {
                    NoticeKind.NeedsConnection -> R.string.settings_two_factor_needs_connection
                    NoticeKind.NeedsConnectionToDelete -> R.string.settings_delete_needs_connection
                    NoticeKind.NeedsConnectionToChangeEmail -> R.string.settings_email_needs_connection
                    NoticeKind.TwoFactorDisabled -> R.string.settings_two_factor_disabled
                    NoticeKind.PasswordChanged -> R.string.cp_changed
                    NoticeKind.BiometricOff -> R.string.cp_biometric_off
                    NoticeKind.EmailChanged -> R.string.settings_email_changed
                    NoticeKind.MarketingFailed -> R.string.auth_error_generic
                },
            ),
            icon = R.drawable.ic_info_20,
            modifier = modifier,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, start = 4.dp).semantics { heading() },
    )
}

/** Una fila de Figma 47:1303: ícono, etiqueta, valor opcional y flecha; mide al menos 56 dp al tacto. */
@Composable
private fun SettingsRow(
    @DrawableRes icon: Int,
    label: String,
    value: String?,
    onClick: () -> Unit,
    danger: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ListRowIcon(icon, danger)
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (value != null) {
            // El valor puede cambiar mientras se ve (p. ej. el número de dispositivos al cerrar una sesión): fundido de 150 ms.
            FadeOnChange(value) { current ->
                Text(text = current, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right_20),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(end = 4.dp),
        )
    }
}

/** «Desbloquear con huella» con interruptor; toda la fila se activa (TalkBack lo anuncia como interruptor). */
@Composable
private fun BiometricRow(state: SettingsUiState, onToggle: (Boolean) -> Unit) {
    val label = stringResource(R.string.settings_biometric)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(
                value = state.biometricEnabled,
                enabled = !state.biometricBusy,
                role = Role.Switch,
                onValueChange = onToggle,
            )
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ListRowIcon(R.drawable.ic_fingerprint_22, danger = false)
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        // El toque lo recibe la fila entera; el interruptor solo se dibuja.
        Switch(
            checked = state.biometricEnabled,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onSurface,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedBorderColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurface,
                uncheckedTrackColor = MaterialTheme.colorScheme.background,
                uncheckedBorderColor = MaterialTheme.colorScheme.outline,
            ),
        )
    }
}

/**
 * «Novedades y encuestas» (DEC-64): interruptor del servidor. Sin valor (sin red) se ve apagado y deshabilitado; mientras se guarda,
 * no se puede volver a tocar. Toda la fila se activa y TalkBack la anuncia como interruptor.
 */
@Composable
private fun MarketingRow(state: SettingsUiState, onToggle: (Boolean) -> Unit) {
    val checked = state.marketingOptIn == true
    val enabled = state.marketingOptIn != null && !state.marketingBusy
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onToggle)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ListRowIcon(R.drawable.ic_mail_20, danger = false)
        Text(
            text = stringResource(R.string.settings_marketing),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onSurface,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedBorderColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurface,
                uncheckedTrackColor = MaterialTheme.colorScheme.background,
                uncheckedBorderColor = MaterialTheme.colorScheme.outline,
            ),
        )
    }
}

@Composable
private fun autoLockValue(minutes: Int): String = stringResource(
    when (minutes) {
        0 -> R.string.settings_autolock_value_0
        1 -> R.string.settings_autolock_value_1
        15 -> R.string.settings_autolock_value_15
        30 -> R.string.settings_autolock_value_30
        else -> R.string.settings_autolock_value_5
    },
)

// ---------------------------------------------------------------- Hojas

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AutoLockSheet(selected: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        properties = ModalBottomSheetProperties(securePolicy = SecureFlagPolicy.SecureOn),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        AutoLockSheetContent(selected = selected, onSelect = onSelect, modifier = Modifier.navigationBarsPadding())
    }
}

/** Hoja «Bloqueo automático» (Figma 215:4229). Cada opción es un radio para TalkBack. */
@Composable
fun AutoLockSheetContent(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = 8.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SheetIconDisc(R.drawable.ic_clock_20, danger = false)
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.settings_autolock_title),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.settings_autolock_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background, RoundedCornerShape(20.dp))
                .selectableGroup(),
        ) {
            AutoLockSettings.ALLOWED_MINUTES.sorted().forEach { minutes ->
                AutoLockOption(minutes = minutes, selected = minutes == selected, onClick = { onSelect(minutes) })
            }
        }
    }
}

@Composable
private fun AutoLockOption(minutes: Int, selected: Boolean, onClick: () -> Unit) {
    val label = stringResource(
        when (minutes) {
            0 -> R.string.settings_autolock_option_0
            1 -> R.string.settings_autolock_option_1
            15 -> R.string.settings_autolock_option_15
            30 -> R.string.settings_autolock_option_30
            else -> R.string.settings_autolock_option_5
        },
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Box(
                modifier = Modifier.size(28.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_check_16),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}
