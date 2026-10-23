package com.luis.end_security.core.sync

import com.luis.end_security.core.database.entity.SyncState
import com.luis.end_security.core.network.HttpResponse
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/** Cada fila de la tabla de `docs/bd/modelo-datos.md` §8.2, con el motor real contra el backend falso. */
class SyncEngineTest {

    private val world = SyncWorld()
    private val phone = world.firstDevice()

    /** El servidor rechaza la subida (`validation_failed`): el motor la salta y sigue con la descarga. */
    private val rejectUpload = HttpResponse(400, """{"type":"about:blank","title":"x","status":400,"code":"validation_failed"}""")

    @Test
    fun `crear y subir deja el elemento synced con la version del servidor`() {
        val id = phone.createLocal()

        assertEquals(SyncOutcome.Synced, phone.sync())

        val row = phone.row(id)!!
        assertEquals(SyncState.Synced, row.syncState)
        assertEquals(1L, row.serverVersion)
        assertNotNull(world.serverItem(id))
        assertEquals("el eco de su propia subida no es un conflicto", 0, runBlocking { phone.dao.conflictRows() }.size)
    }

    @Test
    fun `si el servidor acepto la subida pero la respuesta se perdio, el eco no es un conflicto`() {
        val id = phone.createLocal()
        // El servidor procesa el PUT y la respuesta nunca llega.
        world.afterServer = { request ->
            if (request.method == "PUT") {
                world.afterServer = null
                throw java.io.IOException("respuesta perdida")
            }
        }
        assertEquals(SyncOutcome.Offline, phone.sync())
        assertEquals(SyncState.Pending, phone.row(id)!!.syncState)

        // La reintenta: el servidor responde 409 (ya existe) y al bajar ve su propio bloque.
        assertEquals(SyncOutcome.Synced, phone.sync())

        val row = phone.row(id)!!
        assertEquals(SyncState.Synced, row.syncState)
        assertEquals(1L, row.serverVersion)
    }

    @Test
    fun `editar y subir usa la version esperada y sube la version`() {
        val id = phone.createLocal()
        phone.sync()
        assertTrue(phone.editLocal(id))
        assertEquals(SyncState.Pending, phone.row(id)!!.syncState)

        phone.sync()

        assertEquals(SyncState.Synced, phone.row(id)!!.syncState)
        assertEquals(2L, phone.row(id)!!.serverVersion)
        assertEquals(2L, world.serverItem(id)!!.version)
    }

    @Test
    fun `si el local_seq cambia mientras se sube sigue pending con la version nueva y despues se sube otra vez`() {
        val id = phone.createLocal()
        phone.sync() // v1 en el servidor
        phone.editLocal(id)
        // Mientras sube la edición, la persona edita otra vez.
        world.afterServer = { request ->
            if (request.method == "PUT") {
                world.afterServer = null
                phone.editLocal(id)
            }
        }

        phone.sync()

        // El motor ya volvió a subir la segunda edición en la misma ejecución (cada local_seq se intenta una vez).
        val row = phone.row(id)!!
        assertEquals(SyncState.Synced, row.syncState)
        assertEquals(3L, row.serverVersion)
        assertEquals(3L, world.serverItem(id)!!.version)
    }

    @Test
    fun `una edicion durante la subida no se marca synced en esa respuesta`() {
        val id = phone.createLocal()
        phone.sync()
        phone.editLocal(id)
        world.afterServer = { request ->
            if (request.method == "PUT") {
                world.afterServer = null
                // La persona edita otra vez mientras la respuesta viaja, y se va la red antes de la segunda subida.
                phone.editLocal(id)
                world.networkDown = true
            }
        }

        assertEquals(SyncOutcome.Offline, phone.sync())

        val row = phone.row(id)!!
        assertEquals("el local_seq cambió durante la subida: no es synced", SyncState.Pending, row.syncState)
        assertEquals("pero guarda la versión que dio el servidor", 2L, row.serverVersion)
    }

