package com.luis.end_security.feature.vault.data

import com.luis.end_security.core.database.dao.AccountDao
import com.luis.end_security.core.database.dao.VaultItemDao
import com.luis.end_security.core.database.entity.AccountEntity
import com.luis.end_security.core.database.entity.SyncState
import com.luis.end_security.core.database.entity.VaultItemEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** [AccountDao] en memoria: una fila o ninguna, con el cursor de sincronización. */
class FakeAccountDao(initial: AccountEntity? = null) : AccountDao() {
    private val row = MutableStateFlow(initial)

    val current: AccountEntity? get() = row.value

    override suspend fun get(): AccountEntity? = row.value

    override fun observe(): Flow<AccountEntity?> = row

    override suspend fun exists(): Boolean = row.value != null

    override suspend fun deleteAll() {
        row.value = null
    }

    override suspend fun updateSyncCursor(cursor: Long, at: Long) {
        val old = row.value ?: return
        row.value = AccountEntity(
            old.userId, old.email, old.displayName, old.kdfSalt, old.kdfMemoryKiB, old.kdfIterations, old.kdfParallelism,
            old.pvkNonce, old.pvkCiphertext, old.keyVersion, cursor, at, old.bioIv, old.bioWrappedVaultKey,
        )
    }

    override suspend fun insert(account: AccountEntity) {
        row.value = account
    }

    override suspend fun deleteBreachChecks() = Unit

    override suspend fun deleteVaultItems() = Unit

    override suspend fun deleteHealthSnapshots() = Unit
}

/**
 * [VaultItemDao] en memoria con las mismas reglas que las consultas de Room (`local_seq` esperado, lápidas,
 * estados de sincronización). Con [account], el cursor se guarda en la «misma transacción» (aquí, el mismo paso).
 */
class FakeVaultItemDao(private val account: FakeAccountDao? = null) : VaultItemDao() {

    private val rows = MutableStateFlow<Map<String, VaultItemEntity>>(emptyMap())

    val all: Map<String, VaultItemEntity> get() = rows.value

    fun put(row: VaultItemEntity) {
        rows.value = rows.value + (row.id to row)
    }

    private fun change(id: String, transform: (VaultItemEntity) -> VaultItemEntity) {
        val old = rows.value[id] ?: return
        put(transform(old))
    }

    override fun observeVisible(): Flow<List<VaultItemEntity>> =
        rows.map { map -> map.values.filter { it.deletedAt == null }.sortedByDescending { it.updatedAt } }

    override suspend fun get(id: String): VaultItemEntity? = rows.value[id]

    override fun observe(id: String): Flow<VaultItemEntity?> = rows.map { map -> map[id]?.takeIf { it.deletedAt == null } }

    override suspend fun syncStateOf(id: String): SyncState? = rows.value[id]?.syncState

    override suspend fun upsertAll(items: List<VaultItemEntity>) {
        items.forEach { put(it) }
    }

    override suspend fun insert(item: VaultItemEntity) {
        require(item.id !in rows.value) { "ya existe" }
        put(item)
    }

    override suspend fun updateContent(
        id: String,
        nonce: ByteArray,
        ciphertext: ByteArray,
        expectedLocalSeq: Long,
        now: Long,
        formatVersion: Int,
    ): Int {
        val row = rows.value[id] ?: return 0
        if (row.localSeq != expectedLocalSeq || row.deletedAt != null || row.formatVersion != formatVersion) return 0
        put(
            VaultItemEntity(
                id, row.formatVersion, nonce, ciphertext, row.serverVersion, row.serverRevision, row.localSeq + 1,
                if (row.syncState == SyncState.Conflict) SyncState.Conflict else SyncState.Pending, null, now,
            ),
        )
        return 1
    }

    override suspend fun deletePhysically(id: String) {
        rows.value = rows.value - id
    }

    override suspend fun tombstone(id: String, now: Long) {
        val row = rows.value[id] ?: return
        put(
            VaultItemEntity(
                id, row.formatVersion, null, null, row.serverVersion, row.serverRevision, row.localSeq + 1,
                if (row.syncState == SyncState.Conflict) SyncState.Conflict else SyncState.Pending, now, now,
            ),
        )
    }

