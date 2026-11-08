package com.luis.end_security.feature.vault.data

import com.luis.end_security.core.common.DeviceClock
import com.luis.end_security.core.crypto.SecureBytes
import com.luis.end_security.core.database.entity.SyncState
import com.luis.end_security.core.database.entity.VaultItemEntity
import com.luis.end_security.core.session.VaultSession
import com.luis.end_security.core.sync.SyncTrigger
import com.luis.end_security.core.sync.UploadGuard
import com.luis.end_security.feature.vault.domain.DamagedEntry
import com.luis.end_security.feature.vault.domain.ItemDraft
import com.luis.end_security.feature.vault.domain.ReadableEntry
import com.luis.end_security.feature.vault.domain.SaveResult
import com.luis.end_security.feature.vault.domain.UnsupportedEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.UUID

class VaultRepositoryTest {

    private var now = 1_700_000_000_000L
    private val clock = object : DeviceClock {
        override fun wallMillis() = now
        override fun elapsedRealtimeMillis() = now
    }
    private val session = VaultSession(clock)
    private val dao = FakeVaultItemDao()
    private val uploads = object : UploadGuard {
        var uploading = false
        override fun isUploading(id: UUID) = uploading
    }
    private val triggers = object : SyncTrigger {
        var count = 0
        override fun changed() {
            count++
        }
    }
    private val repository = VaultRepositoryImpl(dao, session, clock, Dispatchers.Unconfined, uploads, triggers)
    private val userId = UUID.fromString("0b9f3c2e-5d1a-4f7e-9a2b-6c8d1e4f7a90")

    @Before
    fun setUp() {
        session.unlock(userId, SecureBytes.copyOf(ByteArray(32) { it.toByte() }))
    }

    @After
    fun tearDown() = session.lock()

    private fun draft(name: String = "Gmail", password: String = "Tortuga-29-Nubes", username: String = "ana@correo.com") =
        ItemDraft(name, username, password, url = "accounts.google.com", notes = null)

    private suspend fun created(d: ItemDraft = draft()): UUID = (repository.create(d) as SaveResult.Saved).id

    private suspend fun readable(id: UUID) = repository.entry(id) as ReadableEntry

    @Test
    fun `crear guarda un bloque cifrado pending y el elemento se lee de vuelta`() = runBlocking {
        val id = created()
        val row = dao.all.getValue(id.toString())
        assertEquals(SyncState.Pending, row.syncState)
        assertEquals(1L, row.localSeq)
        assertNull(row.serverVersion)
        val stored = (row.nonce!! + row.ciphertext!!).toString(Charsets.ISO_8859_1)
        assertFalse("nada en claro en Room", "Gmail" in stored || "Tortuga" in stored || "ana@correo" in stored)

        val entry = readable(id)
        assertEquals("Gmail", entry.item.name)
        assertEquals("Tortuga-29-Nubes", entry.item.password)
        assertEquals("created_at y password_changed_at nacen iguales", entry.item.createdAt, entry.item.passwordChangedAt)
        assertEquals(Instant.ofEpochMilli(now), entry.item.createdAt)
    }

    @Test
    fun `editar sin cambiar la contrasena conserva password_changed_at`() = runBlocking {
        val id = created()
        val before = readable(id)
        now += 86_400_000L

        val result = repository.update(id, before.localSeq, draft(name = "Gmail personal"))

        assertEquals(SaveResult.Saved(id), result)
        val after = readable(id)
        assertEquals("Gmail personal", after.item.name)
        assertEquals(before.item.passwordChangedAt, after.item.passwordChangedAt)
        assertEquals(before.item.createdAt, after.item.createdAt)
        assertEquals(2L, after.localSeq)
        assertEquals(SyncState.Pending, dao.all.getValue(id.toString()).syncState)
    }

    @Test
    fun `editar con otra contrasena actualiza password_changed_at`() = runBlocking {
        val id = created()
        val before = readable(id)
        now += 86_400_000L

        repository.update(id, before.localSeq, draft(password = "Otra-contrasena-31"))

        val after = readable(id)
        assertEquals(Instant.ofEpochMilli(now), after.item.passwordChangedAt)
        assertEquals(before.item.createdAt, after.item.createdAt)
    }

    @Test
    fun `editar con un local_seq viejo no cambia nada`() = runBlocking {
        val id = created()
        val result = repository.update(id, expectedLocalSeq = 7, draft = draft(name = "Otro"))
        assertEquals(SaveResult.Conflict, result)
        assertEquals("Gmail", readable(id).item.name)
        assertEquals(1L, readable(id).localSeq)
    }

