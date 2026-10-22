package com.luis.end_security.feature.vault.ui

import com.luis.end_security.core.common.DeviceClock
import com.luis.end_security.core.crypto.SecureBytes
import com.luis.end_security.core.security.SecretClipboard
import com.luis.end_security.core.session.VaultSession
import com.luis.end_security.feature.vault.domain.DeletionUndo
import com.luis.end_security.feature.vault.domain.ItemDraft
import com.luis.end_security.feature.vault.domain.PendingDeletion
import com.luis.end_security.feature.vault.domain.ReadableEntry
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
