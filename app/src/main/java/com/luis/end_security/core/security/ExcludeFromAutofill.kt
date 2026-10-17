package com.luis.end_security.core.security

import android.view.View
import android.view.autofill.AutofillManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/**
 * Saca del autocompletado del sistema todo lo que se dibuja en esta pantalla, mientras está
 * visible. Es para la contraseña maestra: no queremos que otro gestor de contraseñas la
 * guarde ni la ofrezca.
 *
 * Se hace a nivel de vista porque `BasicSecureTextField` marca su campo como
 * `ContentType.Password` y Compose no permite quitarlo campo por campo. El costo es que el
 * correo de la misma pantalla tampoco se autocompleta.
 */
@Composable
fun ExcludeFromAutofill() {
    val view = LocalView.current
    DisposableEffect(view) {
        val autofillManager = view.context.getSystemService(AutofillManager::class.java)
        val previous = view.importantForAutofill
        // Si ya había una sesión de autocompletado abierta, se descarta.
        autofillManager?.cancel()
        view.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        onDispose {
            // Al salir tampoco debe aparecer el aviso de «¿Guardar contraseña?».
            autofillManager?.cancel()
            view.importantForAutofill = previous
        }
    }
}
