package com.luis.end_security.core.security

import android.view.View
import android.view.autofill.AutofillManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/**
 * Saca del autocompletado del sistema todo lo que se dibuja en esta ventana, mientras la
 * pantalla está visible. Es para la contraseña maestra: no queremos que otro gestor de
 * contraseñas la guarde ni la ofrezca (regla no negociable 1).
 *
 * - Se marca la vista raíz de la ventana y no la de Compose: `View.isImportantForAutofill()`
 *   revisa los ancestros, y así no dependemos de lo que Compose escriba en su propia vista.
 * - Se cuenta por vista ([SharedRequest]): en una transición entre dos pantallas que lo piden,
 *   la que sale no reactiva el autocompletado de la que entra.
 * - Se hace a nivel de ventana porque `BasicSecureTextField` marca su campo como
 *   `ContentType.Password` y Compose no permite quitarlo campo por campo. El costo es que el
 *   correo de la misma pantalla tampoco se autocompleta.
 */
@Composable
fun ExcludeFromAutofill() {
    val root = LocalView.current.rootView
    DisposableEffect(root) {
        val autofillManager = root.context.getSystemService(AutofillManager::class.java)
        // Si ya había una sesión de autocompletado abierta, se descarta.
        autofillManager?.cancel()
        AutofillExclusion.acquire(root)
        onDispose {
            // Al salir tampoco debe aparecer el aviso de «¿Guardar contraseña?».
            autofillManager?.cancel()
            AutofillExclusion.release(root)
        }
    }
}

/** Exclusión compartida por vista raíz (ver [SharedRequest]). */
internal val AutofillExclusion = SharedRequest<View, Int>(
    apply = { view ->
        view.importantForAutofill.also {
            view.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        }
    },
    restore = { view, original -> view.importantForAutofill = original },
)
