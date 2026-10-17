package com.luis.end_security.feature.settings.ui.legal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.luis.end_security.R
import com.luis.end_security.core.designsystem.component.BackTopBar
import com.luis.end_security.core.designsystem.theme.PreviewSurface

/** Marcador de un documento legal (PEND-12) hasta que exista su texto. Tema claro. */
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
        Column(modifier = Modifier.padding(horizontal = 32.dp)) {
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
        }
    }
}

@Preview(name = "Documento legal (marcador)")
@Composable
private fun LegalDocumentScreenPreview() {
    PreviewSurface {
        LegalDocumentScreen(document = LegalDocument.Privacy, onBack = {})
    }
}
