package com.luis.end_security.feature.vault.ui.generator

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luis.end_security.R
import com.luis.end_security.core.crypto.GeneratorOptions
import com.luis.end_security.core.designsystem.component.BottomNavBar
import com.luis.end_security.core.designsystem.component.BottomNavTab
import com.luis.end_security.core.designsystem.component.MessageBar
import com.luis.end_security.core.designsystem.component.PrimaryButton
import com.luis.end_security.core.designsystem.component.StrengthMeter
import com.luis.end_security.core.designsystem.component.TwoWeightTitle
import com.luis.end_security.core.designsystem.theme.PreviewSurface
import com.luis.end_security.core.designsystem.theme.rememberReducedMotion
import com.luis.end_security.core.security.SecureScreen
import com.luis.end_security.feature.auth.ui.signup.PasswordStrengthLevel
import kotlin.math.roundToInt

private val ResultShape = RoundedCornerShape(24.dp)
private val GroupShape = RoundedCornerShape(20.dp)
private val TileShape = RoundedCornerShape(12.dp)
private val IconButtonShape = RoundedCornerShape(14.dp)

@Composable
fun GeneratorRoute(
    onTabSelected: (BottomNavTab) -> Unit,
    onOpenNewWithPassword: () -> Unit,
    viewModel: GeneratorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnOpenNew by rememberUpdatedState(onOpenNewWithPassword)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                GeneratorEffect.OpenNewWithPassword -> currentOnOpenNew()
                is GeneratorEffect.Use -> Unit
            }
        }
    }
    // Muestra la contraseña generada: sin capturas ni vista en Recientes (regla no negociable 7).
    SecureScreen()
    // Al salir de primer plano se borra de la pantalla; al volver se genera otra.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onHide() }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.ensureGenerated() }

    GeneratorScreen(
        state = state,
        onTabSelected = onTabSelected,
        onRegenerate = viewModel::onRegenerate,
        onCopy = viewModel::onCopy,
        onLengthChange = viewModel::onLengthChange,
        onLengthChangeFinished = viewModel::onLengthChangeFinished,
        onToggle = viewModel::onToggle,
        onSave = viewModel::onSave,
    )
}

/** SCR-15 · Generador. */
@Composable
fun GeneratorScreen(
    state: GeneratorUiState,
    onTabSelected: (BottomNavTab) -> Unit,
    onRegenerate: () -> Unit,
    onCopy: () -> Unit,
    onLengthChange: (Int) -> Unit,
    onLengthChangeFinished: () -> Unit,
    onToggle: (GeneratorToggle) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp)
                .padding(top = 24.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            TwoWeightTitle(
                boldLine = stringResource(R.string.generator_title_bold),
                regularLine = stringResource(R.string.generator_title_regular),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Normal, lineHeight = 35.sp),
            )
            GeneratorContent(
                state = state,
                onRegenerate = onRegenerate,
                onCopy = onCopy,
                onLengthChange = onLengthChange,
                onLengthChangeFinished = onLengthChangeFinished,
                onToggle = onToggle,
            )
        }
        Column(
            modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PrimaryButton(
                text = stringResource(R.string.generator_save),
                onClick = onSave,
                showArrow = false,
                enabled = state.password.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        BottomNavBar(selected = BottomNavTab.Generator, onTabSelected = onTabSelected)
    }
}

/** Resultado, longitud e interruptores: lo mismo en la pantalla y en la hoja de Nueva/editar. */
@Composable
fun GeneratorContent(
    state: GeneratorUiState,
    onRegenerate: () -> Unit,
    onCopy: () -> Unit,
    onLengthChange: (Int) -> Unit,
    onLengthChangeFinished: () -> Unit,
    onToggle: (GeneratorToggle) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(22.dp)) {
        ResultCard(state = state, onRegenerate = onRegenerate, onCopy = onCopy)
        state.copiedSeconds?.let { seconds ->
            MessageBar(
                text = stringResource(R.string.item_copied_password, seconds),
                icon = R.drawable.ic_check_16,
            )
        }
        LengthSlider(
            length = state.options.length,
            onLengthChange = onLengthChange,
            onLengthChangeFinished = onLengthChangeFinished,
        )
        ToggleGroup(options = state.options, onToggle = onToggle)
    }
}

