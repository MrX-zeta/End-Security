package com.luis.end_security.feature.vault.data

import com.luis.end_security.core.common.DefaultDispatcher
import com.luis.end_security.core.common.DeviceClock
import com.luis.end_security.core.crypto.AuthenticationFailed
import com.luis.end_security.core.crypto.Sealed
import com.luis.end_security.core.database.dao.VaultItemDao
import com.luis.end_security.core.database.entity.SyncState
import com.luis.end_security.core.database.entity.VaultItemEntity
import com.luis.end_security.core.session.VaultLockedException
import com.luis.end_security.core.sync.SyncTrigger
import com.luis.end_security.core.sync.UploadGuard
import com.luis.end_security.core.session.VaultSession
import com.luis.end_security.core.session.VaultState
import com.luis.end_security.feature.vault.domain.DamagedEntry
import com.luis.end_security.feature.vault.domain.ItemDraft
import com.luis.end_security.feature.vault.domain.ReadableEntry
import com.luis.end_security.feature.vault.domain.SaveResult
import com.luis.end_security.feature.vault.domain.UnsupportedEntry
import com.luis.end_security.feature.vault.domain.VaultEntry
import com.luis.end_security.feature.vault.domain.VaultItem
import com.luis.end_security.feature.vault.domain.VaultRepository
import com.luis.end_security.feature.vault.domain.VaultSummary
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [VaultRepository] con Room y `VaultSession`. Lo que sale de aquí hacia Room son bloques
 * cifrados con [ItemCipher] (AAD = `id|user_id|format_version`); nombre, usuario, contraseña,
 * sitio y notas nunca se escriben en claro (regla no negociable 1).
 */
