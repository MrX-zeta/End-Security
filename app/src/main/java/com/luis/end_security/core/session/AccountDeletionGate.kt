package com.luis.end_security.core.session

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Eliminar la cuenta (tarea 20). Tras el 204 de EP-18, la limpieza local bloquea la bóveda y la pantalla de Eliminar (que está
 * dentro de `RequireUnlocked`) sale de la composición **antes** de poder navegar. Por eso la navegación a «Cuenta eliminada» no
 * depende de esa pantalla:
 * - [begin] (justo después del 204, antes de la limpieza): `LockGuard` ignora el bloqueo que provoca la propia limpieza;
 * - [finish] (la limpieza terminó, bien o a medias): avisa por [deleted] y `AppNavHost` navega a «Cuenta eliminada» con la pila
 *   limpia;
 * - [end] (ya se navegó): `LockGuard` vuelve a atender los bloqueos.
 * Solo vive en memoria y no guarda datos.
 */
@Singleton
class AccountDeletionGate @Inject constructor() {

    @Volatile
    var active: Boolean = false
        private set

    private val events = Channel<Unit>(Channel.CONFLATED)

    /** Una vez por cuenta eliminada: la limpieza terminó. */
    val deleted: Flow<Unit> = events.receiveAsFlow()

    fun begin() {
        active = true
    }

    fun finish() {
        events.trySend(Unit)
    }

    fun end() {
        active = false
    }
}
