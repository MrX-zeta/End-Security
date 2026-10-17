package com.luis.end_security.core.security

import android.view.Window
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.annotation.MainThread
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import java.util.WeakHashMap

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

/**
 * Cuenta cuántas pantallas seguras hay por ventana. Durante una transición de navegación
 * conviven la pantalla que sale y la que entra; sin el conteo, la que sale quitaría el
 * flag de la que entra. Solo se usa desde el hilo principal (efectos de Compose).
 */
internal object SecureWindowFlag {
    private val requests = WeakHashMap<Window, Int>()

    @MainThread
    fun acquire(window: Window) {
        val count = (requests[window] ?: 0) + 1
        requests[window] = count
        if (count == 1) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    @MainThread
    fun release(window: Window) {
        val count = (requests[window] ?: 1) - 1
        if (count <= 0) {
            requests.remove(window)
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            requests[window] = count
        }
    }
}
