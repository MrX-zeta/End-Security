package com.luis.end_security.feature.vault.data

import com.luis.end_security.core.common.DeviceClock
import com.luis.end_security.core.crypto.SecureBytes
import com.luis.end_security.core.database.entity.SyncState
import com.luis.end_security.core.session.VaultSession
import com.luis.end_security.core.sync.SyncOutcome
import com.luis.end_security.core.sync.SyncTrigger
import com.luis.end_security.core.sync.SyncWorld
import com.luis.end_security.feature.vault.domain.ConflictLoad
import com.luis.end_security.feature.vault.domain.ItemDraft
import com.luis.end_security.feature.vault.domain.KeepVersion
import com.luis.end_security.feature.vault.domain.ReadableEntry
import com.luis.end_security.feature.vault.domain.ResolveResult
import com.luis.end_security.feature.vault.domain.SaveResult
import com.luis.end_security.feature.vault.domain.VersionSide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * Resolver conflictos (OP-46, DEC-57) con dos «teléfonos» reales (cifrado, Room en memoria y motor de
 * sincronización) contra el backend falso: el teléfono A resuelve; el B es «el otro dispositivo».
 */
class ConflictResolverTest {

    private val world = SyncWorld()
    private val clock = object : DeviceClock {
        override fun wallMillis() = world.clock
        override fun elapsedRealtimeMillis() = world.clock
    }
    private val userId = UUID.fromString("0b9f3c2e-5d1a-4f7e-9a2b-6c8d1e4f7a90")
    private val session = VaultSession(clock)
    private val a = world.firstDevice()
    private val b = world.secondDevice()
    private val triggers = object : SyncTrigger {
        var count = 0
        override fun changed() {
            count++
        }
    }
    private val repoA = VaultRepositoryImpl(a.dao, session, clock, Dispatchers.Unconfined, a.engine, triggers)
    private val repoB = VaultRepositoryImpl(b.dao, session, clock, Dispatchers.Unconfined, b.engine, triggers)
    private val resolver = ConflictResolver(a.dao, a.api, session, repoA, clock, triggers, Dispatchers.Unconfined)

    init {
        session.unlock(userId, SecureBytes.copyOf(ByteArray(32) { it.toByte() }))
    }

    @After
    fun tearDown() = session.lock()

    private fun draft(name: String, password: String = "Tortuga-29-Nubes", username: String = "ana@correo.com") =
        ItemDraft(name, username, password, url = "netflix.com", notes = null)

    private suspend fun read(repo: VaultRepositoryImpl, id: UUID) = repo.entry(id) as ReadableEntry

    private fun tick() {
        world.clock += 1_000
    }

    /** Los dos teléfonos tienen v1; el B edita y sube; el A edita lo mismo sin saberlo y sube: conflicto en A. */
    private fun editedOnBoth(local: ItemDraft, remote: ItemDraft): UUID = runBlocking {
        val id = (repoA.create(draft("Netflix")) as SaveResult.Saved).id
        assertEquals(SyncOutcome.Synced, a.sync())
        assertEquals(SyncOutcome.Synced, b.sync())
        tick()
        assertTrue(repoB.update(id, read(repoB, id).localSeq, remote) is SaveResult.Saved)
        assertEquals(SyncOutcome.Synced, b.sync())
        tick()
        assertTrue(repoA.update(id, read(repoA, id).localSeq, local) is SaveResult.Saved)
        a.sync()
        assertEquals("el servidor rechazó la subida del A", SyncState.Conflict, a.row(id)!!.syncState)
        id
    }

    private suspend fun ready(id: UUID) = (resolver.load(id) as ConflictLoad.Ready).detail

    @Test
    fun `la lista de conflictos trae el nombre local y se vacia al resolver`() = runBlocking {
        val id = editedOnBoth(draft("Netflix A", "clave-local-1"), draft("Netflix B", "clave-remota-2"))

        val rows = resolver.observeConflicts().first()
        assertEquals(1, rows.size)
        assertEquals(id, rows.single().id)
        assertEquals("Netflix A", rows.single().name)

        assertTrue(resolver.resolve(ready(id), KeepVersion.Remote) is ResolveResult.Resolved)
        assertTrue(resolver.observeConflicts().first().isEmpty())
    }

