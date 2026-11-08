package com.luis.end_security.core.database

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.luis.end_security.core.database.dao.RemoteRow
import com.luis.end_security.core.database.entity.AccountEntity
import com.luis.end_security.core.database.entity.BreachCheckEntity
import com.luis.end_security.core.database.entity.HealthSnapshotEntity
import com.luis.end_security.core.database.entity.SyncState
import com.luis.end_security.core.database.entity.VaultItemEntity
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
import org.junit.runner.RunWith

/** DAOs con una base en memoria. */
@RunWith(AndroidJUnit4::class)
class DaoTest {

    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun account(userId: String) = AccountEntity(
        userId = userId,
        email = "ana@correo.com",
        displayName = null,
        kdfSalt = ByteArray(16) { 1 },
        kdfMemoryKiB = 65_536,
        kdfIterations = 3,
        kdfParallelism = 1,
        pvkNonce = ByteArray(12) { 2 },
        pvkCiphertext = ByteArray(48) { 3 },
        keyVersion = 1,
        syncCursor = 0,
        lastSyncAt = null,
        bioIv = null,
        bioWrappedVaultKey = null,
    )

    private fun newItem(id: String, serverVersion: Long? = null) = VaultItemEntity(
        id = id,
        formatVersion = 1,
        nonce = ByteArray(12) { 4 },
        ciphertext = ByteArray(40) { 5 },
        serverVersion = serverVersion,
        serverRevision = serverVersion,
        localSeq = 1,
        syncState = if (serverVersion == null) SyncState.Pending else SyncState.Synced,
        deletedAt = null,
        updatedAt = 1_000,
    )

    private fun remote(id: String, version: Long, deleted: Boolean = false) = RemoteRow(
        id = id, version = version, revision = version, formatVersion = 1,
        nonce = if (deleted) null else ByteArray(12) { 9 }, ciphertext = if (deleted) null else ByteArray(40) { 9 },
        deleted = deleted, updatedAt = 5_000,
    )

    private suspend fun seedAccount() = database.accountDao().replace(account("11111111-1111-1111-1111-111111111111"))

    @Test
    fun applyRemotePage_sigue_la_tabla_de_cambios_remotos() = runBlocking {
        seedAccount()
        val dao = database.vaultItemDao()
        dao.insert(newItem("pendiente", serverVersion = 1).let { VaultItemEntity(it.id, 1, it.nonce, it.ciphertext, 1, 1, 2, SyncState.Pending, null, 1_000) })
        dao.insert(newItem("subido", serverVersion = 1))
        dao.insert(newItem("borrado", serverVersion = 1))
        dao.insert(VaultItemEntity("lapida-local", 1, null, null, 1, 1, 2, SyncState.Pending, 900, 900))
        dao.insert(newItem("conocido", serverVersion = 3))

        dao.applyRemotePage(
            changes = listOf(
                remote("pendiente", 2),
                remote("subido", 2),
                remote("borrado", 2, deleted = true),
                remote("nuevo", 1),
                remote("nuevo-borrado", 1, deleted = true),
                remote("lapida-local", 2, deleted = true),
                remote("conocido", 3),
            ),
            cursor = 7,
            now = 9_000,
        )

        assertEquals("cambio remoto sobre pending: conflict", SyncState.Conflict, dao.get("pendiente")!!.syncState)
        assertEquals("reemplaza el bloque synced", 2L, dao.get("subido")!!.serverVersion)
        assertEquals(SyncState.Synced, dao.get("subido")!!.syncState)
        assertNull("lápida remota sobre synced: se borra", dao.get("borrado"))
        assertNotNull("elemento nuevo: se guarda", dao.get("nuevo"))
        assertEquals(SyncState.Synced, dao.get("nuevo")!!.syncState)
        assertNull("lápida de algo que no teníamos: no se guarda", dao.get("nuevo-borrado"))
        assertNull("lápida local y lápida remota: no hay nada que resolver", dao.get("lapida-local"))
        assertEquals("versión que ya conocía: se ignora", 1L, dao.get("conocido")!!.localSeq)
    }

