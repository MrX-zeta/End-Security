package com.luis.end_security.navigation

import android.net.Uri
import java.util.UUID
import com.luis.end_security.feature.auth.ui.verify.VerifyEmailViewModel
import com.luis.end_security.feature.settings.ui.legal.LegalDocument

/** Rutas de la app. Se agregan conforme se implementan las pantallas del grafo (SCR-*). */
object Routes {
    /** SCR-01…SCR-03. */
    const val ONBOARDING = "onboarding"

    /** SCR-04. */
    const val LOGIN = "login"

    /** SCR-05. */
    const val SIGN_UP = "sign_up"

    /** SCR-07: verificación en dos pasos al entrar. */
    const val TWO_FACTOR = "two_factor"

    /** SCR-08: código de respaldo. */
    const val BACKUP_CODE = "backup_code"

    /** SCR-09. Por ahora solo se llega con la ruta de depuración; LockGuard llegará con VaultSession. */
    const val UNLOCK = "unlock"

    /** SCR-39: primera descarga de la bóveda después de entrar. */
    const val VAULT_LOADING = "vault_loading"

    /** SCR-10 y SCR-11: la lista de la bóveda (o la vacía). */
    const val VAULT = "vault"

    const val ITEM_ID_ARG = "id"

    /** SCR-12 y SCR-13: el detalle de un elemento. */
    const val VAULT_ITEM = "vault_item/{$ITEM_ID_ARG}"

    fun vaultItem(id: UUID): String = "vault_item/$id"

    /** SCR-19: «Por resolver», la lista de contraseñas que cambiaron en dos dispositivos. */
    const val CONFLICTS = "conflicts"

    /** SCR-21: «Elegir versión» de un elemento en conflicto. */
    const val CONFLICT_ITEM = "conflict/{$ITEM_ID_ARG}"

    fun conflictItem(id: UUID): String = "conflict/$id"

    /** SCR-14: nueva contraseña (sin `id`) o editar una existente. */
    const val VAULT_EDIT = "vault_edit?$ITEM_ID_ARG={$ITEM_ID_ARG}"

    const val VAULT_NEW = "vault_edit"

    fun vaultEdit(id: UUID): String = "vault_edit?$ITEM_ID_ARG=$id"

    /**
     * Rutas con las que se puede abrir la app en depuración (`MainActivity`). Solo Desbloquear:
     * ninguna pantalla con datos de la bóveda debe abrirse sin pasar por el acceso.
     */
    val DEBUG_START_ROUTES = setOf(UNLOCK)

    /** SCR-06; el argumento es el correo al que se envió el código. */
    const val VERIFY_EMAIL = "verify_email/{${VerifyEmailViewModel.EMAIL_ARG}}"

    fun verifyEmail(email: String): String = "verify_email/${Uri.encode(email)}"

    /** Documento legal (PEND-12); el argumento es [LegalDocument.routeArg]. */
    const val LEGAL_DOCUMENT_ARG = "document"
    const val LEGAL_DOCUMENT = "legal_document/{$LEGAL_DOCUMENT_ARG}"

    fun legalDocument(document: LegalDocument): String = "legal_document/${document.routeArg}"
}
