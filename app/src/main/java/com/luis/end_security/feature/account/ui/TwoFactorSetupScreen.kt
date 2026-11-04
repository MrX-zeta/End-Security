package com.luis.end_security.feature.account.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luis.end_security.R
import com.luis.end_security.core.designsystem.component.BackButton
import com.luis.end_security.core.designsystem.component.BottomNavBar
import com.luis.end_security.core.designsystem.component.BottomNavTab
import com.luis.end_security.core.designsystem.component.CodeField
import com.luis.end_security.core.designsystem.component.MessageBar
import com.luis.end_security.core.designsystem.component.PrimaryButton
import com.luis.end_security.core.designsystem.component.SecondaryButton
import com.luis.end_security.core.designsystem.component.SkeletonBlock
import com.luis.end_security.core.designsystem.component.SkeletonPulse
import com.luis.end_security.core.designsystem.component.TwoWeightTitle
import com.luis.end_security.core.security.ExcludeFromAutofill
import com.luis.end_security.core.security.SecureScreen
import com.luis.end_security.feature.account.domain.OtpAuthUri
import com.luis.end_security.feature.auth.ui.lockoutMessage

private val ContentPadding = 32.dp

/**
 * Activar la verificación en dos pasos (SCR-43, SCR-30, SCR-31, SCR-32; OP-22, OP-23). Un solo destino con los tres
 * pasos: al salir de él (atrás, «Ya los guardé» o bloqueo de la bóveda) el ViewModel se vacía y con él la clave y los
 * códigos. Muestra secretos: sin capturas, sin autocompletar y sin vista en Recientes.
 */
@Composable
fun TwoFactorSetupRoute(
    onExit: () -> Unit,
    onTabSelected: (BottomNavTab) -> Unit,
    viewModel: TwoFactorSetupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SecureScreen()
    ExcludeFromAutofill()
    val context = LocalContext.current
    val currentOnExit by rememberUpdatedState(onExit)
    // Atrás: Paso 2 → Paso 1; Paso 3 → sale (nunca regresa al Paso 2).
    BackHandler { viewModel.onAction(SetupAction.Back) }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                SetupEffect.Exit -> currentOnExit()
                is SetupEffect.OpenUri -> try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(effect.uri)))
                } catch (_: ActivityNotFoundException) {
                    // Ya no hay app que lo abra: el botón se oculta.
                    viewModel.onAction(SetupAction.AuthenticatorAvailable(false))
                }
            }
        }
    }
    // El botón «Abrir app de autenticación» solo sale si hay una app que abra otpauth:// (con `<queries>`).
    LaunchedEffect(Unit) {
        val probe = Intent(Intent.ACTION_VIEW, Uri.parse(OtpAuthUri.PROBE))
        val available = context.packageManager.queryIntentActivities(probe, 0).isNotEmpty()
        viewModel.onAction(SetupAction.AuthenticatorAvailable(available))
    }
    TwoFactorSetupScreen(state = state, onAction = viewModel::onAction, onTabSelected = onTabSelected)
}

