package com.luis.end_security.feature.settings.ui.legal

import androidx.annotation.StringRes
import com.luis.end_security.R

/** Documentos legales de PEND-12 que la app muestra. El texto aún está en redacción. */
enum class LegalDocument(val routeArg: String, @param:StringRes val title: Int) {
    Terms(routeArg = "terms", title = R.string.legal_terms_title),
    Privacy(routeArg = "privacy", title = R.string.legal_privacy_title),
    ;

    companion object {
        fun fromRouteArg(value: String?): LegalDocument? = entries.firstOrNull { it.routeArg == value }
    }
}
