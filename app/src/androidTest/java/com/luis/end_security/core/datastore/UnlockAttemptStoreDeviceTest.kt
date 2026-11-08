package com.luis.end_security.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.luis.end_security.core.common.DeviceClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * [UnlockAttemptStore] con el DataStore de archivo real del teléfono (las pruebas unitarias usan
 * uno en memoria): los fallos se guardan y otro store sobre el mismo archivo los lee.
 */
@RunWith(AndroidJUnit4::class)
class UnlockAttemptStoreDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val file = File(context.cacheDir, "unlock_attempts_test.preferences_pb")
    private val scopes = mutableListOf<CoroutineScope>()
    private val clock = object : DeviceClock {
        var wall = 1_700_000_000_000L
        var elapsed = 3_600_000L
        override fun wallMillis() = wall
        override fun elapsedRealtimeMillis() = elapsed
    }

    @After
    fun tearDown() {
        scopes.forEach { it.cancel() }
        file.delete()
    }

    private fun newStore(): UnlockAttemptStore {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob()).also(scopes::add)
        return UnlockAttemptStore(PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }), clock)
    }

    @Test
    fun los_fallos_sobreviven_a_otro_store_sobre_el_mismo_archivo() = runBlocking {
        file.delete()
        val first = newStore()
        repeat(4) { assertEquals(Duration.ZERO, first.recordFailure()) }
        assertEquals(1.minutes, first.recordFailure())
        scopes.forEach { it.cancel() }
        scopes.clear()

        clock.wall += 15_000L
        clock.elapsed += 15_000L
        val second = newStore()
        assertEquals(45.seconds, second.remainingLockout())
        second.recordSuccess()
        assertEquals(Duration.ZERO, second.remainingLockout())
    }
}
