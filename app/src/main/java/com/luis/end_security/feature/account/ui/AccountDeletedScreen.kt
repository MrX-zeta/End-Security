package com.luis.end_security.feature.account.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.luis.end_security.R
import com.luis.end_security.core.designsystem.component.BlobShape
import com.luis.end_security.core.designsystem.component.NeumorphicDisc
import com.luis.end_security.core.designsystem.component.OrganicBlob
import com.luis.end_security.core.designsystem.component.PrimaryButton

/**
 * SCR-38 · Cuenta eliminada (Figma 62:3270). Va fuera de `RequireUnlocked` y con la pila limpia: la cuenta y todos sus datos
 * ya no están en este teléfono. «Ir al inicio» y Atrás del sistema hacen lo mismo: Iniciar sesión.
 */
@Composable
fun AccountDeletedRoute(onGoHome: () -> Unit) {
    BackHandler(onBack = onGoHome)
    AccountDeletedScreen(onGoHome = onGoHome)
}

@Composable
fun AccountDeletedScreen(onGoHome: () -> Unit, modifier: Modifier = Modifier) {
    // En las pantallas claras de Figma las manchas son un gris apenas más oscuro que el fondo (el token `blob` es el del onboarding oscuro).
    val blobColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Manchas de Figma: una grande arriba a la derecha y otra abajo a la izquierda, ambas cortadas por el borde.
        OrganicBlob(
            shape = BlobShape.B,
            color = blobColor,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 92.dp, y = (-56).dp)
                .requiredSize(width = 230.dp, height = 230.dp),
        )
        OrganicBlob(
            shape = BlobShape.A,
            color = blobColor,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-92).dp, y = (-96).dp)
                .requiredSize(width = 210.dp, height = 220.dp),
        )
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(0.9f))
            NeumorphicDisc(
                icon = R.drawable.ic_check_40,
                contentDescription = null,
                iconTint = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(36.dp))
            Text(
                text = stringResource(R.string.deleted_title),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.deleted_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.weight(1.5f))
            PrimaryButton(
                text = stringResource(R.string.deleted_go_home),
                onClick = onGoHome,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
