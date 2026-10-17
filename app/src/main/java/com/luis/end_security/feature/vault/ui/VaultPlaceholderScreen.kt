package com.luis.end_security.feature.vault.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.luis.end_security.R
import com.luis.end_security.core.designsystem.component.BottomNavBar
import com.luis.end_security.core.designsystem.component.BottomNavTab

/** Marcador de Bóveda · Lista (SCR-10) hasta que se implemente. */
@Composable
fun VaultPlaceholderScreen(
    onTabSelected: (BottomNavTab) -> Unit,
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
                .padding(horizontal = 32.dp, vertical = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.vault_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.vault_placeholder),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        BottomNavBar(selected = BottomNavTab.Vault, onTabSelected = onTabSelected)
    }
}
