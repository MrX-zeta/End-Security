package com.luis.end_security.feature.vault.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

/**
 * Los elementos de la bóveda en este teléfono (OP-01…OP-07). Descifra con la `vault_key` de
 * `VaultSession` y cifra al guardar; con la bóveda bloqueada no entrega nada. Todo queda en Room
 * como `pending`: subirlo es de la sincronización (OP-33), que llega después.
 */
interface VaultRepository {
    /**
     * OP-04/OP-07: los elementos visibles (sin lápidas), descifrados en memoria fuera del hilo
     * principal. Con la bóveda bloqueada emite una lista vacía.
     */
    fun observeEntries(): Flow<List<VaultEntry>>

    /** OP-05: un elemento, o `null` si no existe o la bóveda está bloqueada. */
    suspend fun entry(id: UUID): VaultEntry?

    /** El elemento [id] y sus cambios; descifra solo ese. `null` si no existe, se borró o la bóveda se bloquea. */
    fun observeEntry(id: UUID): Flow<VaultEntry?>

    /** OP-01: genera el id, cifra y guarda como `pending`. */
    suspend fun create(draft: ItemDraft): SaveResult

    /**
     * OP-02: cifra de nuevo con el [expectedLocalSeq] que se leyó. `password_changed_at` solo cambia
     * si cambió la contraseña (DEC-04).
     */
    suspend fun update(id: UUID, expectedLocalSeq: Long, draft: ItemDraft): SaveResult

    /** OP-03: borra la fila o deja la lápida `pending`. `false` si el `local_seq` ya no coincide. */
    suspend fun delete(id: UUID, expectedLocalSeq: Long): Boolean
}

/**
 * «Deshacer» de un borrado (SCR-17, DEC-56). El borrado no se escribe hasta pasados 5 s, hasta
 * que la bóveda se bloquea o hasta que la app pasa a segundo plano. Solo hay uno pendiente: uno
 * nuevo confirma el anterior.
 */
interface DeletionUndo {
    val pending: StateFlow<PendingDeletion?>

    fun schedule(deletion: PendingDeletion)

    /** Cancela el borrado pendiente sin tocar Room. */
    fun undo()

    /** Escribe ya el borrado pendiente, si lo hay. */
    suspend fun flush()

    companion object {
        const val UNDO_MILLIS = 5_000L
    }
}