    @Test
    fun `cargar descifra las dos versiones en memoria`() = runBlocking {
        val id = editedOnBoth(draft("Netflix A", "clave-local-1"), draft("Netflix B", "clave-remota-2"))

        val detail = ready(id)
        val local = (detail.local as VersionSide.Live).item
        val remote = (detail.remote as VersionSide.Live).item
        assertEquals("Netflix A", local.name)
        assertEquals("clave-local-1", local.password)
        assertEquals("Netflix B", remote.name)
        assertEquals("clave-remota-2", remote.password)
        assertFalse("el detalle no imprime nada", detail.toString().contains("Netflix"))
    }

    @Test
    fun `elegir la del servidor reemplaza la fila local y queda synced`() = runBlocking {
        val id = editedOnBoth(draft("Netflix A", "clave-local-1"), draft("Netflix B", "clave-remota-2"))
        val serverVersion = world.serverItem(id)!!.version

        val result = resolver.resolve(ready(id), KeepVersion.Remote)

        assertEquals("Netflix B", (result as ResolveResult.Resolved).name)
        val row = a.row(id)!!
        assertEquals(SyncState.Synced, row.syncState)
        assertEquals(serverVersion, row.serverVersion)
        val entry = read(repoA, id)
        assertEquals("Netflix B", entry.item.name)
        assertEquals("clave-remota-2", entry.item.password)
        // Nada que subir: el servidor conserva su versión.
        assertEquals(SyncOutcome.Synced, a.sync())
        assertEquals(serverVersion, world.serverItem(id)!!.version)
        assertEquals("Netflix B", resolver.lastResolved.value)
    }

    @Test
    fun `elegir la local la vuelve a cifrar con nonce nuevo y queda pending sobre la version del servidor`() = runBlocking {
        val id = editedOnBoth(draft("Netflix A", "clave-local-1"), draft("Netflix B", "clave-remota-2"))
        val before = a.row(id)!!
        val serverVersion = world.serverItem(id)!!.version

        val result = resolver.resolve(ready(id), KeepVersion.Local)

        assertEquals("Netflix A", (result as ResolveResult.Resolved).name)
        val row = a.row(id)!!
        assertEquals(SyncState.Pending, row.syncState)
        assertEquals(serverVersion, row.serverVersion)
        assertFalse("nonce nuevo, nunca se reutiliza", before.nonce!!.contentEquals(row.nonce!!))
        assertTrue("se pidió sincronizar", triggers.count > 0)

        // Se sube y el otro dispositivo la recibe.
        assertEquals(SyncOutcome.Synced, a.sync())
        assertEquals(SyncState.Synced, a.row(id)!!.syncState)
        assertEquals(SyncOutcome.Synced, b.sync())
        val seenByB = read(repoB, id)
        assertEquals("Netflix A", seenByB.item.name)
        assertEquals("clave-local-1", seenByB.item.password)
    }

    @Test
    fun `lapida remota y conservar la mia crea un elemento nuevo y quita la fila vieja`() = runBlocking {
        val id = (repoA.create(draft("Netflix")) as SaveResult.Saved).id
        a.sync()
        b.sync()
        tick()
        // El otro dispositivo la elimina.
        assertTrue(repoB.delete(id, read(repoB, id).localSeq))
        b.sync()
        tick()
        // Aquí se edita sin saberlo.
        assertTrue(repoA.update(id, read(repoA, id).localSeq, draft("Netflix A", "clave-local-1")) is SaveResult.Saved)
        a.sync()
        assertEquals(SyncState.Conflict, a.row(id)!!.syncState)

        val detail = ready(id)
        assertTrue(detail.remote is VersionSide.Deleted)
        assertTrue(detail.local is VersionSide.Live)

        val result = resolver.resolve(detail, KeepVersion.Local)

        assertTrue(result is ResolveResult.Resolved)
        val rows = a.dao.all.values.filter { it.deletedAt == null }
        assertEquals("queda un solo elemento vivo", 1, rows.size)
        val fresh = rows.single()
        assertNotEquals("id nuevo: la AAD lleva el id", id.toString(), fresh.id)
        assertEquals(SyncState.Pending, fresh.syncState)
        val kept = read(repoA, UUID.fromString(fresh.id))
        assertEquals("Netflix A", kept.item.name)
        assertEquals("clave-local-1", kept.item.password)
        assertTrue("la fila vieja ya no está en conflicto", a.dao.all[id.toString()]?.syncState != SyncState.Conflict)
        assertTrue(resolver.observeConflicts().first().isEmpty())
    }