    // --- Sincronización ---

    override suspend fun updateSyncCursor(cursor: Long, at: Long) {
        account?.updateSyncCursor(cursor, at)
    }

    override suspend fun syncedIds(): List<String> = rows.value.values.filter { it.syncState == SyncState.Synced }.map { it.id }

    override suspend fun pendingRows(): List<VaultItemEntity> =
        rows.value.values.filter { it.syncState == SyncState.Pending }.sortedWith(compareBy({ it.localSeq }, { it.id }))

    override suspend fun conflictRows(): List<VaultItemEntity> =
        rows.value.values.filter { it.syncState == SyncState.Conflict }.sortedByDescending { it.updatedAt }

    override fun observeConflictIds(): Flow<List<String>> =
        rows.map { map -> map.values.filter { it.syncState == SyncState.Conflict }.map { it.id } }

    override fun observeConflicts(): Flow<List<VaultItemEntity>> =
        rows.map { map -> map.values.filter { it.syncState == SyncState.Conflict }.sortedByDescending { it.updatedAt } }

    override suspend fun reopenAsPending(id: String, expectedLocalSeq: Long): Int {
        val row = rows.value[id] ?: return 0
        if (row.localSeq != expectedLocalSeq || row.syncState != SyncState.Conflict) return 0
        put(VaultItemEntity(id, row.formatVersion, row.nonce, row.ciphertext, null, null, row.localSeq, SyncState.Pending, row.deletedAt, row.updatedAt))
        return 1
    }

    override suspend fun markUploaded(id: String, uploadedLocalSeq: Long, version: Long, revision: Long): Int {
        val row = rows.value[id] ?: return 0
        val synced = row.localSeq == uploadedLocalSeq && row.deletedAt == null && row.syncState == SyncState.Pending
        put(
            VaultItemEntity(
                id, row.formatVersion, row.nonce, row.ciphertext, version, revision, row.localSeq,
                if (synced) SyncState.Synced else row.syncState, row.deletedAt, row.updatedAt,
            ),
        )
        return 1
    }

    override suspend fun markConflict(id: String) = change(id) {
        VaultItemEntity(it.id, it.formatVersion, it.nonce, it.ciphertext, it.serverVersion, it.serverRevision, it.localSeq, SyncState.Conflict, it.deletedAt, it.updatedAt)
    }

    override suspend fun clearServerVersion(id: String) = change(id) {
        VaultItemEntity(it.id, it.formatVersion, it.nonce, it.ciphertext, null, null, it.localSeq, it.syncState, it.deletedAt, it.updatedAt)
    }

    override suspend fun removeTombstone(id: String) {
        if (rows.value[id]?.deletedAt != null) rows.value = rows.value - id
    }

    override suspend fun resolveKeepingLocal(
        id: String,
        nonce: ByteArray,
        ciphertext: ByteArray,
        serverVersion: Long,
        serverRevision: Long,
        expectedLocalSeq: Long,
        now: Long,
    ): Int {
        val row = rows.value[id] ?: return 0
        if (row.localSeq != expectedLocalSeq || row.syncState != SyncState.Conflict) return 0
        put(
            VaultItemEntity(id, row.formatVersion, nonce, ciphertext, serverVersion, serverRevision, row.localSeq + 1, SyncState.Pending, null, now),
        )
        return 1
    }

    override suspend fun resolveRekeep(id: String, serverVersion: Long, serverRevision: Long, expectedLocalSeq: Long, now: Long): Int {
        val row = rows.value[id] ?: return 0
        if (row.localSeq != expectedLocalSeq || row.syncState != SyncState.Conflict) return 0
        put(
            VaultItemEntity(
                id, row.formatVersion, row.nonce, row.ciphertext, serverVersion, serverRevision, row.localSeq + 1, SyncState.Pending, row.deletedAt, now,
            ),
        )
        return 1
    }
}
