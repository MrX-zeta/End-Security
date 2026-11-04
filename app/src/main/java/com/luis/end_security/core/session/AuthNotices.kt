package com.luis.end_security.core.session

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Avisos de un solo uso para Iniciar sesión tras un cierre de sesión local que el usuario no pidió. Solo viven en
 * memoria y no llevan datos de la cuenta.
 */
@Singleton
class AuthNotices @Inject constructor() {

    @Volatile
    private var passwordChanged = false

    /** Cambiaste la contraseña y no se pudo guardar en este teléfono: «Tu contraseña cambió. Entra con la nueva.» */
    fun postPasswordChanged() {
        passwordChanged = true
    }

    /** `true` una sola vez después de [postPasswordChanged]. */
    fun consumePasswordChanged(): Boolean {
        val pending = passwordChanged
        passwordChanged = false
        return pending
    }
}