    @Test
    fun applyRemotePage_guarda_el_cursor_en_la_misma_transaccion_que_las_filas() = runBlocking {
        seedAccount()
        val dao = database.vaultItemDao()

        dao.applyRemotePage(listOf(remote("a", 1), remote("b", 2)), cursor = 2, now = 9_000)

        val account = database.accountDao().get()!!
        assertEquals(2L, account.syncCursor)
        assertEquals(9_000L, account.lastSyncAt)
        assertNotNull(dao.get("a"))
        assertNotNull(dao.get("b"))
    }

    @Test
    fun applyRemotePage_sube_el_local_seq_para_invalidar_ediciones_abiertas() = runBlocking {
        seedAccount()
        val dao = database.vaultItemDao()
        dao.applyRemotePage(listOf(remote("a", 1)), cursor = 1, now = 1)
        val opened = dao.get("a")!!.localSeq

        // Llega un cambio remoto sobre la fila synced: una edición abierta con `opened` ya no debe pasar.
        dao.applyRemotePage(listOf(remote("a", 2)), cursor = 2, now = 2)

        assertTrue(dao.get("a")!!.localSeq > opened)
        assertEquals(0, dao.updateContent("a", ByteArray(12), ByteArray(40), expectedLocalSeq = opened, now = 9, formatVersion = 1))
    }

    @Test
    fun applyRemotePage_con_keepOnly_borra_los_synced_ausentes_y_conserva_pending_y_conflict() = runBlocking {
        seedAccount()
        val dao = database.vaultItemDao()
        dao.insert(newItem("sigue", serverVersion = 1))
        dao.insert(newItem("desaparecio", serverVersion = 1))
        dao.insert(newItem("nuevo-local"))
        dao.insert(VaultItemEntity("en-conflicto", 1, ByteArray(12), ByteArray(40), 1, 1, 2, SyncState.Conflict, null, 1))

        dao.applyRemotePage(emptyList(), cursor = 5, now = 1, keepOnly = setOf("sigue"))

        assertNotNull(dao.get("sigue"))
        assertNull(dao.get("desaparecio"))
        assertNotNull("pending no se pierde", dao.get("nuevo-local"))
        assertNotNull("conflict no se pierde", dao.get("en-conflicto"))
    }

    @Test
    fun markUploaded_solo_deja_synced_si_el_local_seq_no_cambio_y_no_se_borro() = runBlocking {
        val dao = database.vaultItemDao()
        dao.insert(newItem("a"))
        dao.insert(newItem("b"))
        dao.updateContent("b", ByteArray(12), ByteArray(40), expectedLocalSeq = 1, now = 2, formatVersion = 1)
        dao.insert(newItem("c"))
        dao.delete("c", expectedLocalSeq = 1, now = 3, uploadInFlight = true)

        dao.markUploaded("a", uploadedLocalSeq = 1, version = 1, revision = 1)
        dao.markUploaded("b", uploadedLocalSeq = 1, version = 1, revision = 1)
        dao.markUploaded("c", uploadedLocalSeq = 1, version = 1, revision = 1)

        assertEquals(SyncState.Synced, dao.get("a")!!.syncState)
        assertEquals("el local_seq cambió durante la subida", SyncState.Pending, dao.get("b")!!.syncState)
        assertEquals(1L, dao.get("b")!!.serverVersion)
        assertEquals("borrado durante la subida: lápida pending con la versión nueva", SyncState.Pending, dao.get("c")!!.syncState)
        assertEquals(1L, dao.get("c")!!.serverVersion)
        assertNull(dao.get("c")!!.ciphertext)
    }

    @Test
    fun borrar_con_una_subida_en_curso_deja_lapida_aunque_nunca_se_haya_subido() = runBlocking {
        val dao = database.vaultItemDao()
        dao.insert(newItem("a"))
        assertTrue(dao.delete("a", expectedLocalSeq = 1, now = 2, uploadInFlight = true))
        assertNull(dao.get("a")!!.ciphertext)
    }

