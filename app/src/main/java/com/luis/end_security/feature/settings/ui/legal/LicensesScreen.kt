package com.luis.end_security.feature.settings.ui.legal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.SecureFlagPolicy
import com.luis.end_security.R
import com.luis.end_security.core.designsystem.component.BackTopBar
import com.luis.end_security.core.designsystem.component.ListCard
import com.luis.end_security.core.designsystem.component.ListRowDivider
import com.luis.end_security.core.designsystem.component.ListRowIcon
import com.luis.end_security.core.designsystem.theme.PreviewSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Prefijo de la etiqueta de prueba de cada fila de la lista (`licenses_row_<índice>`). */
const val LICENSES_ROW_TAG = "licenses_row_"

/** Etiqueta del texto de la licencia dentro de la hoja. */
const val LICENSE_TEXT_TAG = "license_text"

/**
 * Pantalla «Licencias» (PEND-12): la lista fija de [Licenses] y, al tocar una fila, el texto completo de su licencia en una hoja.
 * No muestra secretos; funciona sin sesión.
 */
@Composable
fun LicensesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    // Qué fila está abierta sobrevive a girar la pantalla; es un índice, nada sensible.
    var openIndex by rememberSaveable { mutableStateOf(-1) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        BackTopBar(onBack = onBack)
        LicensesBody(entries = Licenses.entries, onOpen = { openIndex = it })
    }
    Licenses.entries.getOrNull(openIndex)?.let { entry ->
        LicenseTextSheet(entry = entry, onDismiss = { openIndex = -1 })
    }
}

/** La lista sin estado, para probarla y verla en la vista previa. */
@Composable
fun LicensesBody(entries: List<LicenseEntry>, onOpen: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.licenses_title),
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.licenses_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        ListCard {
            entries.forEachIndexed { index, entry ->
                if (index > 0) ListRowDivider()
                LicenseRow(entry = entry, onClick = { onOpen(index) }, modifier = Modifier.testTag("$LICENSES_ROW_TAG$index"))
            }
        }
    }
}

@Composable
private fun LicenseRow(entry: LicenseEntry, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClickLabel = stringResource(R.string.licenses_open_text), role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ListRowIcon(R.drawable.ic_file_text_20, danger = false)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = entry.holder,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = entry.license.shortName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right_20),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(end = 4.dp),
        )
    }
}

/** El texto completo de la licencia de [entry], leído de `assets/licenses/`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LicenseTextSheet(entry: LicenseEntry, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val text by produceState<String?>(null, entry.license) {
        value = try {
            withContext(Dispatchers.IO) { context.assets.open(entry.license.assetPath).bufferedReader(Charsets.UTF_8).use { it.readText() } }
        } catch (_: Exception) {
            ""
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        properties = ModalBottomSheetProperties(securePolicy = SecureFlagPolicy.SecureOn),
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 32.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = "${entry.holder} · ${entry.license.shortName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val content = text
            Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Text(
                    text = if (content.isNullOrEmpty()) (if (content == null) "" else stringResource(R.string.licenses_load_error)) else content,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag(LICENSE_TEXT_TAG),
                )
            }
        }
    }
}

@Preview(name = "Licencias")
@Composable
private fun LicensesPreview() {
    PreviewSurface { LicensesBody(entries = Licenses.entries, onOpen = {}) }
}
