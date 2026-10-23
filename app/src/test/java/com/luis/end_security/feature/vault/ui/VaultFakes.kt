package com.luis.end_security.feature.vault.ui

import com.luis.end_security.core.common.DeviceClock
import com.luis.end_security.core.crypto.SecureBytes
import com.luis.end_security.core.security.SecretClipboard
import com.luis.end_security.core.sync.SyncStatus
import com.luis.end_security.core.session.VaultSession
import com.luis.end_security.feature.vault.domain.ConflictDetail
import com.luis.end_security.feature.vault.domain.ConflictLoad
import com.luis.end_security.feature.vault.domain.ConflictRepository
import com.luis.end_security.feature.vault.domain.ConflictRow
import com.luis.end_security.feature.vault.domain.DeletionUndo
import com.luis.end_security.feature.vault.domain.KeepVersion
import com.luis.end_security.feature.vault.domain.ResolveResult
import com.luis.end_security.feature.vault.domain.ItemDraft
import com.luis.end_security.feature.vault.domain.PendingDeletion
import com.luis.end_security.feature.vault.domain.DamagedEntry
import com.luis.end_security.feature.vault.domain.ReadableEntry
import com.luis.end_security.feature.vault.domain.UnsupportedEntry
import com.luis.end_security.feature.vault.domain.VaultSummary
import com.luis.end_security.feature.vault.domain.SaveResult
import com.luis.end_security.feature.vault.domain.VaultEntry
import com.luis.end_security.feature.vault.domain.VaultItem
import com.luis.end_security.feature.vault.domain.VaultRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID

/** Reloj fijo, movible a mano. */
class TestClock(var now: Long = 1_700_000_000_000L) : DeviceClock {
    override fun wallMillis() = now

    override fun elapsedRealtimeMillis() = now
}

fun openedSession(clock: DeviceClock = TestClock()): VaultSession =
    VaultSession(clock).also {
        it.unlock(UUID.fromString("0b9f3c2e-5d1a-4f7e-9a2b-6c8d1e4f7a90"), SecureBytes.copyOf(ByteArray(32) { 1 }))
    }

fun item(
    name: String,
    username: String = "ana@correo.com",
    password: String = "Tortuga-29-Nubes",
    url: String? = null,
    notes: String? = null,
    changedAt: Instant = Instant.ofEpochMilli(1_700_000_000_000L),
) = VaultItem(name, username, password, url, notes, changedAt, changedAt)

fun readable(name: String, id: UUID = UUID.randomUUID(), localSeq: Long = 1, item: VaultItem = item(name)) =
    ReadableEntry(id, localSeq, item)

/** [VaultRepository] en memoria: la lista se cambia a mano y las escrituras se anotan. */
class FakeVaultRepository(initial: List<VaultEntry> = emptyList()) : VaultRepository {
    val entries = MutableStateFlow(initial)
    val created = mutableListOf<ItemDraft>()
    val updated = mutableListOf<Triple<UUID, Long, ItemDraft>>()
    var saveResult: SaveResult? = null

    override fun observeEntries(): Flow<List<VaultEntry>> = entries

    override suspend fun entry(id: UUID): VaultEntry? = entries.value.firstOrNull { it.id == id }

    /** Para la lista: lo mismo que las entradas, sin contraseña ni notas. */
    override fun observeSummaries(): Flow<List<VaultSummary>> = entries.map { list ->
        list.map { entry ->
            when (entry) {
                is ReadableEntry -> VaultSummary.Readable(entry.id, entry.localSeq, entry.item.name, entry.item.username, entry.item.url, false)
                is DamagedEntry -> VaultSummary.Damaged(entry.id, entry.localSeq)
                is UnsupportedEntry -> VaultSummary.Unsupported(entry.id, entry.localSeq)
            }
        }
    }

    val conflictIds = MutableStateFlow<List<UUID>>(emptyList())

    override fun observeConflictIds(): Flow<List<UUID>> = conflictIds

    override fun observeEntry(id: UUID): Flow<VaultEntry?> = entries.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun create(draft: ItemDraft): SaveResult {
        created += draft
        return saveResult ?: SaveResult.Saved(UUID.randomUUID())
    }

    override suspend fun update(id: UUID, expectedLocalSeq: Long, draft: ItemDraft): SaveResult {
        updated += Triple(id, expectedLocalSeq, draft)
        return saveResult ?: SaveResult.Saved(id)
    }

    override suspend fun delete(id: UUID, expectedLocalSeq: Long): Boolean = true
}

/** [SyncStatus] que se mueve a mano: «sin conexión» o no. */
class FakeSyncStatus : SyncStatus {
    override val offline = MutableStateFlow(false)
}

class FakeDeletionUndo : DeletionUndo {
    private val _pending = MutableStateFlow<PendingDeletion?>(null)
    override val pending: StateFlow<PendingDeletion?> = _pending.asStateFlow()
    val scheduled = mutableListOf<PendingDeletion>()
    var undone = 0

    override fun schedule(deletion: PendingDeletion) {
        scheduled += deletion
        _pending.value = deletion
    }

    override fun undo() {
        undone++
        _pending.value = null
    }

    override suspend fun flush() {
        _pending.value = null
    }
}

class FakeSecretClipboard(private val seconds: Int = 30) : SecretClipboard {
    val copied = mutableListOf<String>()

    override suspend fun copy(text: String): Int {
        copied += text
        return seconds
    }
}

/** [ConflictRepository] que se mueve a mano. */
class FakeConflictRepository : ConflictRepository {
    val conflicts = MutableStateFlow<List<ConflictRow>>(emptyList())
    var loadResult: ConflictLoad = ConflictLoad.Failed
    var resolveResult: ResolveResult = ResolveResult.Resolved("Netflix")
    val resolved = mutableListOf<Pair<ConflictDetail, KeepVersion>>()
    var loads = 0

    private val _lastResolved = MutableStateFlow<String?>(null)
    override val lastResolved: StateFlow<String?> = _lastResolved.asStateFlow()

    fun noticeResolved(name: String?) {
        _lastResolved.value = name
    }

    override fun observeConflicts(): Flow<List<ConflictRow>> = conflicts

    override suspend fun load(id: UUID): ConflictLoad {
        loads++
        return loadResult
    }

    override suspend fun resolve(detail: ConflictDetail, keep: KeepVersion): ResolveResult {
        resolved += detail to keep
        return resolveResult
    }

    override fun clearResolved() {
        _lastResolved.value = null
    }
}
