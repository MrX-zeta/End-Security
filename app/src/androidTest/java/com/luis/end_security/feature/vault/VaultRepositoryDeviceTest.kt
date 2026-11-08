package com.luis.end_security.feature.vault

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.luis.end_security.core.common.DeviceClock
import com.luis.end_security.core.crypto.SecureBytes
import com.luis.end_security.core.database.AppDatabase
import com.luis.end_security.core.session.VaultSession
import com.luis.end_security.core.sync.SyncTrigger
import com.luis.end_security.core.sync.UploadGuard
import com.luis.end_security.feature.vault.data.VaultRepositoryImpl
import com.luis.end_security.feature.vault.domain.ItemDraft
import com.luis.end_security.feature.vault.domain.ReadableEntry
import com.luis.end_security.feature.vault.domain.SaveResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** El repositorio con Room y SQLite reales: lo que queda en el archivo de la base es solo el bloque cifrado. */
@RunWith(AndroidJUnit4::class)
class VaultRepositoryDeviceTest {

    private lateinit var database: AppDatabase
    private lateinit var session: VaultSession
    private lateinit var repository: VaultRepositoryImpl
    private val userId = UUID.fromString("0b9f3c2e-5d1a-4f7e-9a2b-6c8d1e4f7a90")

    @Before
    fun setUp() {
        val clock = object : DeviceClock {
            override fun wallMillis() = System.currentTimeMillis()
            override fun elapsedRealtimeMillis() = System.nanoTime() / 1_000_000
        }
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
        ).build()
        session = VaultSession(clock).also { it.unlock(userId, SecureBytes.copyOf(ByteArray(32) { 7 })) }
        repository = VaultRepositoryImpl(
            database.vaultItemDao(), session, clock, Dispatchers.Default,
            object : UploadGuard {
                override fun isUploading(id: UUID) = false
            },
            object : SyncTrigger {
                override fun changed() = Unit
            },
        )
    }

    @After
    fun tearDown() {
        session.lock()
        database.close()
    }

    @Test
    fun crear_editar_y_leer_con_la_base_real_sin_texto_claro_en_las_filas() = runBlocking {
        val id = (repository.create(ItemDraft("Netflix", "luis.ruiz@gmail.com", "Tortuga-Violeta-29", "netflix.com", "mi nota")) as SaveResult.Saved).id

        val raw = database.query("SELECT * FROM vault_items", null).use { cursor ->
            val builder = StringBuilder()
            while (cursor.moveToNext()) {
                for (column in 0 until cursor.columnCount) {
                    builder.append(
                        when (cursor.getType(column)) {
                            android.database.Cursor.FIELD_TYPE_BLOB -> String(cursor.getBlob(column), Charsets.ISO_8859_1)
                            else -> cursor.getString(column).orEmpty()
                        },
                    )
                }
            }
            builder.toString()
        }
        listOf("Netflix", "luis.ruiz", "Tortuga", "netflix.com", "mi nota").forEach {
            assertFalse("«$it» no debe estar en claro en Room", it in raw)
        }

        val entry = repository.entry(id) as ReadableEntry
        assertEquals("Netflix", entry.item.name)
        assertEquals(1L, entry.localSeq)

        assertEquals(SaveResult.Saved(id), repository.update(id, entry.localSeq, ItemDraft("Netflix HD", "luis", "Tortuga-Violeta-29", null, null)))
        val edited = repository.observeEntries().first().single() as ReadableEntry
        assertEquals("Netflix HD", edited.item.name)
        assertEquals(2L, edited.localSeq)
        assertEquals("la contraseña no cambió", entry.item.passwordChangedAt, edited.item.passwordChangedAt)

        session.lock()
        assertEquals(emptyList<Any>(), repository.observeEntries().first())
    }
}
