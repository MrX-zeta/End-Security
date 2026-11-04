package com.luis.end_security.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.SecureFlagPolicy
import com.luis.end_security.R

/** Hoja de «Cerrar sesión» que se muestra (Figma 215:3811 y 215:4020). La usan Ajustes y Desbloquear. */
sealed interface SignOutSheet {
    /** «¿Cerrar sesión?» */
    data object Confirm : SignOutSheet

    /** «Tienes N cambios sin subir»: [count] elementos `pending` o `conflict`. */
    data class Unsynced(val count: Int) : SignOutSheet
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignOutBottomSheet(
    sheet: SignOutSheet,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    locked: Boolean = false,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        properties = ModalBottomSheetProperties(securePolicy = SecureFlagPolicy.SecureOn),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        SignOutSheetContent(
            sheet = sheet,
            onConfirm = onConfirm,
            onCancel = onDismiss,
            locked = locked,
            modifier = Modifier.navigationBarsPadding(),
        )
    }
}

/**
 * Hojas de «Cerrar sesión»: la normal (Figma 215:3811) y la de cambios sin subir (215:4020). Con [locked] (bóveda
 * bloqueada, en Desbloquear) el texto dice «Desbloquea tu bóveda y espera a que se suban».
 */
@Composable
fun SignOutSheetContent(
    sheet: SignOutSheet,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    locked: Boolean = false,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = 8.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SheetIconDisc(R.drawable.ic_log_out_20, danger = true)
        Spacer(Modifier.height(12.dp))
        val (title, body, confirm) = when (sheet) {
            SignOutSheet.Confirm -> Triple(
                stringResource(R.string.settings_sign_out_title),
                stringResource(R.string.settings_sign_out_body),
                stringResource(R.string.settings_sign_out_confirm),
            )
            is SignOutSheet.Unsynced -> Triple(
                pluralStringResource(R.plurals.settings_sign_out_unsynced_title, sheet.count, sheet.count),
                stringResource(
                    if (locked) R.string.settings_sign_out_unsynced_body_locked else R.string.settings_sign_out_unsynced_body,
                ),
                stringResource(R.string.settings_sign_out_anyway),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        DangerButton(text = confirm, onClick = onConfirm, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        SecondaryButton(text = stringResource(R.string.settings_sign_out_cancel), onClick = onCancel, modifier = Modifier.fillMaxWidth())
    }
}

/** Disco de 64 con el ícono de la hoja (rojo suave con `danger`, gris con el resto). */
@Composable
fun SheetIconDisc(@DrawableRes icon: Int, danger: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(64.dp)
            .background(if (danger) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.background, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp),
        )
    }
}
