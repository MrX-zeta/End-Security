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
     * Elementos que llegan del servidor en la descarga inicial: quedan `synced` con su versión y
     * revisión. Las lápidas se borran de Room (modelo de datos §8.2, «cambio remoto»).
     */
    @Transaction
    open suspend fun applyRemote(items: List<VaultItemEntity>, tombstoneIds: List<String>) {
        // Una fila `pending` o `conflict` tiene cambios sin subir: no se pisa ni se borra. Marcar
        // el conflicto y resolverlo con la vault_key (ConflictResolver) toca a OP-33.
        upsertAll(items.filter { isSyncedOrAbsent(it.id) }.map { remote ->
            // `local_seq` solo sube: si una pantalla abrió la fila con un valor, el reemplazo remoto lo
            // invalida (regla 8, INV-27); con 0 otra edición pasaría la comprobación sin ver el cambio.
            val previous = get(remote.id)?.localSeq ?: -1L
            VaultItemEntity(
                remote.id, remote.formatVersion, remote.nonce, remote.ciphertext, remote.serverVersion,
                remote.serverRevision, previous + 1, remote.syncState, remote.deletedAt, remote.updatedAt,
            )
        })
        tombstoneIds.filter { isSyncedOrAbsent(it) }.forEach { deletePhysically(it) }
    }

    private suspend fun isSyncedOrAbsent(id: String): Boolean = syncStateOf(id).let { it == null || it == SyncState.Synced }

    @Query("SELECT sync_state FROM vault_items WHERE id = :id")
    protected abstract suspend fun syncStateOf(id: String): SyncState?

    @Upsert
    protected abstract suspend fun upsertAll(items: List<VaultItemEntity>)

    /** Crear (OP-01): la fila llega como `pending`, sin `server_version` y con `local_seq` 1. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insert(item: VaultItemEntity)

    /**
     * Editar (OP-02): bloque nuevo, `local_seq + 1` y `pending` (un `conflict` se queda en
     * `conflict`). Devuelve las filas cambiadas: 0 si el `local_seq` esperado no coincide.
     */
    @Query(
        """
        UPDATE vault_items
        SET nonce = :nonce, ciphertext = :ciphertext, local_seq = local_seq + 1, updated_at = :now,
            sync_state = CASE WHEN sync_state = 'conflict' THEN 'conflict' ELSE 'pending' END
        WHERE id = :id AND local_seq = :expectedLocalSeq AND deleted_at IS NULL
        """,
    )
    abstract suspend fun updateContent(id: String, nonce: ByteArray, ciphertext: ByteArray, expectedLocalSeq: Long, now: Long): Int

    /**
     * Borrar (OP-03): si nunca se subió, se borra la fila; si ya está en el servidor, queda una
     * lápida (`nonce`/`ciphertext` en `null`, `pending`) para subir el borrado. `false` si el
     * elemento no existe o el `local_seq` esperado no coincide.
     */
    @Transaction
    open suspend fun delete(id: String, expectedLocalSeq: Long, now: Long): Boolean {
        val item = get(id) ?: return false
        if (item.localSeq != expectedLocalSeq || item.deletedAt != null) return false
        if (item.serverVersion == null) {
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
