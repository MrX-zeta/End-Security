package com.luis.end_security.feature.vault.data

import com.luis.end_security.core.database.dao.VaultItemDao
import com.luis.end_security.core.database.entity.SyncState
import com.luis.end_security.core.database.entity.VaultItemEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** [VaultItemDao] en memoria con las mismas reglas que las consultas de Room (`local_seq` esperado, lápidas). */
class FakeVaultItemDao : VaultItemDao() {

    private val rows = MutableStateFlow<Map<String, VaultItemEntity>>(emptyMap())

    val all: Map<String, VaultItemEntity> get() = rows.value

    fun put(row: VaultItemEntity) {
        rows.value = rows.value + (row.id to row)
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
    ): Int {
        val row = rows.value[id] ?: return 0
        if (row.localSeq != expectedLocalSeq || row.deletedAt != null) return 0
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
}
