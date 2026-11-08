package com.luis.end_security.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.luis.end_security.core.common.DeviceClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Con un DataStore en memoria compartido entre stores: crear otro store sobre él es como cerrar
 * y abrir la app. (El DataStore de archivo no sirve en la JVM de Windows: falla al renombrar.)
 */
class UnlockAttemptStoreTest {

    private val clock = FakeClock()
    private val dataStore = InMemoryDataStore()

    private fun newStore() = UnlockAttemptStore(dataStore, clock)

    @Test
    fun `5 fallos bloquean 1 min y el sexto 2 min`() = runBlocking {
        val store = newStore()
        repeat(4) { assertEquals(Duration.ZERO, store.recordFailure()) }
        assertEquals(1.minutes, store.recordFailure())
        assertEquals(1.minutes, store.remainingLockout())
        clock.advance(61.seconds)
        assertEquals(Duration.ZERO, store.remainingLockout())
        assertEquals(2.minutes, store.recordFailure())
    }

    @Test
    fun `un acierto reinicia los fallos`() = runBlocking {
        val store = newStore()
        repeat(5) { store.recordFailure() }
        store.recordSuccess()
        assertEquals(Duration.ZERO, store.remainingLockout())
        repeat(4) { assertEquals(Duration.ZERO, store.recordFailure()) }
    }

    @Test
    fun `cerrar la app no reinicia el bloqueo`() = runBlocking {
        val first = newStore()
        repeat(5) { first.recordFailure() }
        clock.advance(20.seconds)
        assertEquals(40.seconds, newStore().remainingLockout())
    }

    @Test
    fun `adelantar la hora no acorta el bloqueo`() = runBlocking {
        val store = newStore()
        repeat(5) { store.recordFailure() }
        clock.wall += 86_400_000L
        assertEquals(1.minutes, store.remainingLockout())
    }

    @Test
    fun `tras reiniciar el telefono manda el reloj de pared`() = runBlocking {
        val store = newStore()
        repeat(5) { store.recordFailure() }
        clock.reboot(wallAdvance = 45.seconds)
        assertEquals(15.seconds, store.remainingLockout())
    }

    private class FakeClock : DeviceClock {
        var wall = 1_700_000_000_000L
        var elapsed = 3_600_000L

        override fun wallMillis(): Long = wall

        override fun elapsedRealtimeMillis(): Long = elapsed

        fun advance(duration: Duration) {
            wall += duration.inWholeMilliseconds
            elapsed += duration.inWholeMilliseconds
        }

        fun reboot(wallAdvance: Duration) {
            wall += wallAdvance.inWholeMilliseconds
            elapsed = 5_000L
        }
    }

    private class InMemoryDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        private val mutex = Mutex()

        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            mutex.withLock { transform(state.value).also { state.value = it } }
    }
}