    @Test
    fun `lapida remota y eliminarla deja la lapida del servidor`() = runBlocking {
        val id = (repoA.create(draft("Netflix")) as SaveResult.Saved).id
        a.sync()
        b.sync()
        tick()
        repoB.delete(id, read(repoB, id).localSeq)
        b.sync()
        tick()
        repoA.update(id, read(repoA, id).localSeq, draft("Netflix A"))
        a.sync()

        val result = resolver.resolve(ready(id), KeepVersion.Remote)

        assertTrue(result is ResolveResult.Resolved)
        assertNull("ya no se ve", repoA.entry(id))
        assertEquals(SyncOutcome.Synced, a.sync())
        assertTrue(world.serverItem(id)!!.deleted)
    }

    @Test
    fun `aqui se elimino y alla cambio, conservar lo mio sube la eliminacion`() = runBlocking {
        val id = (repoA.create(draft("Netflix")) as SaveResult.Saved).id
        a.sync()
        b.sync()
        tick()
        repoB.update(id, read(repoB, id).localSeq, draft("Netflix B", "clave-remota-2"))
        b.sync()
        tick()
        assertTrue(repoA.delete(id, read(repoA, id).localSeq))
        a.sync()
        assertEquals(SyncState.Conflict, a.row(id)!!.syncState)

        val detail = ready(id)
        assertTrue(detail.local is VersionSide.Deleted)

        assertTrue(resolver.resolve(detail, KeepVersion.Local) is ResolveResult.Resolved)
        assertEquals(SyncOutcome.Synced, a.sync())
        assertTrue("la eliminación llegó al servidor", world.serverItem(id)!!.deleted)
    }

    @Test
    fun `si algo cambio mientras tanto la resolucion avisa que esta vieja`() = runBlocking {
        val id = editedOnBoth(draft("Netflix A", "clave-local-1"), draft("Netflix B", "clave-remota-2"))
        val detail = ready(id)

        assertTrue(resolver.resolve(detail, KeepVersion.Remote) is ResolveResult.Resolved)
        // La misma pantalla vieja intenta resolver otra vez.
        assertEquals(ResolveResult.Stale, resolver.resolve(detail, KeepVersion.Local))
    }

    @Test
    fun `si se edito mientras estaba abierta la pantalla, conservar la del servidor no pisa la edicion`() = runBlocking {
        val id = editedOnBoth(draft("Netflix A", "clave-local-1"), draft("Netflix B", "clave-remota-2"))
        val detail = ready(id)

        // La persona sale, edita el elemento (sigue en conflicto) y vuelve con la pantalla vieja.
        assertTrue(repoA.update(id, read(repoA, id).localSeq, draft("Netflix C", "clave-nueva-3")) is SaveResult.Saved)

        assertEquals(ResolveResult.Stale, resolver.resolve(detail, KeepVersion.Remote))
        assertEquals("Netflix C", read(repoA, id).item.name)
        assertEquals(SyncState.Conflict, a.row(id)!!.syncState)
    }

    @Test
    fun `sin red no se puede cargar y un elemento que no esta en conflicto ya no existe`() = runBlocking {
        val id = editedOnBoth(draft("Netflix A"), draft("Netflix B"))

        world.networkDown = true
        assertEquals(ConflictLoad.Offline, resolver.load(id))
        world.networkDown = false

        assertEquals(ConflictLoad.Gone, resolver.load(UUID.randomUUID()))
    }

    @Test
    fun `si el servidor ya no lo tiene se vuelve a crear solo`() = runBlocking {
        val id = editedOnBoth(draft("Netflix A"), draft("Netflix B"))
        world.state.items.remove(id.toString())

        assertEquals(ConflictLoad.Gone, resolver.load(id))

        assertEquals(SyncState.Pending, a.row(id)!!.syncState)
        assertNotNull(a.row(id))
    }

    @Test
    fun `con la boveda bloqueada no se carga ni se resuelve`() = runBlocking {
        val id = editedOnBoth(draft("Netflix A"), draft("Netflix B"))
        val detail = ready(id)

        session.lock()

        assertEquals(ConflictLoad.Locked, resolver.load(id))
        assertEquals(ResolveResult.Locked, resolver.resolve(detail, KeepVersion.Remote))
        assertTrue(resolver.observeConflicts().first().isEmpty())
    }
}