    @Test
    fun `un elemento danado aparece como danado y no tumba la lista`() = runBlocking {
        val good = created(draft(name = "Amazon"))
        val bad = created(draft(name = "BBVA"))
        val row = dao.all.getValue(bad.toString())
        val tampered = row.ciphertext!!.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
        dao.put(VaultItemEntity(row.id, row.formatVersion, row.nonce, tampered, null, null, row.localSeq, row.syncState, null, row.updatedAt))

        val entries = repository.observeEntries().first()

        assertEquals(2, entries.size)
        assertTrue(entries.first { it.id == good } is ReadableEntry)
        assertTrue(entries.first { it.id == bad } is DamagedEntry)
    }

    @Test
    fun `un nonce corrupto aparece como danado y no tumba la lista`() = runBlocking {
        val good = created(draft(name = "Amazon"))
        val bad = created(draft(name = "BBVA"))
        val row = dao.all.getValue(bad.toString())
        dao.put(VaultItemEntity(row.id, row.formatVersion, ByteArray(5), row.ciphertext, null, null, row.localSeq, row.syncState, null, row.updatedAt))

        val entries = repository.observeEntries().first()

        assertTrue(entries.first { it.id == good } is ReadableEntry)
        assertTrue(entries.first { it.id == bad } is DamagedEntry)
    }

    @Test
    fun `observeEntry sigue solo a ese elemento, y se vacia al bloquear o borrar`() = runBlocking {
        val id = created()
        assertEquals("Gmail", (repository.observeEntry(id).first() as ReadableEntry).item.name)

        val other = created(draft(name = "Otro"))
        assertEquals("Gmail", (repository.observeEntry(id).first() as ReadableEntry).item.name)
        assertEquals("Otro", (repository.observeEntry(other).first() as ReadableEntry).item.name)

        repository.delete(other, 1)
        assertNull(repository.observeEntry(other).first())

        session.lock()
        assertNull(repository.observeEntry(id).first())
    }

    @Test
    fun `un elemento de otro usuario o de otra fila no abre porque la AAD no coincide`() = runBlocking {
        val id = created()
        val other = UUID.randomUUID()
        val row = dao.all.getValue(id.toString())
        // El mismo bloque bajo otro id: la AAD lleva el id, así que no abre.
        dao.put(VaultItemEntity(other.toString(), 1, row.nonce, row.ciphertext, null, null, 1, SyncState.Pending, null, row.updatedAt))
        assertTrue(repository.entry(other) is DamagedEntry)
    }

    @Test
    fun `un format_version mas nuevo se muestra en solo lectura`() = runBlocking {
        val id = created()
        val row = dao.all.getValue(id.toString())
        dao.put(VaultItemEntity(row.id, 2, row.nonce, row.ciphertext, null, null, row.localSeq, row.syncState, null, row.updatedAt))

        val entry = repository.entry(id)

        assertTrue(entry is UnsupportedEntry)
        assertEquals(2, (entry as UnsupportedEntry).formatVersion)
        assertEquals("nunca se reescribe con el formato viejo", SaveResult.NotFound, repository.update(id, 1, draft()))
    }

    @Test
    fun `borrar un elemento que nunca se subio borra la fila y uno subido deja lapida`() = runBlocking {
        val local = created(draft(name = "Local"))
        val synced = created(draft(name = "Subido"))
        val row = dao.all.getValue(synced.toString())
        dao.put(VaultItemEntity(row.id, 1, row.nonce, row.ciphertext, 4, 4, row.localSeq, SyncState.Synced, null, row.updatedAt))

        assertTrue(repository.delete(local, 1))
        assertTrue(repository.delete(synced, 1))

        assertNull(dao.all[local.toString()])
        val tombstone = dao.all.getValue(synced.toString())
        assertNull(tombstone.ciphertext)
        assertNotNull(tombstone.deletedAt)
        assertEquals(SyncState.Pending, tombstone.syncState)
        assertEquals("la lápida no se ve", emptyList<Any>(), repository.observeEntries().first())
    }

    @Test
    fun `con la boveda bloqueada no se entrega ni se guarda nada`() = runBlocking {
        val id = created()
        session.lock()

        assertEquals(emptyList<Any>(), repository.observeEntries().first())
        assertNull(repository.entry(id))
        assertEquals(SaveResult.Locked, repository.create(draft()))
        assertEquals(SaveResult.Locked, repository.update(id, 1, draft()))
    }

    @Test
    fun `nombre y notas se recortan y los vacios quedan como null`() = runBlocking {
        val id = created(ItemDraft("  Gmail  ", " ana ", "x", url = "  ", notes = "   "))
        val item = readable(id).item
        assertEquals("Gmail", item.name)
        assertEquals("ana", item.username)
        assertNull(item.url)
        assertNull(item.notes)
    }
}
