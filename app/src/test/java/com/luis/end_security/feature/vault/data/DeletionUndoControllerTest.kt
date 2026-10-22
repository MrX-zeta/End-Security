package com.luis.end_security.feature.vault.data

import androidx.lifecycle.LifecycleOwner
import com.luis.end_security.core.common.DeviceClock
import com.luis.end_security.core.crypto.SecureBytes
import com.luis.end_security.core.session.VaultSession
import com.luis.end_security.feature.vault.domain.DeletionUndo
import com.luis.end_security.feature.vault.domain.ItemDraft
import com.luis.end_security.feature.vault.domain.PendingDeletion
import com.luis.end_security.feature.vault.domain.SaveResult
import com.luis.end_security.feature.vault.domain.VaultEntry
import com.luis.end_security.feature.vault.domain.VaultRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class DeletionUndoControllerTest {

    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)
    private val session = VaultSession(
        object : DeviceClock {
            override fun wallMillis() = 0L
            override fun elapsedRealtimeMillis() = 0L
        },
    )

    /** Anota los borrados que llegan a Room. */
    private class RecordingRepository : VaultRepository {
        val deleted = mutableListOf<Pair<UUID, Long>>()
        var failWith: Exception? = null
        var result = true

        override fun observeEntries(): Flow<List<VaultEntry>> = emptyFlow()

        override suspend fun entry(id: UUID): VaultEntry? = null

        override fun observeEntry(id: UUID): Flow<VaultEntry?> = emptyFlow()

        override suspend fun create(draft: ItemDraft): SaveResult = error("no se usa")

        override suspend fun update(id: UUID, expectedLocalSeq: Long, draft: ItemDraft): SaveResult = error("no se usa")

        override suspend fun delete(id: UUID, expectedLocalSeq: Long): Boolean {
            failWith?.let { throw it }
            deleted += id to expectedLocalSeq
            return result
        }
    }

    private val repository = RecordingRepository()
    private val first = PendingDeletion(UUID.fromString("00000000-0000-0000-0000-000000000001"), "Gmail", 3)
    private val second = PendingDeletion(UUID.fromString("00000000-0000-0000-0000-000000000002"), "Amazon", 1)

    @After
    fun tearDown() = session.lock()

    @Test
    fun `no escribe nada durante los 5 s y a los 5 s escribe el borrado`() = scope.runTest {
        val controller = DeletionUndoController(repository, session, backgroundScope)
        controller.schedule(first)
        runCurrent()
        assertEquals(first, controller.pending.value)

        advanceTimeBy(DeletionUndo.UNDO_MILLIS - 1)
        runCurrent()
        assertEquals(emptyList<Any>(), repository.deleted)
        assertEquals(first, controller.pending.value)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(first.id to 3L), repository.deleted)
        assertNull(controller.pending.value)
    }

    @Test
    fun `deshacer dentro de los 5 s cancela sin tocar Room`() = scope.runTest {
        val controller = DeletionUndoController(repository, session, backgroundScope)
        controller.schedule(first)
        advanceTimeBy(3_000)
        controller.undo()
        advanceTimeBy(10_000)
        runCurrent()

        assertEquals(emptyList<Any>(), repository.deleted)
        assertNull(controller.pending.value)
    }

    @Test
    fun `deshacer despues de los 5 s ya no hay nada que deshacer`() = scope.runTest {
        val controller = DeletionUndoController(repository, session, backgroundScope)
        controller.schedule(first)
        advanceTimeBy(DeletionUndo.UNDO_MILLIS)
        runCurrent()
        controller.undo()
        assertEquals(1, repository.deleted.size)
    }

    @Test
    fun `bloquear la boveda durante los 5 s aplica la lapida de inmediato`() = scope.runTest {
        val controller = DeletionUndoController(repository, session, backgroundScope)
        session.unlock(UUID.randomUUID(), SecureBytes.copyOf(ByteArray(32)))
        runCurrent()
        controller.schedule(first)
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(emptyList<Any>(), repository.deleted)

        session.lock()
        runCurrent()

        assertEquals(listOf(first.id to 3L), repository.deleted)
        assertNull(controller.pending.value)
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals("no se escribe dos veces", 1, repository.deleted.size)
    }

    @Test
    fun `pasar a segundo plano escribe el borrado pendiente`() = scope.runTest {
        val controller = DeletionUndoController(repository, session, backgroundScope)
        controller.schedule(first)
        advanceTimeBy(1_000)
        controller.onStop(
            object : LifecycleOwner {
                override val lifecycle get() = error("el controlador no lo usa")
            },
        )
        runCurrent()

        assertEquals(listOf(first.id to 3L), repository.deleted)
        assertNull(controller.pending.value)
    }

    @Test
    fun `un fallo de Room no cierra la app ni deja el aviso para siempre`() = scope.runTest {
        val controller = DeletionUndoController(repository, session, backgroundScope)
        repository.failWith = IllegalStateException("disco lleno")
        controller.schedule(first)
        advanceTimeBy(DeletionUndo.UNDO_MILLIS)
        runCurrent()

        assertNull(controller.pending.value)
    }

    @Test
    fun `si el local_seq cambio y no se borro, el aviso se quita igual`() = scope.runTest {
        val controller = DeletionUndoController(repository, session, backgroundScope)
        repository.result = false
        controller.schedule(first)
        advanceTimeBy(DeletionUndo.UNDO_MILLIS)
        runCurrent()

        assertNull(controller.pending.value)
    }

    @Test
    fun `un borrado nuevo confirma el anterior y empieza su propio plazo`() = scope.runTest {
        val controller = DeletionUndoController(repository, session, backgroundScope)
        controller.schedule(first)
        advanceTimeBy(2_000)
        controller.schedule(second)
        runCurrent()

        assertEquals(listOf(first.id to 3L), repository.deleted)
        assertEquals(second, controller.pending.value)

        advanceTimeBy(DeletionUndo.UNDO_MILLIS - 1)
        runCurrent()
        assertEquals(1, repository.deleted.size)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(first.id to 3L, second.id to 1L), repository.deleted)
    }
}