    @Test
    fun `un 409 deja el elemento en conflicto`() {
        val id = phone.createLocal()
        phone.sync()
        val other = world.secondDevice()
        other.sync()
        // El otro teléfono edita y sube primero; este edita con la versión vieja.
        other.editLocal(id)
        other.sync()
        phone.editLocal(id)

        phone.sync()

        assertEquals(SyncState.Conflict, phone.row(id)!!.syncState)
    }

    @Test
    fun `un 404 al editar vuelve a crear el elemento`() {
        val id = phone.createLocal()
        phone.sync()
        phone.editLocal(id)
        // El servidor purgó el elemento.
        world.state.items.remove(id.toString())

        phone.sync()

        val row = phone.row(id)!!
        assertEquals(SyncState.Synced, row.syncState)
        assertEquals(1L, row.serverVersion)
        assertNotNull(world.serverItem(id))
    }

    @Test
    fun `una lapida subida borra la fila local`() {
        val id = phone.createLocal()
        phone.sync()
        assertTrue(phone.deleteLocal(id))
        assertNull(phone.row(id)!!.ciphertext)

        phone.sync()

        assertNull(phone.row(id))
        assertTrue(world.serverItem(id)!!.deleted)
    }

    @Test
    fun `un elemento que nunca se subio y se borra no toca el servidor`() {
        val id = phone.createLocal()
        assertTrue(phone.deleteLocal(id))
        assertNull(phone.row(id))

        phone.sync()

        assertNull(world.serverItem(id))
    }

    @Test
    fun `una lapida remota sobre una fila synced la borra`() {
        val id = phone.createLocal()
        phone.sync()
        val other = world.secondDevice()
        other.sync()
        other.deleteLocal(id)
        other.sync()

        phone.sync()

        assertNull(phone.row(id))
    }

    @Test
    fun `un cambio remoto sobre una fila synced la reemplaza y sube el local_seq`() {
        val id = phone.createLocal()
        phone.sync()
        val before = phone.row(id)!!
        val other = world.secondDevice()
        other.sync()
        other.editLocal(id)
        other.sync()

        phone.sync()

        val after = phone.row(id)!!
        assertEquals(SyncState.Synced, after.syncState)
        assertEquals(2L, after.serverVersion)
        assertTrue("una edición abierta con el local_seq anterior ya no pasa", after.localSeq > before.localSeq)
        assertFalse(before.ciphertext!!.contentEquals(after.ciphertext!!))
    }

    @Test
    fun `un elemento nuevo de otro telefono se descarga como synced`() {
        val other = world.secondDevice()
        val id = other.createLocal()
        other.sync()

        phone.sync()

        assertEquals(SyncState.Synced, phone.row(id)!!.syncState)
        assertEquals(1L, phone.row(id)!!.serverVersion)
        assertTrue("y el cursor avanzó", phone.accounts.current!!.syncCursor > 0)
    }

    @Test
    fun `un cambio remoto sobre una fila pending la marca conflicto`() {
        val id = phone.createLocal()
        phone.sync()
        val other = world.secondDevice()
        other.sync()
        other.editLocal(id)
        other.sync()
        // Este teléfono edita pero no puede subir (sin red): al bajar, lo remoto choca con lo local.
        phone.editLocal(id)
        world.interceptor = { request -> if (request.method == "PUT") rejectUpload else null }

        phone.sync()
        world.interceptor = null

        assertEquals(SyncState.Conflict, phone.row(id)!!.syncState)
    }

    @Test
    fun `la fila local pending y la remota ya borrada son un conflicto, pero si las dos son lapida no hay nada que resolver`() {
        val id = phone.createLocal()
        phone.sync()
        val other = world.secondDevice()
        other.sync()
        other.deleteLocal(id)
        other.sync()
        phone.editLocal(id)
        world.interceptor = { request -> if (request.method == "PUT") rejectUpload else null }

        phone.sync()
        world.interceptor = null

        assertEquals("editada aquí, borrada allá: conflicto", SyncState.Conflict, phone.row(id)!!.syncState)
    }