@Composable
fun TwoFactorSetupScreen(
    state: TwoFactorSetupUiState,
    onAction: (SetupAction) -> Unit,
    onTabSelected: (BottomNavTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val step = state.step
    val codeFocus = remember { FocusRequester() }
    LaunchedEffect(state.focusRequest) {
        if (state.focusRequest > 0) codeFocus.requestFocus()
    }
    FlowScaffold(
        onBack = { onAction(SetupAction.Back) },
        onTabSelected = onTabSelected,
        modifier = modifier,
        toast = state.copiedSeconds?.let { stringResource(R.string.tf_copied, it) },
        bottom = {
            when (step) {
                SetupStep.Key -> if (state.keyError) {
                    PrimaryButton(
                        text = stringResource(R.string.tf_key_retry),
                        onClick = { onAction(SetupAction.Retry) },
                        showArrow = false,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    if (state.isLoadingKey) GeneratingLine()
                    if (state.canOpenAuthenticator && state.keyDisplay != null) {
                        SecondaryButton(
                            text = stringResource(R.string.tf_open_authenticator),
                            onClick = { onAction(SetupAction.OpenAuthenticator) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    PrimaryButton(
                        text = stringResource(R.string.tf_continue),
                        onClick = { onAction(SetupAction.Continue) },
                        enabled = state.keyDisplay != null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                SetupStep.Code -> PrimaryButton(
                    text = stringResource(R.string.tf_confirm),
                    onClick = { onAction(SetupAction.Confirm) },
                    // Siempre activo (tarea 15): lo que falta se explica al tocarlo.
                    enabled = !state.isLockedOut,
                    loading = state.isConfirming,
                    modifier = Modifier.fillMaxWidth(),
                )
                SetupStep.Codes -> {
                    SecondaryButton(
                        text = stringResource(R.string.tf_copy_codes),
                        onClick = { onAction(SetupAction.CopyCodes) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PrimaryButton(
                        text = stringResource(R.string.tf_codes_saved),
                        onClick = { onAction(SetupAction.Finish) },
                        showArrow = false,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
    ) {
        StepIndicator(step = step.ordinal + 1)
        Spacer(Modifier.height(16.dp))
        when (step) {
            SetupStep.Key -> KeyStep(state = state, onCopy = { onAction(SetupAction.CopyKey) })
            SetupStep.Code -> CodeStep(state = state, onAction = onAction, codeFocus = codeFocus)
            SetupStep.Codes -> {
                TwoWeightTitle(
                    boldLine = stringResource(R.string.tf_step3_title_bold),
                    regularLine = stringResource(R.string.tf_step3_title_regular),
                    style = TitleStyle(),
                )
                Spacer(Modifier.height(16.dp))
                RecoveryCodesBody(codes = state.codes, notice = stringResource(R.string.tf_codes_notice))
            }
        }
    }
}

@Composable
internal fun TitleStyle() = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Normal, lineHeight = 30.sp)

/**
 * Cuerpo común de las pantallas de la verificación en dos pasos (Figma 47:1509…): barra superior con atrás y título,
 * contenido que se desplaza, acciones fijas abajo y la barra inferior con Ajustes activa.
 */
@Composable
fun FlowScaffold(
    onBack: () -> Unit,
    onTabSelected: (BottomNavTab) -> Unit,
    modifier: Modifier = Modifier,
    toast: String? = null,
    bottom: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding()) {
        Box(modifier = Modifier.weight(1f)) {
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BackButton(onClick = onBack)
                    Text(
                        text = stringResource(R.string.tf_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 14.dp).semantics { heading() },
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = ContentPadding)
                        .padding(top = 8.dp),
                ) { content() }
                Column(
                    modifier = Modifier.padding(horizontal = ContentPadding).padding(top = 8.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) { bottom() }
            }
            if (toast != null) {
                MessageBar(
                    text = toast,
                    icon = R.drawable.ic_copy_20,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = ContentPadding, vertical = 120.dp),
                )
            }
        }
        BottomNavBar(selected = BottomNavTab.Settings, onTabSelected = onTabSelected)
    }
}

/** «Paso N de 3» y tres tramos (Figma 47:1509). */
@Composable
private fun StepIndicator(step: Int) {
    val label = stringResource(R.string.tf_step_label, step)
    Column(modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { }) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(3) { index ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .background(
                            if (index < step) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            CircleShape,
                        ),
                )
            }
        }
    }
}

@Composable
private fun KeyStep(state: TwoFactorSetupUiState, onCopy: () -> Unit) {
    TwoWeightTitle(
        boldLine = stringResource(R.string.tf_step1_title_bold),
        regularLine = stringResource(R.string.tf_step1_title_regular),
        style = TitleStyle(),
    )
    Spacer(Modifier.height(16.dp))
    KeyCard(keyDisplay = state.keyDisplay, loading = state.isLoadingKey, onCopy = onCopy)
    Spacer(Modifier.height(16.dp))
    if (state.keyError) {
        Text(
            text = stringResource(R.string.tf_key_error),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        return
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        InstructionRow(1, stringResource(R.string.tf_instruction_1))
        InstructionRow(2, stringResource(R.string.tf_instruction_2))
        InstructionRow(3, stringResource(R.string.tf_instruction_3))
    }
    Spacer(Modifier.height(16.dp))
    Text(
        text = stringResource(R.string.tf_key_warning),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** La tarjeta oscura «Tu clave» (Figma 47:1509) o su esqueleto mientras responde EP-19 (69:3626). */
@Composable
private fun KeyCard(keyDisplay: String?, loading: Boolean, onCopy: () -> Unit) {
    val dark = MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(dark, RoundedCornerShape(20.dp))
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (loading || keyDisplay == null) {
                SkeletonPulse {
                    SkeletonBlock(Modifier.size(width = 60.dp, height = 12.dp), RoundedCornerShape(6.dp))
                    Spacer(Modifier.height(8.dp))
                    SkeletonBlock(Modifier.fillMaxWidth(0.8f).height(22.dp), RoundedCornerShape(8.dp))
                    Spacer(Modifier.height(8.dp))
                    SkeletonBlock(Modifier.fillMaxWidth(0.6f).height(22.dp), RoundedCornerShape(8.dp))
                }
            } else {
                Text(
                    text = stringResource(R.string.tf_key_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.background,
                )
                Text(
                    text = keyDisplay,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 2.sp, lineHeight = 30.sp),
                    color = MaterialTheme.colorScheme.background,
                )
            }
        }
        Spacer(Modifier.size(12.dp))
        CopyIconButton(enabled = keyDisplay != null, onClick = onCopy, contentDescription = stringResource(R.string.tf_copy_key))
    }
}

@Composable
private fun CopyIconButton(enabled: Boolean, onClick: () -> Unit, contentDescription: String) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.4f), RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_copy_20),
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun InstructionRow(number: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier.size(24.dp).background(MaterialTheme.colorScheme.background, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = number.toString(), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
        }
        Text(text = text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** «● Generando tu clave…» sobre el botón mientras responde EP-19 (Figma 69:3626). */
@Composable
private fun GeneratingLine() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
        Text(
            text = stringResource(R.string.tf_key_generating),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun CodeStep(state: TwoFactorSetupUiState, onAction: (SetupAction) -> Unit, codeFocus: FocusRequester) {
    TwoWeightTitle(
        boldLine = stringResource(R.string.tf_step2_title_bold),
        regularLine = stringResource(R.string.tf_step2_title_regular),
        style = TitleStyle(),
    )
    Spacer(Modifier.height(20.dp))
    CodeField(
        value = state.code,
        onValueChange = { onAction(SetupAction.CodeChanged(it)) },
        errorText = when {
            state.isLockedOut -> lockoutMessage(state.lockoutSecondsLeft)
            state.wrongCode -> stringResource(R.string.two_factor_error_wrong_code)
            state.genericError -> stringResource(R.string.auth_error_generic)
            state.showIncomplete -> stringResource(R.string.code_error_incomplete)
            else -> null
        },
        modifier = Modifier.focusRequester(codeFocus),
        onDone = { onAction(SetupAction.Confirm) },
    )
    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(R.drawable.ic_clock_16),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = stringResource(R.string.tf_code_changes_in, state.secondsLeft),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Aviso oscuro y los códigos de respaldo en dos columnas (Figma 47:1714). Lo comparten el Paso 3 y los códigos nuevos
 * de «Generar códigos de respaldo nuevos».
 */
@Composable
fun RecoveryCodesBody(codes: List<String>, notice: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.onSurface, RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .semantics(mergeDescendants = true) {},
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_info_20),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Text(text = notice, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.background)
        }
        Spacer(Modifier.height(16.dp))
        val half = (codes.size + 1) / 2
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
                .padding(horizontal = 24.dp, vertical = 20.dp),
        ) {
            for (column in listOf(codes.take(half), codes.drop(half))) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    column.forEach { code ->
                        Text(
                            text = code,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                        )
                    }
                }
            }
        }
    }
}