    @Test
    fun updateContent_no_reescribe_un_format_version_distinto() = runBlocking {
        val dao = database.vaultItemDao()
        dao.insert(VaultItemEntity("a", 2, ByteArray(12), ByteArray(40), 1, 1, 1, SyncState.Synced, null, 1))
        assertEquals(0, dao.updateContent("a", ByteArray(12), ByteArray(40), expectedLocalSeq = 1, now = 2, formatVersion = 1))
        assertEquals(1, dao.updateContent("a", ByteArray(12), ByteArray(40), expectedLocalSeq = 1, now = 2, formatVersion = 2))
    }

    @Test
    fun resolver_conflictos_con_la_del_servidor_o_con_la_local() = runBlocking {
        val dao = database.vaultItemDao()
        dao.insert(VaultItemEntity("s", 1, ByteArray(12), ByteArray(40), 1, 1, 3, SyncState.Conflict, null, 1))
        dao.insert(VaultItemEntity("l", 1, ByteArray(12), ByteArray(40), 1, 1, 3, SyncState.Conflict, null, 1))

        assertFalse("local_seq viejo: no pisa una edición reciente", dao.resolveWithRemote(remote("s", 4), expectedLocalSeq = 2))
        assertTrue(dao.resolveWithRemote(remote("s", 4), expectedLocalSeq = 3))
        assertEquals(SyncState.Synced, dao.get("s")!!.syncState)
        assertEquals(4L, dao.get("s")!!.serverVersion)
        assertTrue(dao.get("s")!!.localSeq > 3)

        assertEquals(0, dao.resolveKeepingLocal("l", ByteArray(12), ByteArray(40), 4, 4, expectedLocalSeq = 2, now = 5))
        assertEquals(1, dao.resolveKeepingLocal("l", ByteArray(12) { 8 }, ByteArray(40) { 8 }, 4, 4, expectedLocalSeq = 3, now = 5))
        assertEquals(SyncState.Pending, dao.get("l")!!.syncState)
        assertEquals(4L, dao.get("l")!!.serverVersion)
    }

    @Test
    fun observe_entrega_un_solo_elemento_y_null_si_es_lapida() = runBlocking {
        val dao = database.vaultItemDao()
        dao.insert(newItem("a", serverVersion = 1))
        dao.insert(newItem("b", serverVersion = 1))
        assertEquals("a", dao.observe("a").first()?.id)
        dao.delete("a", expectedLocalSeq = 1, now = 5)
        assertNull(dao.observe("a").first())
        assertNotNull(dao.observe("b").first())
    }

    @Test
    fun account_siempre_es_una_sola_fila() = runBlocking {
        val dao = database.accountDao()
        assertNull(dao.get())
        dao.replace(account("11111111-1111-1111-1111-111111111111"))
        dao.replace(account("22222222-2222-2222-2222-222222222222"))
        assertEquals("22222222-2222-2222-2222-222222222222", dao.get()?.userId)
        val count = database.query("SELECT COUNT(*) FROM account", null).use { it.moveToFirst(); it.getInt(0) }
        assertEquals(1, count)
    }

    @Test
    fun crear_editar_y_leer_en_flow() = runBlocking {
        val dao = database.vaultItemDao()
        dao.insert(newItem("a"))
        assertEquals(1, dao.updateContent("a", ByteArray(12) { 6 }, ByteArray(40) { 7 }, expectedLocalSeq = 1, now = 2_000, formatVersion = 1))
        val item = dao.get("a")!!
        assertEquals(2, item.localSeq)
        assertEquals(SyncState.Pending, item.syncState)
        assertEquals(listOf("a"), dao.observeVisible().first().map { it.id })
    }

    @Test
    fun editar_con_local_seq_viejo_no_cambia_nada() = runBlocking {
        val dao = database.vaultItemDao()
        dao.insert(newItem("a"))
        assertEquals(0, dao.updateContent("a", ByteArray(12), ByteArray(40), expectedLocalSeq = 7, now = 2_000, formatVersion = 1))
        assertEquals(1, dao.get("a")!!.localSeq)
    }

    @Test
    fun editar_en_conflicto_se_queda_en_conflicto() = runBlocking {
        val dao = database.vaultItemDao()
        dao.insert(VaultItemEntity("a", 1, ByteArray(12), ByteArray(40), 3, 3, 1, SyncState.Conflict, null, 1_000))
        dao.updateContent("a", ByteArray(12) { 1 }, ByteArray(40) { 1 }, expectedLocalSeq = 1, now = 2_000, formatVersion = 1)
        assertEquals(SyncState.Conflict, dao.get("a")!!.syncState)
    }

