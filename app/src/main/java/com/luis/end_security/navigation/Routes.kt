package com.luis.end_security.navigation

import com.luis.end_security.feature.settings.ui.legal.LegalDocument

/** Rutas de la app. Se agregan conforme se implementan las pantallas del grafo (SCR-*). */
object Routes {
    /** SCR-01…SCR-03. */
    const val ONBOARDING = "onboarding"

    /** SCR-04. */
    const val LOGIN = "login"

    /** SCR-05. */
    const val SIGN_UP = "sign_up"

    /** Documento legal (PEND-12); el argumento es [LegalDocument.routeArg]. */
    const val LEGAL_DOCUMENT_ARG = "document"
    const val LEGAL_DOCUMENT = "legal_document/{$LEGAL_DOCUMENT_ARG}"

    fun legalDocument(document: LegalDocument): String = "legal_document/${document.routeArg}"
}