@Composable
private fun ResultCard(state: GeneratorUiState, onRegenerate: () -> Unit, onCopy: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val reducedMotion = rememberReducedMotion()
    // Un cambio breve de opacidad al generar otra (≤ 150 ms) y el dado gira una vez; con «reducir movimiento», sin animar.
    val alpha = remember { Animatable(1f) }
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(state.generation) {
        if (state.generation > 1 && !reducedMotion) {
            alpha.snapTo(0.35f)
            alpha.animateTo(1f, tween(durationMillis = 140, easing = LinearEasing))
        }
    }
    LaunchedEffect(state.generation) {
        if (state.generation > 1 && !reducedMotion) {
            rotation.snapTo(0f)
            rotation.animateTo(360f, tween(durationMillis = 300))
        }
    }
    val level = state.strengthScore?.let { PasswordStrengthLevel.fromScore(it) }
    val spokenPrefix = stringResource(R.string.generator_password_description, "")
    // Una sola vez por contraseña (no en cada cuadro de la animación): menos copias en memoria.
    val spoken = remember(state.password) { spokenPrefix + state.password.toList().joinToString(" ") }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.onSurface, ResultShape)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = state.password.ifEmpty { " " },
            style = MaterialTheme.typography.titleLarge.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                letterSpacing = 1.4.sp,
            ),
            color = colors.background,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { this.alpha = alpha.value }
                // Con el foco se lee carácter por carácter, no como una palabra.
                .semantics { if (state.password.isNotEmpty()) contentDescription = spoken },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            StrengthMeter(
                filledBars = level?.filledBars ?: 0,
                // Sobre la tarjeta oscura (Figma 46:1022) las barras van en amarillo y la etiqueta en claro.
                color = colors.primary,
                label = level?.let { stringResource(it.label) },
                labelColor = colors.background,
                barWidth = 28.dp,
                labelBelow = true,
                modifier = Modifier.weight(1f),
            )
            ResultIconButton(
                icon = R.drawable.ic_dice_22,
                contentDescription = stringResource(R.string.generator_regenerate),
                onClick = onRegenerate,
                rotation = rotation.value,
            )
            ResultIconButton(
                icon = R.drawable.ic_copy_20,
                contentDescription = stringResource(R.string.generator_copy),
                onClick = onCopy,
                enabled = state.password.isNotEmpty(),
            )
        }
    }
}

/** Botón de ícono amarillo de 48 × 48 (Figma 46:1032). */
@Composable
private fun ResultIconButton(
    icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    rotation: Float = 0f,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.alpha(if (enabled) 1f else 0.4f),
        shape = IconButtonShape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(icon),
                contentDescription = contentDescription,
                modifier = Modifier
                    .size(22.dp)
                    .rotate(rotation),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LengthSlider(length: Int, onLengthChange: (Int) -> Unit, onLengthChangeFinished: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val valueText = pluralStringResource(R.plurals.generator_length_value, length, length)
    val lengthLabel = stringResource(R.string.generator_length)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = stringResource(R.string.generator_length),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = colors.onSurface,
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = colors.onSurface,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
        val sliderColors = SliderDefaults.colors(
            thumbColor = colors.primary,
            activeTrackColor = colors.onSurface,
            inactiveTrackColor = colors.outline.copy(alpha = 0.3f),
        )
        val interaction = remember { MutableInteractionSource() }
        Slider(
            value = length.toFloat(),
            onValueChange = { onLengthChange(it.roundToInt()) },
            onValueChangeFinished = onLengthChangeFinished,
            valueRange = GeneratorOptions.MIN_LENGTH.toFloat()..GeneratorOptions.MAX_LENGTH.toFloat(),
            steps = GeneratorOptions.MAX_LENGTH - GeneratorOptions.MIN_LENGTH - 1,
            interactionSource = interaction,
            colors = sliderColors,
            // Figma 46:1054: pista de 4 dp y disco de 24 dp; los pasos existen para TalkBack pero no se dibujan.
            thumb = {
                SliderDefaults.Thumb(
                    interactionSource = interaction,
                    colors = sliderColors,
                    thumbSize = DpSize(24.dp, 24.dp),
                )
            },
            track = { sliderState ->
                SliderDefaults.Track(
                    sliderState = sliderState,
                    modifier = Modifier.height(4.dp),
                    colors = sliderColors,
                    drawStopIndicator = null,
                    drawTick = { _, _ -> },
                    thumbTrackGapSize = 0.dp,
                    trackInsideCornerSize = 0.dp,
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .semantics {
                    contentDescription = lengthLabel
                    stateDescription = valueText
                },
        )
    }
}

@Composable
private fun ToggleGroup(options: GeneratorOptions, onToggle: (GeneratorToggle) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, GroupShape)
            .padding(vertical = 4.dp),
    ) {
        ToggleRow("A", stringResource(R.string.generator_upper), null, options.uppercase) { onToggle(GeneratorToggle.Uppercase) }
        RowDivider()
        ToggleRow("1", stringResource(R.string.generator_digits), null, options.digits) { onToggle(GeneratorToggle.Digits) }
        RowDivider()
        ToggleRow("#", stringResource(R.string.generator_symbols), null, options.symbols) { onToggle(GeneratorToggle.Symbols) }
        RowDivider()
        ToggleRow(
            "Il",
            stringResource(R.string.generator_avoid),
            stringResource(R.string.generator_avoid_hint),
            options.avoidAmbiguous,
        ) { onToggle(GeneratorToggle.AvoidAmbiguous) }
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 12.dp),
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
    )
}