    @Test
    fun borrar_sin_haberse_subido_borra_la_fila() = runBlocking {
        val dao = database.vaultItemDao()
        dao.insert(newItem("a", serverVersion = null))
        assertTrue(dao.delete("a", expectedLocalSeq = 1, now = 2_000))
        assertNull(dao.get("a"))
    }

    @Test
    fun borrar_ya_subido_deja_lapida_pendiente() = runBlocking {
        val dao = database.vaultItemDao()
        dao.insert(newItem("a", serverVersion = 5))
        assertTrue(dao.delete("a", expectedLocalSeq = 1, now = 2_000))
        val tombstone = dao.get("a")!!
        assertTrue(tombstone.isTombstone)
        assertNull(tombstone.nonce)
        assertEquals(2_000L, tombstone.deletedAt)
        assertEquals(2, tombstone.localSeq)
        assertEquals(SyncState.Pending, tombstone.syncState)
        assertTrue("la lápida no se ve en la Bóveda", dao.observeVisible().first().isEmpty())
        assertFalse("no se borra dos veces", dao.delete("a", expectedLocalSeq = 2, now = 3_000))
    }

    @Test
    fun breach_check_se_borra_con_el_elemento() = runBlocking {
        database.vaultItemDao().insert(newItem("a"))
        database.breachCheckDao().upsert(BreachCheckEntity("a", 1, ByteArray(12), ByteArray(20), 1_000))
        assertNotNull(database.breachCheckDao().get("a"))
        database.vaultItemDao().delete("a", expectedLocalSeq = 1, now = 2_000)
        assertNull(database.breachCheckDao().get("a"))
    }

    @Test
    fun health_snapshot_una_por_dia_y_se_recorta() = runBlocking {
        val dao = database.healthSnapshotDao()
        dao.upsert(HealthSnapshotEntity("2025-09-30", 1, ByteArray(12), ByteArray(30), 1))
        dao.upsert(HealthSnapshotEntity("2026-10-18", 1, ByteArray(12), ByteArray(30), 2))
        dao.upsert(HealthSnapshotEntity("2026-10-18", 1, ByteArray(12) { 9 }, ByteArray(30), 3))
        assertEquals(listOf("2025-09-30", "2026-10-18"), dao.observeAll().first().map { it.day })
        dao.deleteBefore("2025-10-18")
        assertEquals(listOf("2026-10-18"), dao.observeAll().first().map { it.day })
        assertEquals(3L, dao.observeAll().first().single().createdAt)
    }

    @Test
    fun cerrar_sesion_vacia_todas_las_tablas() = runBlocking {
        database.accountDao().replace(account("11111111-1111-1111-1111-111111111111"))
        database.vaultItemDao().insert(newItem("a"))
        database.healthSnapshotDao().upsert(HealthSnapshotEntity("2026-10-18", 1, ByteArray(12), ByteArray(30), 1))
        database.clearAllTables()
        assertNull(database.accountDao().get())
        assertTrue(database.vaultItemDao().observeVisible().first().isEmpty())
        assertTrue(database.healthSnapshotDao().observeAll().first().isEmpty())
    }

    @Test
    fun cambiar_de_cuenta_borra_los_datos_de_la_anterior() = runBlocking {
        database.accountDao().replace(account("11111111-1111-1111-1111-111111111111"))
        database.vaultItemDao().insert(newItem("a"))
        database.healthSnapshotDao().upsert(HealthSnapshotEntity("2026-10-18", 1, ByteArray(12), ByteArray(30), 1))
        // La misma cuenta otra vez (p. ej. al actualizar key_version): los datos se quedan.
        database.accountDao().replace(account("11111111-1111-1111-1111-111111111111"))
        assertNotNull(database.vaultItemDao().get("a"))
        database.accountDao().replace(account("22222222-2222-2222-2222-222222222222"))
        assertNull(database.vaultItemDao().get("a"))
        assertTrue(database.healthSnapshotDao().observeAll().first().isEmpty())
        assertTrue(database.accountDao().exists())
    }
}
