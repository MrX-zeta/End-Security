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
    const val GENERATED_ARG = "generated"

    const val VAULT_EDIT = "vault_edit?$ITEM_ID_ARG={$ITEM_ID_ARG}&$GENERATED_ARG={$GENERATED_ARG}"

    const val VAULT_NEW = "vault_edit"

    /** «Guardar en la bóveda» desde el Generador: Nueva contraseña con la generada (va por memoria, no por la ruta). */
    const val VAULT_NEW_GENERATED = "vault_edit?$GENERATED_ARG=true"

    /** SCR-15: el Generador. */
    const val GENERATOR = "generator"

    /** SCR-22, SCR-23 y SCR-40: Salud (Resumen | Progreso). */
    const val HEALTH = "health"

    /** SCR-29: Ajustes (pestaña). */
    const val SETTINGS = "settings"

    /** SCR-43, SCR-30…32: activar la verificación en dos pasos (un destino con los tres pasos). */
    const val TWO_FACTOR_SETUP = "settings_two_factor_setup"

    /** Verificación en dos pasos activa (Figma 218:4304). */
    const val TWO_FACTOR_ACTIVE = "settings_two_factor_active"

    /** SCR-35: Contraseña maestra (cambiarla, OP-27). */
    const val CHANGE_PASSWORD = "settings_change_password"

    /** SCR-33: Dispositivos con sesión abierta (OP-29, OP-30). */
    const val DEVICES = "settings_devices"

    /** SCR-34: Actividad reciente (OP-31). */
    const val ACTIVITY = "settings_activity"

    /** Resultado que el cambio de contraseña deja en Ajustes: [PASSWORD_CHANGED] o [PASSWORD_CHANGED_BIOMETRIC_OFF]. */
    const val RESULT_PASSWORD_CHANGED = "password_changed"
    const val PASSWORD_CHANGED = 1
    const val PASSWORD_CHANGED_BIOMETRIC_OFF = 2

    /** Resultado que «Desactivar» deja en la entrada de Ajustes: la pantalla muestra el aviso y lo consume. */
    const val RESULT_TWO_FACTOR_DISABLED = "two_factor_disabled"

    const val RANKING_TAB_ARG = "tab"

    /** SCR-24 y SCR-25: ranking de fuerza («Más fuertes» o «Más débiles», según `tab`). */
    const val HEALTH_RANKING = "health_ranking?$RANKING_TAB_ARG={$RANKING_TAB_ARG}"

    fun healthRanking(weakest: Boolean): String = "health_ranking?$RANKING_TAB_ARG=${if (weakest) "Weakest" else "Strongest"}"

    /** SCR-26, SCR-27 y SCR-28. */
    const val HEALTH_BREACHED = "health_breached"
    const val HEALTH_REPEATED = "health_repeated"
    const val HEALTH_OLD = "health_old"

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
