package com.luis.end_security.feature.settings.ui.legal

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.SecureFlagPolicy
import com.luis.end_security.R
import com.luis.end_security.core.designsystem.component.BackTopBar
import com.luis.end_security.core.designsystem.theme.PreviewSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Lo que dice el estado de carga del documento. */
private sealed interface Loaded {
    data object Loading : Loaded

    data object Failed : Loaded

    data class Ready(val model: LegalDocumentModel) : Loaded
}

/**
 * Un documento legal (PEND-12) leído de `assets/legal/` (copia de `docs/legal/`) e interpretado con [LegalMarkdown]. Es el mismo
 * en la pantalla (desde Ajustes) y en la hoja inferior (desde Crear cuenta); funciona sin sesión.
 */
@Composable
fun LegalDocumentContent(
    document: LegalDocument,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val loaded by produceState<Loaded>(Loaded.Loading, document) {
        value = try {
            val text = withContext(Dispatchers.IO) { context.assets.open(document.assetPath).bufferedReader(Charsets.UTF_8).use { it.readText() } }
            Loaded.Ready(LegalMarkdown.parse(text))
        } catch (_: Exception) {
            Loaded.Failed
        }
    }
    when (val state = loaded) {
        Loaded.Loading -> Box(modifier.fillMaxWidth().height(160.dp))
        Loaded.Failed -> Text(
            text = stringResource(R.string.legal_load_error),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(horizontal = 32.dp, vertical = 16.dp),
        )
        is Loaded.Ready -> {
            // Qué secciones están abiertas sobrevive a girar la pantalla; son índices, nada sensible.
            var expanded by rememberSaveable(document) { mutableStateOf(emptyList<Int>()) }
            LegalDocumentBody(
                model = state.model,
                expanded = expanded.toSet(),
                onToggle = { index -> expanded = if (index in expanded) expanded - index else expanded + index },
                onToggleAll = { expanded = if (expanded.size == state.model.sections.size) emptyList() else state.model.sections.indices.toList() },
                modifier = modifier,
            )
        }
    }
}

/**
 * El documento ya interpretado: título, fecha, la tarjeta «Resumen» abierta y cada sección `##` como un acordeón plegado, con
 * «Abrir todo / Cerrar todo». Sin estado propio para poder probarlo: [expanded] son los índices de [LegalDocumentModel.sections] abiertos.
 */
@Composable
fun LegalDocumentBody(
    model: LegalDocumentModel,
    expanded: Set<Int>,
    onToggle: (Int) -> Unit,
    onToggleAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(0.dp))
        Text(
            text = model.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        if (model.meta.isNotEmpty()) {
            Text(text = model.meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        model.intro.forEach { BlockView(it) }
        if (model.summary.isNotEmpty()) SummaryCard(model.summary)
        if (model.sections.isNotEmpty()) {
            val all = expanded.size == model.sections.size
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Text(
                    text = stringResource(if (all) R.string.legal_close_all else R.string.legal_open_all),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clickable(role = Role.Button, onClick = onToggleAll)
                        .padding(horizontal = 4.dp, vertical = 14.dp),
                )
            }
        }
        model.sections.forEachIndexed { index, section ->
            SectionAccordion(section = section, expanded = index in expanded, onToggle = { onToggle(index) })
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** «Resumen»: tarjeta siempre abierta con el fondo amarillo claro (`primary` suave). */
@Composable
private fun SummaryCard(blocks: List<Block>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.28f), RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.legal_summary_title),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        blocks.forEach { BlockView(it) }
    }
}

/**
 * Una sección plegable. El encabezado es un botón que dice si está «Expandido» o «Contraído» (TalkBack) y el texto, abierto,
 * se lee en orden justo después.
 */
@Composable
private fun SectionAccordion(section: LegalSection, expanded: Boolean, onToggle: () -> Unit) {
    val stateText = stringResource(if (expanded) R.string.legal_expanded else R.string.legal_collapsed)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(role = Role.Button, onClick = onToggle)
                .semantics(mergeDescendants = true) { stateDescription = stateText }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = section.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right_20),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                // Cerrado apunta a la derecha; abierto, hacia abajo.
                modifier = Modifier.size(20.dp).rotate(if (expanded) 90f else 0f),
            )
        }
        if (expanded) {
            Column(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                section.blocks.forEach { BlockView(it) }
            }
        }
    }
}

@Composable
private fun annotated(spans: List<Span>): AnnotatedString = buildAnnotatedString {
    spans.forEach { span ->
        if (span.bold) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(span.text) } else append(span.text)
    }
}

@Composable
private fun BlockView(block: Block) {
    val color = MaterialTheme.colorScheme.onSurface
    when (block) {
        is Block.Paragraph -> Text(annotated(block.spans), style = MaterialTheme.typography.bodyMedium, color = color)
        is Block.Subtitle -> Text(
            text = block.text,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = color,
            modifier = Modifier.padding(top = 4.dp),
        )
        is Block.Bullet -> ListItem(marker = "•", spans = block.spans)
        is Block.Numbered -> ListItem(marker = "${block.number}.", spans = block.spans)
        is Block.Card -> Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background, RoundedCornerShape(16.dp))
                .padding(12.dp)
                .semantics(mergeDescendants = true) {},
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // El primer campo de la fila, en negrita, y debajo «Qué hace: …» y «Datos que recibe: …».
            Text(
                annotated(block.title.map { it.copy(bold = true) }),
                style = MaterialTheme.typography.bodyMedium,
                color = color,
            )
            block.fields.forEach { field ->
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(field.label + ": ") }
                        append(annotated(field.spans))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ListItem(marker: String, spans: List<Span>) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.semantics(mergeDescendants = true) {}) {
        Text(
            text = marker,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(if (marker.length > 1) 24.dp else 12.dp),
        )
        Text(annotated(spans), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
    }
}

/** Documento legal como pantalla (ruta [com.luis.end_security.navigation.Routes.LEGAL_DOCUMENT]). Tema claro. */
@Composable
fun LegalDocumentScreen(
    document: LegalDocument,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        BackTopBar(onBack = onBack)
        LegalDocumentContent(document = document)
    }
}

/**
 * Documento legal en una hoja inferior casi a pantalla completa, sobre la pantalla que lo abre
 * (Crear cuenta). Así lo que el usuario ya escribió se conserva. La hoja solo muestra el
 * documento, y su ventana lleva FLAG_SECURE como la pantalla de abajo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalDocumentSheet(
    document: LegalDocument,
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
        LegalDocumentContent(
            document = document,
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
        )
    }
}

@Preview(name = "Documento legal")
@Composable
private fun LegalDocumentScreenPreview() {
    PreviewSurface {
        LegalDocumentBody(
            model = LegalMarkdown.parse("# Aviso\n\n**End Security** · fecha\n\n## Resumen\n- Uno\n- Dos\n\n## 1. Sección\nTexto **fuerte**."),
            expanded = setOf(0),
            onToggle = {},
            onToggleAll = {},
        )
    }
}