@Singleton
class VaultRepositoryImpl @Inject constructor(
    private val dao: VaultItemDao,
    private val session: VaultSession,
    private val clock: DeviceClock,
    @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    private val uploads: UploadGuard,
    private val syncTrigger: SyncTrigger,
) : VaultRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeEntries(): Flow<List<VaultEntry>> = session.state
        .flatMapLatest { state ->
            when (state) {
                VaultState.Locked -> flowOf(emptyList())
                is VaultState.Unlocked -> dao.observeVisible().map { rows -> decryptAll(rows, state.userId) }
            }
        }
        .flowOn(defaultDispatcher)

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeSummaries(): Flow<List<VaultSummary>> = session.state
        .flatMapLatest { state ->
            when (state) {
                VaultState.Locked -> flowOf(emptyList())
                is VaultState.Unlocked -> dao.observeVisible().map { rows -> summarizeAll(rows, state.userId) }
            }
        }
        .flowOn(defaultDispatcher)

    override fun observeConflictIds(): Flow<List<UUID>> =
        dao.observeConflictIds().map { ids -> ids.mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() } }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeEntry(id: UUID): Flow<VaultEntry?> = session.state
        .flatMapLatest { state ->
            when (state) {
                VaultState.Locked -> flowOf(null)
                is VaultState.Unlocked -> dao.observe(id.toString()).map { row ->
                    row?.let {
                        try {
                            toEntry(it, state.userId)
                        } catch (_: VaultLockedException) {
                            null
                        }
                    }
                }
            }
        }
        .flowOn(defaultDispatcher)

    override suspend fun entry(id: UUID): VaultEntry? = withContext(defaultDispatcher) {
        val userId = (session.state.value as? VaultState.Unlocked)?.userId ?: return@withContext null
        val row = dao.get(id.toString()) ?: return@withContext null
        if (row.deletedAt != null) return@withContext null
        try {
            toEntry(row, userId)
        } catch (_: VaultLockedException) {
            null
        }
    }

    override suspend fun create(draft: ItemDraft): SaveResult = withContext(defaultDispatcher) {
        val userId = (session.state.value as? VaultState.Unlocked)?.userId ?: return@withContext SaveResult.Locked
        val id = UUID.randomUUID()
        val now = Instant.ofEpochMilli(clock.wallMillis())
        // `created_at` y `password_changed_at` nacen iguales (DEC-04).
        val item = draft.toItem(createdAt = now, passwordChangedAt = now)
        val sealed = try {
            session.withVaultKey { key -> ItemCipher.encrypt(key, id, userId, item) }
        } catch (_: VaultLockedException) {
            return@withContext SaveResult.Locked
        }
        dao.insert(newRow(id, sealed, now))
        syncTrigger.changed()
        SaveResult.Saved(id)
    }

    override suspend fun update(id: UUID, expectedLocalSeq: Long, draft: ItemDraft): SaveResult =
        withContext(defaultDispatcher) {
            val userId = (session.state.value as? VaultState.Unlocked)?.userId ?: return@withContext SaveResult.Locked
            val row = dao.get(id.toString()) ?: return@withContext SaveResult.NotFound
            if (row.deletedAt != null) return@withContext SaveResult.NotFound
            if (row.localSeq != expectedLocalSeq) return@withContext SaveResult.Conflict
            try {
                val current = (toEntry(row, userId) as? ReadableEntry)?.item ?: return@withContext SaveResult.NotFound
                val now = Instant.ofEpochMilli(clock.wallMillis())
                // `password_changed_at` solo cambia si cambió la contraseña (DEC-04).
                val changedAt = if (current.password == draft.password) current.passwordChangedAt else now
                val item = draft.toItem(createdAt = current.createdAt, passwordChangedAt = changedAt)
                val sealed = session.withVaultKey { key -> ItemCipher.encrypt(key, id, userId, item) }
                val changed = dao.updateContent(
                    id.toString(), sealed.nonce, sealed.ciphertext, expectedLocalSeq, now.toEpochMilli(), row.formatVersion,
                )
                if (changed == 0) {
                    SaveResult.Conflict
                } else {
                    syncTrigger.changed()
                    SaveResult.Saved(id)
                }
            } catch (_: VaultLockedException) {
                SaveResult.Locked
            }
        }

    override suspend fun delete(id: UUID, expectedLocalSeq: Long): Boolean {
        // Con una subida en curso, la fila no se borra: queda lápida y el motor envía el DELETE al terminar.
        val deleted = dao.delete(id.toString(), expectedLocalSeq, clock.wallMillis(), uploadInFlight = uploads.isUploading(id))
        if (deleted) syncTrigger.changed()
        return deleted
    }

    // --- Ayudantes ---

    /** Con la bóveda bloqueada a la mitad, [VaultLockedException]: quien llama devuelve la lista vacía. */
    private fun decryptAll(rows: List<VaultItemEntity>, userId: UUID): List<VaultEntry> = try {
        rows.mapNotNull { toEntry(it, userId) }
    } catch (_: VaultLockedException) {
        emptyList()
    }

    private fun summarizeAll(rows: List<VaultItemEntity>, userId: UUID): List<VaultSummary> = try {
        rows.mapNotNull { toSummary(it, userId) }
    } catch (_: VaultLockedException) {
        emptyList()
    }

    private fun toSummary(row: VaultItemEntity, userId: UUID): VaultSummary? {
        val id = runCatching { UUID.fromString(row.id) }.getOrNull() ?: return null
        val nonce = row.nonce
        val ciphertext = row.ciphertext
        if (nonce == null || ciphertext == null) return null // lápida
        if (row.formatVersion > ItemCipher.FORMAT_VERSION) return VaultSummary.Unsupported(id, row.localSeq)
        return try {
            val summary = session.withVaultKey { key ->
                ItemCipher.decryptSummary(key, id, userId, row.formatVersion, Sealed(nonce, ciphertext))
            }
            VaultSummary.Readable(id, row.localSeq, summary.name, summary.username, summary.url, row.syncState == SyncState.Conflict)
        } catch (_: AuthenticationFailed) {
            VaultSummary.Damaged(id, row.localSeq)
        } catch (_: ItemFormatException) {
            VaultSummary.Damaged(id, row.localSeq)
        } catch (_: IllegalArgumentException) {
            VaultSummary.Damaged(id, row.localSeq)
        }
    }

    private fun toEntry(row: VaultItemEntity, userId: UUID): VaultEntry? {
        val id = runCatching { UUID.fromString(row.id) }.getOrNull() ?: return null
        val nonce = row.nonce
        val ciphertext = row.ciphertext
        if (nonce == null || ciphertext == null) return null // lápida
        // DEC-04: una versión de formato más nueva se muestra en solo lectura y nunca se reescribe.
        if (row.formatVersion > ItemCipher.FORMAT_VERSION) return UnsupportedEntry(id, row.localSeq, row.formatVersion)
        return try {
            val item = session.withVaultKey { key ->
                ItemCipher.decrypt(key, id, userId, row.formatVersion, Sealed(nonce, ciphertext))
            }
            ReadableEntry(id, row.localSeq, item)
        } catch (_: AuthenticationFailed) {
            DamagedEntry(id, row.localSeq)
        } catch (_: ItemFormatException) {
            DamagedEntry(id, row.localSeq)
        } catch (_: IllegalArgumentException) {
            // Un nonce que no mide 12 B (corrupción local) no debe tumbar la lista.
            DamagedEntry(id, row.localSeq)
        }
    }

    private fun newRow(id: UUID, sealed: Sealed, now: Instant) = VaultItemEntity(
        id = id.toString(),
        formatVersion = ItemCipher.FORMAT_VERSION,
        nonce = sealed.nonce,
        ciphertext = sealed.ciphertext,
        serverVersion = null,
        serverRevision = null,
        localSeq = 1,
        syncState = SyncState.Pending,
        deletedAt = null,
        updatedAt = now.toEpochMilli(),
    )

    private fun ItemDraft.toItem(createdAt: Instant, passwordChangedAt: Instant) = VaultItem(
        name = name.trim(),
        username = username.trim(),
        password = password,
        url = url?.trim()?.takeIf { it.isNotEmpty() },
        notes = notes?.trim()?.takeIf { it.isNotEmpty() },
        createdAt = createdAt,
        passwordChangedAt = passwordChangedAt,
    )
}
