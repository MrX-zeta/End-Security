package com.luis.end_security.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.luis.end_security.core.database.entity.AccountEntity
import com.luis.end_security.core.database.entity.BreachCheckEntity
import com.luis.end_security.core.database.entity.HealthSnapshotEntity
import com.luis.end_security.core.database.entity.SyncState
import com.luis.end_security.core.database.entity.VaultItemEntity
import kotlinx.coroutines.flow.Flow

/** La cuenta de este teléfono (OP-19 la lee para desbloquear sin red). Siempre una fila o ninguna. */
@Dao
abstract class AccountDao {

    @Query("SELECT * FROM account LIMIT 1")
    abstract suspend fun get(): AccountEntity?

    @Query("SELECT * FROM account LIMIT 1")
    abstract fun observe(): Flow<AccountEntity?>

    @Query("SELECT EXISTS(SELECT 1 FROM account)")
    abstract suspend fun exists(): Boolean

    /**
     * Reemplaza la cuenta: nunca quedan dos filas. Si es otra cuenta, también se borran los datos
     * de la anterior (elementos, caché de HIBP y salud), para no mezclar bloques de dos usuarios.
     */
    @Transaction
    open suspend fun replace(account: AccountEntity) {
        val previous = get()
        if (previous != null && previous.userId != account.userId) {
            deleteBreachChecks()
            deleteVaultItems()
            deleteHealthSnapshots()
        }
        deleteAll()
        insert(account)
    }

    @Query("DELETE FROM breach_checks")
    protected abstract suspend fun deleteBreachChecks()

    @Query("DELETE FROM vault_items")
    protected abstract suspend fun deleteVaultItems()

    @Query("DELETE FROM health_snapshots")
    protected abstract suspend fun deleteHealthSnapshots()

    @Query("DELETE FROM account")
    abstract suspend fun deleteAll()

    /** Cursor de sincronización tras aplicar cambios del servidor (OP-32/OP-33). */
    @Query("UPDATE account SET sync_cursor = :cursor, last_sync_at = :at")
    abstract suspend fun updateSyncCursor(cursor: Long, at: Long)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insert(account: AccountEntity)
}

/**
 * Elementos cifrados (OP-01…OP-07; ciclo del modelo de datos §8.2). Las escrituras llevan el
 * `local_seq` esperado (regla 8): si otra escritura se adelantó, no se aplican y devuelven 0/false.
 */
@Dao
abstract class VaultItemDao {

    /** Los que se ven en la Bóveda: sin lápidas. Se descifran en memoria (OP-04, OP-07). */
    @Query("SELECT * FROM vault_items WHERE deleted_at IS NULL ORDER BY updated_at DESC")
    abstract fun observeVisible(): Flow<List<VaultItemEntity>>

    @Query("SELECT * FROM vault_items WHERE id = :id")
    abstract suspend fun get(id: String): VaultItemEntity?

    /** Un solo elemento y sus cambios: el detalle no necesita descifrar toda la bóveda. */
    @Query("SELECT * FROM vault_items WHERE id = :id AND deleted_at IS NULL")
    abstract fun observe(id: String): Flow<VaultItemEntity?>

