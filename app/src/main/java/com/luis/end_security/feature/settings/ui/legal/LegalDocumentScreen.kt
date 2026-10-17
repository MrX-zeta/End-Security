package com.luis.end_security.feature.settings.ui.legal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.SecureFlagPolicy
import com.luis.end_security.R
import com.luis.end_security.core.designsystem.component.BackTopBar
import com.luis.end_security.core.designsystem.theme.PreviewSurface

/**
 * Contenido de un documento legal (PEND-12): marcador hasta que exista su texto. Es el mismo
 * en la pantalla (desde Ajustes) y en la hoja inferior (desde Crear cuenta).
 */
@Composable
fun LegalDocumentContent(
    document: LegalDocument,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(document.title),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.legal_in_progress),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
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

@Preview(name = "Documento legal (marcador)")
@Composable
private fun LegalDocumentScreenPreview() {
    PreviewSurface {
        LegalDocumentScreen(document = LegalDocument.Privacy, onBack = {})
    }
}