/**
 * Una fila de ajuste (Figma 46:1072): el cuadro con el carácter que agrega (DEC-59), el texto y el interruptor.
 * Toda la fila es el interruptor para TalkBack (48 dp de alto de sobra) y el cuadro no se lee.
 */
@Composable
private fun ToggleRow(glyph: String, title: String, hint: String?, checked: Boolean, onChange: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = { onChange() })
            .padding(start = 12.dp, end = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(colors.background, TileShape)
                .clearAndSetSemantics { },
            contentAlignment = Alignment.Center,
        ) {
            Text(text = glyph, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = colors.onSurface)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = colors.onSurface,
            )
            if (hint != null) {
                Text(text = hint, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onPrimary,
                checkedTrackColor = colors.primary,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = colors.surface,
                uncheckedTrackColor = colors.outline.copy(alpha = 0.6f),
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

/**
 * «Generar» desde Nueva/editar: el mismo generador en una hoja inferior con «Usar esta contraseña», que la devuelve
 * al campo (DEC-24). La hoja lleva FLAG_SECURE y la contraseña se borra al cerrarla o al salir de primer plano.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneratorSheet(
    onUse: (String) -> Unit,
    onDismiss: () -> Unit,
    viewModel: GeneratorViewModel = hiltViewModel(key = "generator_sheet"),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnUse by rememberUpdatedState(onUse)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            if (effect is GeneratorEffect.Use) {
                currentOnUse(effect.password)
                // Ya se entregó: no se queda en el ViewModel (que vive mientras viva la pantalla de abajo).
                viewModel.onHide()
            }
        }
    }
    DisposableEffect(viewModel) { onDispose { viewModel.onHide() } }
    ModalBottomSheet(
        onDismissRequest = {
            viewModel.onHide()
            onDismiss()
        },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        properties = ModalBottomSheetProperties(securePolicy = SecureFlagPolicy.SecureOn),
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        SecureScreen()
        LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onHide() }
        LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.ensureGenerated() }
        GeneratorSheetContent(
            state = state,
            onRegenerate = viewModel::onRegenerate,
            onCopy = viewModel::onCopy,
            onLengthChange = viewModel::onLengthChange,
            onLengthChangeFinished = viewModel::onLengthChangeFinished,
            onToggle = viewModel::onToggle,
            onUse = viewModel::onUse,
        )
    }
}

/** El contenido de la hoja (título, generador y «Usar esta contraseña»), sin estado, para probarlo y verlo en la vista previa. */
@Composable
fun GeneratorSheetContent(
    state: GeneratorUiState,
    onRegenerate: () -> Unit,
    onCopy: () -> Unit,
    onLengthChange: (Int) -> Unit,
    onLengthChangeFinished: () -> Unit,
    onToggle: (GeneratorToggle) -> Unit,
    onUse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp)
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(
            text = stringResource(R.string.generator_title_bold),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        GeneratorContent(
            state = state,
            onRegenerate = onRegenerate,
            onCopy = onCopy,
            onLengthChange = onLengthChange,
            onLengthChangeFinished = onLengthChangeFinished,
            onToggle = onToggle,
        )
        PrimaryButton(
            text = stringResource(R.string.generator_use),
            onClick = onUse,
            showArrow = false,
            enabled = state.password.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun previewState(options: GeneratorOptions = GeneratorOptions(), copied: Int? = null) = GeneratorUiState(
    options = options,
    password = "q7#Vt!m2Lp@9xR4w",
    strengthScore = 4,
    generation = 1,
    copiedSeconds = copied,
)

@Preview(name = "Generador", device = "spec:width=412dp,height=917dp")
@Composable
private fun GeneratorPreview() {
    PreviewSurface {
        GeneratorScreen(previewState(), {}, {}, {}, {}, {}, {}, {})
    }
}

@Preview(name = "Generador · copiada y sin símbolos", device = "spec:width=412dp,height=917dp")
@Composable
private fun GeneratorCopiedPreview() {
    PreviewSurface {
        GeneratorScreen(previewState(GeneratorOptions(24, symbols = false, avoidAmbiguous = true), copied = 30), {}, {}, {}, {}, {}, {}, {})
    }
}

@Preview(name = "Generador · hoja de Nueva/editar", widthDp = 412)
@Composable
private fun GeneratorSheetPreview() {
    PreviewSurface {
        GeneratorSheetContent(previewState(), {}, {}, {}, {}, {}, {})
    }
}
