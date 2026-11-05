package com.luis.end_security.feature.settings.ui.legal

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.luis.end_security.BuildConfig
import com.luis.end_security.R
import com.luis.end_security.core.designsystem.component.BackTopBar
import com.luis.end_security.core.designsystem.component.ListCard
import com.luis.end_security.core.designsystem.component.ListRowDivider
import com.luis.end_security.core.designsystem.component.ListRowIcon
import com.luis.end_security.core.designsystem.component.MessageBar
import com.luis.end_security.core.designsystem.theme.PreviewSurface
import kotlinx.coroutines.delay

/** El correo de «Reportar un problema de seguridad»: destinatario [LegalInfo.PRIVACY_EMAIL], asunto fijo y sin cuerpo (nada del usuario). */
internal fun securityReportIntent(): Intent =
    Intent(Intent.ACTION_SENDTO, "mailto:".toUri())
        .putExtra(Intent.EXTRA_EMAIL, arrayOf(LegalInfo.PRIVACY_EMAIL))
        .putExtra(Intent.EXTRA_SUBJECT, LegalInfo.SECURITY_REPORT_SUBJECT)

/** El correo de «Soporte»: destinatario [LegalInfo.SUPPORT_EMAIL], asunto fijo y sin cuerpo. */
internal fun supportIntent(): Intent =
    Intent(Intent.ACTION_SENDTO, "mailto:".toUri())
        .putExtra(Intent.EXTRA_EMAIL, arrayOf(LegalInfo.SUPPORT_EMAIL))
        .putExtra(Intent.EXTRA_SUBJECT, LegalInfo.SUPPORT_SUBJECT)

/** Abre [intent]; `false` si el teléfono no tiene una app que lo atienda. */
private fun Context.tryStart(intent: Intent): Boolean = try {
    startActivity(intent)
    true
} catch (_: ActivityNotFoundException) {
    false
}

/**
 * Pantalla «Acerca de» (PEND-12): nombre y versión, línea académica, crédito de HIBP (CC BY 4.0), «Reportar un problema de
 * seguridad» (correo) y el enlace al repositorio. No muestra secretos; funciona sin sesión.
 */
@Composable
fun AboutScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var unavailable by remember { mutableStateOf(0) }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            BackTopBar(onBack = onBack)
            AboutBody(
                versionName = BuildConfig.VERSION_NAME,
                onReportSecurityIssue = { if (!context.tryStart(securityReportIntent())) unavailable++ },
                onContactSupport = { if (!context.tryStart(supportIntent())) unavailable++ },
                onOpenRepository = {
                    if (!context.tryStart(Intent(Intent.ACTION_VIEW, LegalInfo.REPOSITORY_URL.toUri()))) unavailable++
                },
            )
        }
        UnavailableBar(token = unavailable, modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 32.dp, vertical = 8.dp))
    }
}

/** «No hay una app para abrirlo» un momento; [token] cambia cada vez que hay que mostrarlo. */
@Composable
private fun UnavailableBar(token: Int, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(token) {
        if (token > 0) {
            visible = true
            delay(4_000)
            visible = false
        }
    }
    if (visible) MessageBar(text = stringResource(R.string.about_no_app), icon = R.drawable.ic_info_20, modifier = modifier)
}

/** Lo que se ve, sin estado ni intents: así se prueba y se previsualiza. */
@Composable
fun AboutBody(
    versionName: String,
    onReportSecurityIssue: () -> Unit,
    onContactSupport: () -> Unit,
    onOpenRepository: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.about_title),
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.about_version, versionName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = stringResource(R.string.about_academic),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.about_hibp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        ListCard {
            AboutRow(icon = R.drawable.ic_shield_alert_20, label = stringResource(R.string.about_report_security), onClick = onReportSecurityIssue)
            ListRowDivider()
            AboutRow(icon = R.drawable.ic_mail_20, label = stringResource(R.string.about_support), onClick = onContactSupport)
            ListRowDivider()
            AboutRow(icon = R.drawable.ic_external_link_20, label = stringResource(R.string.about_repository), onClick = onOpenRepository)
        }
    }
}

@Composable
private fun AboutRow(@androidx.annotation.DrawableRes icon: Int, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ListRowIcon(icon, danger = false)
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right_20),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(end = 4.dp),
        )
    }
}

@Preview(name = "Acerca de")
@Composable
private fun AboutPreview() {
    PreviewSurface { AboutBody(versionName = "1.0", onReportSecurityIssue = {}, onContactSupport = {}, onOpenRepository = {}) }
}
