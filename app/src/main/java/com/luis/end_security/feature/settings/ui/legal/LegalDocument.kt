package com.luis.end_security.feature.settings.ui.legal

import androidx.annotation.StringRes
import com.luis.end_security.R

/**
 * Documentos legales de PEND-12 que la app muestra. El texto vive en `docs/legal/` y la tarea de Gradle `copyLegalDocs` lo copia a
 * `assets/legal/` ([assetPath]); no se reescribe aquí.
 */
enum class LegalDocument(
    val routeArg: String,
    /** El nombre corto con el que se enlaza en Crear cuenta («Términos»). */
    @param:StringRes val title: Int,
    /** El título de la barra superior del documento (Figma 235:4976 y 236:5003). */
    @param:StringRes val screenTitle: Int,
    val assetPath: String,
) {
    Terms(routeArg = "terms", title = R.string.legal_terms_title, screenTitle = R.string.settings_legal_terms, assetPath = "legal/terminos-y-condiciones.md"),
    Privacy(routeArg = "privacy", title = R.string.legal_privacy_title, screenTitle = R.string.legal_privacy_title, assetPath = "legal/aviso-de-privacidad.md"),
    ;

    companion object {
        fun fromRouteArg(value: String?): LegalDocument? = entries.firstOrNull { it.routeArg == value }
    }
}