    @Test
    fun `410 hace una descarga completa sin perder pending ni conflict y borra los synced que el servidor ya no tiene`() {
        val keepSynced = phone.createLocal()
        val vanished = phone.createLocal()
        phone.sync()
        val pendingId = phone.createLocal()
        val conflictId = UUID.randomUUID()
        phone.dao.put(world.row(conflictId, serverVersion = 1, localSeq = 2, state = SyncState.Conflict))
        // El servidor purgó lápidas y «vanished» ya no existe allá; el cursor del teléfono quedó atrás.
        world.state.items.remove(vanished.toString())
        world.state.users.values.first().purgedThroughRevision = world.state.users.values.first().vaultRevision + 10
        world.interceptor = { request -> if (request.method == "PUT") rejectUpload else null }

        phone.sync()
        world.interceptor = null

        assertEquals(SyncState.Synced, phone.row(keepSynced)!!.syncState)
        assertNull("el synced que el servidor ya no tiene se borra", phone.row(vanished))
        assertEquals("pending no se pierde", SyncState.Pending, phone.row(pendingId)!!.syncState)
        assertEquals("conflict no se pierde", SyncState.Conflict, phone.row(conflictId)!!.syncState)
    }

    @Test
    fun `borrar mientras se sube deja una lapida pending y despues se envia el DELETE`() {
        val id = phone.createLocal()
        world.afterServer = { request ->
            if (request.method == "PUT") {
                world.afterServer = null
                // Mientras el PUT está en el aire, la persona elimina el elemento.
                assertTrue(phone.engine.isUploading(id))
                assertTrue(phone.deleteLocal(id))
                assertNotNull("no se borró físicamente", phone.row(id))
                assertNull(phone.row(id)!!.ciphertext)
            }
        }

        phone.sync()

        assertNull("la lápida ya llegó al servidor y la fila local se fue", phone.row(id))
        assertTrue(world.serverItem(id)!!.deleted)
        assertEquals(listOf("PUT /vault/items/$id", "DELETE /vault/items/$id"), world.requests.filter { it.contains(id.toString()) })
        assertFalse(phone.engine.isUploading(id))
    }

    @Test
    fun `sin red termina Offline, avisa y no pierde nada, y al volver la red sube`() {
        val id = phone.createLocal()
        world.networkDown = true

        assertEquals(SyncOutcome.Offline, phone.sync())
        assertTrue(phone.engine.offline.value)
        assertEquals(SyncState.Pending, phone.row(id)!!.syncState)

        world.networkDown = false
        assertEquals(SyncOutcome.Synced, phone.sync())
        assertFalse(phone.engine.offline.value)
        assertEquals(SyncState.Synced, phone.row(id)!!.syncState)
    }

    @Test
    fun `un 5xx se reintenta despues y deja lo pendiente como estaba`() {
        val id = phone.createLocal()
        world.interceptor = { HttpResponse(503, null) }

        assertEquals(SyncOutcome.RetryLater, phone.sync())
        assertEquals(SyncState.Pending, phone.row(id)!!.syncState)
        assertFalse("un 5xx no es «sin conexión»", phone.engine.offline.value)

        world.interceptor = null
        assertEquals(SyncOutcome.Synced, phone.sync())
    }

    @Test
    fun `sube de uno en uno en orden de local_seq`() {
        val first = phone.createLocal()
        val second = phone.createLocal()
        phone.sync()
        phone.editLocal(second)
        phone.editLocal(second)
        phone.editLocal(first)
        world.requests.clear()

        phone.sync()

        val puts = world.requests.filter { it.startsWith("PUT") }
        assertEquals(listOf("PUT /vault/items/$first", "PUT /vault/items/$second"), puts)
    }

    @Test
    fun `sin cuenta no hay nada que sincronizar`() {
        runBlocking { phone.accounts.deleteAll() }
        assertEquals(SyncOutcome.NotSignedIn, phone.sync())
        assertTrue(world.requests.none { it.contains("/vault/") })
    }

    @Test
    fun `una sola ejecucion a la vez`() {
        phone.createLocal()
        var concurrent = 0
        var maxConcurrent = 0
        world.interceptor = {
            concurrent++
            maxConcurrent = maxOf(maxConcurrent, concurrent)
            concurrent--
            null
        }
        runBlocking {
            val a = async { phone.engine.sync() }
            val b = async { phone.engine.sync() }
            a.await()
            b.await()
        }
        assertEquals(1, maxConcurrent)
    }
}
