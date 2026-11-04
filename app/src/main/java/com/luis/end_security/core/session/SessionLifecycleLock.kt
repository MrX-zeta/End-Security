package com.luis.end_security.core.session

import kotlinx.coroutines.sync.Mutex
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Exclusión mutua del ciclo de vida de la sesión (INV-07): `signOutLocally` (borra Room y bloquea) y toda apertura de
 * la bóveda (contraseña, huella, inicio de sesión) la toman, así que una apertura no puede colarse entre el borrado de
 * la cuenta y el bloqueo y dejar una `vault_key` viva de una cuenta que ya no existe. Solo cubre el paso corto de
 * «comprobar y abrir»; la derivación de Argon2 y el diálogo de la huella corren fuera.
 */
@Singleton
class SessionLifecycleLock @Inject constructor() {
    val mutex = Mutex()
}