    /**
     * Aplica una página de `GET /vault/items` (OP-32/OP-33) y guarda el cursor **en la misma transacción**
     * (modelo de datos §8.2): o quedan filas y cursor, o ninguno. Por cada cambio remoto:
     *
     * | Fila local | Cambio remoto |
     * |---|---|
     * | no existe | se guarda `synced`; una lápida no se guarda |
     * | ya conoce esa versión (`server_version >= version`) | se ignora (es el eco de su propia subida) |
     * | `synced` | se reemplaza, o se borra si es lápida |
     * | `pending` o `conflict` | queda `conflict`; si las dos lados son lápida, se borra la fila |
     *
     * `local_seq` solo sube al reemplazar: una edición abierta con el valor anterior ya no pasa (regla 8, INV-27).
     *
     * [keepOnly] (descarga completa tras `410`): las filas `synced` que el servidor ya no tiene se borran;
     * `pending` y `conflict` no se pierden.
     */
    @Transaction
    open suspend fun applyRemotePage(changes: List<RemoteRow>, cursor: Long, now: Long, keepOnly: Set<String>? = null) {
        for (remote in changes) {
            val local = get(remote.id)
            when {
                local == null -> if (!remote.deleted) upsertAll(listOf(remote.toEntity(localSeq = 0)))
                local.serverVersion != null && remote.version <= local.serverVersion -> Unit
                local.syncState == SyncState.Synced ->
                    if (remote.deleted) deletePhysically(remote.id) else upsertAll(listOf(remote.toEntity(localSeq = local.localSeq + 1)))
                // Lo local también está borrado (lápida pendiente) y el servidor también: no hay nada que resolver.
                local.deletedAt != null && remote.deleted -> deletePhysically(remote.id)
                // Mismo bloque (el nonce es aleatorio): es el eco de nuestra propia subida, que el servidor aceptó pero
                // cuya respuesta no llegó. No es un conflicto; queda `synced` con la versión del servidor.
                local.deletedAt == null && !remote.deleted && sameBlock(local, remote) ->
                    upsertAll(listOf(remote.toEntity(localSeq = local.localSeq)))
                else -> markConflict(remote.id)
            }
        }
        updateSyncCursor(cursor, now)
        keepOnly?.let { keep -> syncedIds().filter { it !in keep }.forEach { deletePhysically(it) } }
    }

    private fun sameBlock(local: VaultItemEntity, remote: RemoteRow): Boolean {
        val nonce = local.nonce
        val ciphertext = local.ciphertext
        return nonce != null && ciphertext != null && local.formatVersion == remote.formatVersion &&
            nonce.contentEquals(remote.nonce) && ciphertext.contentEquals(remote.ciphertext)
    }

    /** Cursor y hora de la última sincronización (en la misma tabla `account`, dentro de la transacción de arriba). */
    @Query("UPDATE account SET sync_cursor = :cursor, last_sync_at = :at")
    protected abstract suspend fun updateSyncCursor(cursor: Long, at: Long)

    @Query("SELECT id FROM vault_items WHERE sync_state = 'synced'")
    protected abstract suspend fun syncedIds(): List<String>

    // --- Subida (OP-34) ---

    /** Los pendientes, en orden de `local_seq` (de uno en uno). Las lápidas pendientes incluidas. */
    @Query("SELECT * FROM vault_items WHERE sync_state = 'pending' ORDER BY local_seq, id")
    abstract suspend fun pendingRows(): List<VaultItemEntity>

    /** Los que esperan que la persona elija una versión (SCR-18…21). */
    @Query("SELECT * FROM vault_items WHERE sync_state = 'conflict' ORDER BY updated_at DESC")
    abstract suspend fun conflictRows(): List<VaultItemEntity>

    @Query("SELECT id FROM vault_items WHERE sync_state = 'conflict'")
    abstract fun observeConflictIds(): Flow<List<String>>

    /** Los conflictos con su bloque (lápidas incluidas), para «Por resolver». */
    @Query("SELECT * FROM vault_items WHERE sync_state = 'conflict' ORDER BY updated_at DESC")
    abstract fun observeConflicts(): Flow<List<VaultItemEntity>>

    /** El servidor ya no tiene el elemento: deja de ser conflicto y se vuelve a crear (`pending`, sin versión). */
    @Query(
        """
        UPDATE vault_items SET sync_state = 'pending', server_version = NULL, server_revision = NULL
        WHERE id = :id AND local_seq = :expectedLocalSeq AND sync_state = 'conflict'
        """,
    )
    abstract suspend fun reopenAsPending(id: String, expectedLocalSeq: Long): Int

    /**
     * Respuesta `created`/`updated` (§8.2): guarda la versión del servidor y, **solo si el `local_seq` no
     * cambió durante la subida** y el elemento no se borró, queda `synced`; si no, sigue `pending`.
     */
    @Query(
        """
        UPDATE vault_items
        SET server_version = :version, server_revision = :revision,
            sync_state = CASE WHEN local_seq = :uploadedLocalSeq AND deleted_at IS NULL AND sync_state = 'pending'
                              THEN 'synced' ELSE sync_state END
        WHERE id = :id
        """,
    )
    abstract suspend fun markUploaded(id: String, uploadedLocalSeq: Long, version: Long, revision: Long): Int

