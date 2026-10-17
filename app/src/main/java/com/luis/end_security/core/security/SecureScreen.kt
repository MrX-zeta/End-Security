package com.luis.end_security.core.security

import android.view.Window
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

/**
 * Activa FLAG_SECURE en la ventana mientras la pantalla que lo llama está en composición
 * (regla no negociable 7, DEC-26): bloquea capturas, grabación de pantalla y la vista previa
 * en Recientes. Al salir lo quita, salvo que otra pantalla segura siga visible.
 */
@Composable
fun SecureScreen() {
    val window = LocalActivity.current?.window ?: return
    DisposableEffect(window) {
        SecureWindowFlag.acquire(window)
        onDispose { SecureWindowFlag.release(window) }
    }
}

/** FLAG_SECURE compartido por ventana (ver [SharedRequest]). */
internal val SecureWindowFlag = SharedRequest<Window, Unit>(
    apply = { window -> window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) },
    restore = { window, _ -> window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) },
)
