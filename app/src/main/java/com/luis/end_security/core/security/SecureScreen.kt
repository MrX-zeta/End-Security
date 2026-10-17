package com.luis.end_security.core.security

import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/**
 * Protege la pantalla que lo llama mientras está en composición (regla no negociable 7, DEC-26):
 * - FLAG_SECURE en la ventana: bloquea capturas, grabación de pantalla y la vista previa en
 *   Recientes.
 * - En Android 14 o superior, marca la ventana como datos sensibles para accesibilidad: solo
 *   los servicios declarados como herramientas de accesibilidad (TalkBack, Switch Access…) ven
 *   su contenido. Compose expone el texto real de los campos de contraseña en el árbol de
 *   accesibilidad, y FLAG_SECURE no protege contra eso. En Android 9 a 13 no hay equivalente.
 *
 * Al salir se quita todo, salvo que otra pantalla segura siga visible.
 */
@Composable
fun SecureScreen() {
    val window = LocalActivity.current?.window ?: return
    val root = LocalView.current.rootView
    DisposableEffect(window, root) {
        SecureWindowFlag.acquire(window)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            AccessibilityDataSensitive.acquire(root)
        }
        onDispose {
            SecureWindowFlag.release(window)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                AccessibilityDataSensitive.release(root)
            }
        }
    }
}

/** FLAG_SECURE compartido por ventana (ver [SharedRequest]). */
internal val SecureWindowFlag = SharedRequest<Window, Unit>(
    apply = { window -> window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) },
    restore = { window, _ -> window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) },
)

/**
 * Datos sensibles para accesibilidad, compartido por vista raíz. Se marca la raíz porque las
 * vistas en modo AUTO (la de Compose incluida) heredan la marca de sus ancestros.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
internal val AccessibilityDataSensitive = SharedRequest<View, Boolean>(
    apply = { view ->
        view.isAccessibilityDataSensitive.also {
            view.setAccessibilityDataSensitive(View.ACCESSIBILITY_DATA_SENSITIVE_YES)
        }
    },
    restore = { view, wasSensitive ->
        view.setAccessibilityDataSensitive(
            if (wasSensitive) View.ACCESSIBILITY_DATA_SENSITIVE_YES else View.ACCESSIBILITY_DATA_SENSITIVE_AUTO,
        )
    },
)