    /** Respuesta `409` (§8.2). */
    @Query("UPDATE vault_items SET sync_state = 'conflict' WHERE id = :id")
    abstract suspend fun markConflict(id: String)

    /** Respuesta `404` al editar (§8.2): se purgó en el servidor; se vuelve a crear. */
    @Query("UPDATE vault_items SET server_version = NULL, server_revision = NULL WHERE id = :id")
    abstract suspend fun clearServerVersion(id: String)

    /** Respuesta `deleted` (§8.2): la lápida ya llegó al servidor; se borra la fila. */
    @Query("DELETE FROM vault_items WHERE id = :id AND deleted_at IS NOT NULL")
    abstract suspend fun removeTombstone(id: String)

    // --- Resolver conflictos (OP-46, DEC-57) ---

    /**
     * Elige la versión del servidor: la fila local se reemplaza y queda `synced`. `local_seq` sube.
     * `false` si el `local_seq` ya no coincide (se editó mientras tanto): no se pisa una edición reciente.
     */
    @Transaction
    open suspend fun resolveWithRemote(remote: RemoteRow, expectedLocalSeq: Long): Boolean {
        val local = get(remote.id) ?: return false
        if (local.syncState != SyncState.Conflict || local.localSeq != expectedLocalSeq) return false
        if (remote.deleted) deletePhysically(remote.id) else upsertAll(listOf(remote.toEntity(localSeq = local.localSeq + 1)))
        return true
    }

    /**
     * Elige la versión de este teléfono: el bloque nuevo (nonce nuevo) se guarda con la `server_version` del
     * servidor y queda `pending` para subirse. `false` si el `local_seq` ya no coincide (se editó mientras tanto).
     */
    @Query(
        """
        UPDATE vault_items
        SET nonce = :nonce, ciphertext = :ciphertext, server_version = :serverVersion, server_revision = :serverRevision,
            local_seq = local_seq + 1, updated_at = :now, sync_state = 'pending'
        WHERE id = :id AND local_seq = :expectedLocalSeq AND sync_state = 'conflict'
        """,
    )
    abstract suspend fun resolveKeepingLocal(
        id: String,
        nonce: ByteArray,
        ciphertext: ByteArray,
        serverVersion: Long,
        serverRevision: Long,
        expectedLocalSeq: Long,
        now: Long,
    ): Int

    /**
     * Conservar la mía cuando la del servidor es una lápida (o cuando la mía es la lápida): la lápida local
     * queda `pending` con la `server_version` que dice el servidor, para que el `DELETE` pase.
     */
    @Query(
        """
        UPDATE vault_items
        SET server_version = :serverVersion, server_revision = :serverRevision, local_seq = local_seq + 1,
            updated_at = :now, sync_state = 'pending'
        WHERE id = :id AND local_seq = :expectedLocalSeq AND sync_state = 'conflict'
        """,
    )
    abstract suspend fun resolveRekeep(id: String, serverVersion: Long, serverRevision: Long, expectedLocalSeq: Long, now: Long): Int

    @Query("SELECT sync_state FROM vault_items WHERE id = :id")
    protected abstract suspend fun syncStateOf(id: String): SyncState?

    @Upsert
    protected abstract suspend fun upsertAll(items: List<VaultItemEntity>)

    /** Crear (OP-01): la fila llega como `pending`, sin `server_version` y con `local_seq` 1. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insert(item: VaultItemEntity)

    /**
     * Editar (OP-02): bloque nuevo, `local_seq + 1` y `pending` (un `conflict` se queda en
     * `conflict`). Devuelve las filas cambiadas: 0 si el `local_seq` esperado no coincide o si la fila es de otra
     * `format_version` (defensa en profundidad: una versión más nueva nunca se reescribe con el formato viejo).
     */
    @Query(
        """
        UPDATE vault_items
        SET nonce = :nonce, ciphertext = :ciphertext, local_seq = local_seq + 1, updated_at = :now,
            sync_state = CASE WHEN sync_state = 'conflict' THEN 'conflict' ELSE 'pending' END
        WHERE id = :id AND local_seq = :expectedLocalSeq AND deleted_at IS NULL AND format_version = :formatVersion
        """,
    )
    abstract suspend fun updateContent(
        id: String,
        nonce: ByteArray,
        ciphertext: ByteArray,
        expectedLocalSeq: Long,
        now: Long,
        formatVersion: Int,
    ): Int

