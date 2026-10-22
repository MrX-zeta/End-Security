package com.luis.end_security.feature.vault.data

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.luis.end_security.core.common.ApplicationScope
import com.luis.end_security.core.session.VaultSession
import com.luis.end_security.core.session.VaultState
import com.luis.end_security.feature.vault.domain.DeletionUndo
import com.luis.end_security.feature.vault.domain.PendingDeletion
import com.luis.end_security.feature.vault.domain.VaultRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * El «Deshacer» de un borrado (SCR-17, DEC-56). Mientras corren los 5 s, Room no se toca: la lista
 * solo oculta el elemento. Se escribe el borrado (fila o lápida) cuando:
 * - pasan 5 s;
 * - la bóveda se bloquea (la lista deja de verse y ya no habría quién pulse «Deshacer»);
 * - la app pasa a segundo plano (lo registra `EndSecurityApp` en `ProcessLifecycleOwner`);
 * - se programa otro borrado, que confirma el anterior.
 *
 * Vive en la aplicación, no en una pantalla: el borrado se pide desde el detalle y el aviso se ve
 * en la lista.
 */
@Singleton
class DeletionUndoController @Inject constructor(
    private val repository: VaultRepository,
    session: VaultSession,
    @param:ApplicationScope private val scope: CoroutineScope,
) : DeletionUndo, DefaultLifecycleObserver {

    private val lock = Any()
    private val _pending = MutableStateFlow<PendingDeletion?>(null)
    override val pending: StateFlow<PendingDeletion?> = _pending.asStateFlow()

    // Protegido por [lock].
    private var timer: Job? = null

    init {
        // Solo el paso de abierta a bloqueada: un Locked inicial no debe confirmar nada.
        var previous: VaultState = session.state.value
        scope.launch {
            session.state.collect { state ->
                if (previous is VaultState.Unlocked && state is VaultState.Locked) flush()
                previous = state
            }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        scope.launch { flush() }
    }

    override fun schedule(deletion: PendingDeletion) {
        val previous: PendingDeletion?
        synchronized(lock) {
            previous = _pending.value
            timer?.cancel()
            _pending.value = deletion
            timer = scope.launch {
                delay(DeletionUndo.UNDO_MILLIS)
                commit(deletion)
            }
        }
        // Uno nuevo confirma el anterior.
        if (previous != null && previous != deletion) scope.launch { commit(previous, clearPending = false) }
    }

    override fun undo() {
        synchronized(lock) {
            timer?.cancel()
            timer = null
            _pending.value = null
        }
    }

    override suspend fun flush() {
        val current = synchronized(lock) {
            timer?.cancel()
            timer = null
            _pending.value
        } ?: return
        commit(current)
    }

    /** Escribe el borrado sin cancelarse a la mitad; después, quita el aviso si sigue siendo este. */
    private suspend fun commit(deletion: PendingDeletion, clearPending: Boolean = true) {
        try {
            // Si devuelve `false` (el `local_seq` cambió) el elemento sigue en la lista: el aviso se quita igual.
            withContext(NonCancellable) { repository.delete(deletion.id, deletion.localSeq) }
        } catch (_: Exception) {
            // Un fallo de Room no debe cerrar la app ni dejar el aviso (con el nombre) para siempre.
        } finally {
            if (clearPending) {
                synchronized(lock) {
                    if (_pending.value == deletion) {
                        _pending.value = null
                        timer = null
                    }
                }
            }
        }
    }
}
