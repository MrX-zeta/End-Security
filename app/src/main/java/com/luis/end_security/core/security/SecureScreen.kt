package com.luis.end_security.core.security

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.view.View
import android.view.ViewParent
import android.view.Window
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

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
 *
 * Dentro de un diálogo o una hoja inferior protege la ventana de ese diálogo (tiene la suya),
 * no la de la actividad. Si no encuentra ventana, en compilaciones de depuración lanza un
 * error para no dejar una pantalla con secretos sin protección sin que nadie lo note.
 */
@Composable
fun SecureScreen() {
    if (LocalInspectionMode.current) return // @Preview: no hay ventana real.
    val view = LocalView.current
    val window = view.dialogWindow() ?: LocalActivity.current?.window
    if (window == null) {
        check(!view.context.isDebuggable()) { "SecureScreen sin ventana: la pantalla quedaría sin FLAG_SECURE" }
        return
    }
    val root = view.rootView
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

/** Ventana del diálogo (o de la hoja inferior) que contiene a [this], si lo hay. */
private fun View.dialogWindow(): Window? {
    var parent: ViewParent? = parent
    while (parent != null) {
        if (parent is DialogWindowProvider) return parent.window
        parent = parent.parent
    }
    return null
}

private fun Context.isDebuggable(): Boolean =
    (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

/**
 * FLAG_SECURE compartido por ventana (ver [SharedRequest]). Todo el código que ponga o quite
 * FLAG_SECURE debe pasar por aquí (por ejemplo, un ajuste futuro de «pantalla siempre segura»
 * haría un [SharedRequest.acquire] permanente); si alguien lo quitara por fuera, el conteo
 * dejaría de ser cierto.
 */
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