    /**
     * Borrar (OP-03): si nunca se subió, se borra la fila; si ya está en el servidor, queda una
     * lápida (`nonce`/`ciphertext` en `null`, `pending`) para subir el borrado. `false` si el
     * elemento no existe o el `local_seq` esperado no coincide. [uploadInFlight]: el motor de sincronización
     * lo está subiendo ahora mismo.
     */
    @Transaction
    open suspend fun delete(id: String, expectedLocalSeq: Long, now: Long, uploadInFlight: Boolean = false): Boolean {
        val item = get(id) ?: return false
        if (item.localSeq != expectedLocalSeq || item.deletedAt != null) return false
        // Con una subida en curso no se borra la fila: el servidor va a recibirlo, así que queda lápida
        // `pending` y al terminar la subida se envía el DELETE (tarea 12, borrar mientras se sube).
        if (item.serverVersion == null && !uploadInFlight) {
            deletePhysically(id)
        } else {
            tombstone(id, now)
        }
        return true
    }

    @Query("DELETE FROM vault_items WHERE id = :id")
    protected abstract suspend fun deletePhysically(id: String)

    @Query(
        """
        UPDATE vault_items
        SET nonce = NULL, ciphertext = NULL, deleted_at = :now, updated_at = :now, local_seq = local_seq + 1,
            sync_state = CASE WHEN sync_state = 'conflict' THEN 'conflict' ELSE 'pending' END
        WHERE id = :id
        """,
    )
    protected abstract suspend fun tombstone(id: String, now: Long)
}

/** Caché cifrada de HIBP (TBL-L-BREACH). */
@Dao
abstract class BreachCheckDao {

    @Upsert
    abstract suspend fun upsert(check: BreachCheckEntity)

    @Query("SELECT * FROM breach_checks WHERE item_id = :itemId")
    abstract suspend fun get(itemId: String): BreachCheckEntity?
}

/** Historial cifrado de la salud (TBL-L-HEALTH). */
@Dao
abstract class HealthSnapshotDao {

    /** Si ya hay foto de ese día, se reemplaza. */
    @Upsert
    abstract suspend fun upsert(snapshot: HealthSnapshotEntity)

    @Query("SELECT * FROM health_snapshots ORDER BY day")
    abstract fun observeAll(): Flow<List<HealthSnapshotEntity>>

    /** Conserva 12 meses: borra los días anteriores a [firstDayToKeep] (`AAAA-MM-DD`). */
    @Query("DELETE FROM health_snapshots WHERE day < :firstDayToKeep")
    abstract suspend fun deleteBefore(firstDayToKeep: String)
}

/**
 * Un cambio que viene del servidor (`VaultItem` del contrato) tal como lo necesita Room: solo el bloque cifrado.
 * Una lápida lleva `nonce` y `ciphertext` en `null`.
 */
class RemoteRow(
    val id: String,
    val version: Long,
    val revision: Long,
    val formatVersion: Int,
    val nonce: ByteArray?,
    val ciphertext: ByteArray?,
    val deleted: Boolean,
    val updatedAt: Long,
) {
    fun toEntity(localSeq: Long) = VaultItemEntity(
        id = id,
        formatVersion = formatVersion,
        nonce = nonce,
        ciphertext = ciphertext,
        serverVersion = version,
        serverRevision = revision,
        localSeq = localSeq,
        syncState = SyncState.Synced,
        deletedAt = if (deleted) updatedAt else null,
        updatedAt = updatedAt,
    )

    override fun toString(): String = "RemoteRow(id=$id, version=$version, deleted=$deleted)"
}
