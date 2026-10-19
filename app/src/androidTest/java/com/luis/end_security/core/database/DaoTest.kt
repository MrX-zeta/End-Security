package com.luis.end_security.core.database

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
        assertEquals(1, dao.updateContent("a", ByteArray(12) { 6 }, ByteArray(40) { 7 }, expectedLocalSeq = 1, now = 2_000))
        val item = dao.get("a")!!
        assertEquals(2, item.localSeq)
        assertEquals(SyncState.Pending, item.syncState)
        assertEquals(listOf("a"), dao.observeVisible().first().map { it.id })
    }

    @Test
    fun editar_con_local_seq_viejo_no_cambia_nada() = runBlocking {
        val dao = database.vaultItemDao()
        dao.insert(newItem("a"))
        assertEquals(0, dao.updateContent("a", ByteArray(12), ByteArray(40), expectedLocalSeq = 7, now = 2_000))
        assertEquals(1, dao.get("a")!!.localSeq)
    }

    @Test
    fun editar_en_conflicto_se_queda_en_conflicto() = runBlocking {
        val dao = database.vaultItemDao()
        dao.insert(VaultItemEntity("a", 1, ByteArray(12), ByteArray(40), 3, 3, 1, SyncState.Conflict, null, 1_000))
        dao.updateContent("a", ByteArray(12) { 1 }, ByteArray(40) { 1 }, expectedLocalSeq = 1, now = 2_000)
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
