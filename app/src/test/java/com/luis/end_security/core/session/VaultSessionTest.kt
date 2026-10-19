package com.luis.end_security.core.session

import com.luis.end_security.core.common.DeviceClock
import com.luis.end_security.core.crypto.SecureBytes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID
import java.util.concurrent.CountDownLatch

class VaultSessionTest {

    private val userId = UUID.fromString("0b9f3c2e-5d1a-4f7e-9a2b-6c8d1e4f7a90")

    private fun key(fill: Int = 5) = SecureBytes.copyOf(ByteArray(32) { fill.toByte() })

    private class FakeClock : DeviceClock {
        var elapsed = 1_000_000L
        override fun wallMillis() = 0L
        override fun elapsedRealtimeMillis() = elapsed
    }

    private val clock = FakeClock()

    @Test
    fun `empieza bloqueada y withVaultKey lanza VaultLockedException`() {
        val session = VaultSession(clock)
        assertEquals(VaultState.Locked, session.state.value)
        assertThrows(VaultLockedException::class.java) { session.withVaultKey { it.size } }
    }

    @Test
    fun `unlock abre y presta la llave`() {
        val session = VaultSession(clock)
        val vaultKey = key()
        session.unlock(userId, vaultKey)
        assertEquals(VaultState.Unlocked(userId), session.state.value)
        assertEquals(List<Byte>(32) { 5 }, session.withVaultKey { reader -> reader.withBytes { it.toList() } })
        assertEquals("SecretReader(***)", session.withVaultKey { it.toString() })
    }

    @Test
    fun `lock borra la llave y vuelve a bloquear`() {
        val session = VaultSession(clock)
        val vaultKey = key()
        session.unlock(userId, vaultKey)
        session.lock()
        assertTrue(vaultKey.isWiped)
        assertEquals(VaultState.Locked, session.state.value)
        assertThrows(VaultLockedException::class.java) { session.withVaultKey { it.size } }
    }

    @Test
    fun `un segundo unlock de la misma cuenta borra la llave anterior`() {
        val session = VaultSession(clock)
        val first = key(1)
        session.unlock(userId, first)
        session.unlock(userId, key(2))
        assertTrue(first.isWiped)
    }

    @Test
    fun `abrir otra cuenta sin bloquear antes no se permite`() {
        val session = VaultSession(clock)
        session.unlock(userId, key(1))
        assertThrows(IllegalStateException::class.java) {
            session.unlock(UUID.fromString("0b9f3c2e-5d1a-4f7e-9a2b-6c8d1e4f7a91"), key(2))
        }
    }

    @Test
    fun `pasado el plazo de autobloqueo withVaultKey bloquea y no presta la llave`() {
        val session = VaultSession(clock)
        val vaultKey = key()
        session.unlock(userId, vaultKey)
        session.setLockDeadline(clock.elapsed + 5 * 60_000L)
        clock.elapsed += 4 * 60_000L + 59_000L
        assertEquals(32, session.withVaultKey { it.size })
        clock.elapsed += 1_000L
        assertThrows(VaultLockedException::class.java) { session.withVaultKey { it.size } }
        assertEquals(VaultState.Locked, session.state.value)
        assertTrue(vaultKey.isWiped)
    }

    @Test
    fun `una llave guardada fuera ya no sirve despues de lock`() {
        val session = VaultSession(clock)
        session.unlock(userId, key())
        val leaked = session.withVaultKey { it }
        session.lock()
        assertThrows(IllegalStateException::class.java) { leaked.withBytes { it.size } }
        // Solo se presta para leer: el bloque no tiene wipe() ni close().
        assertTrue(leaked !is AutoCloseable)
    }

    @Test
    fun `bloquear a mitad de un uso da VaultLockedException, nunca ceros`() {
        val session = VaultSession(clock)
        session.unlock(userId, key())
        val holding = CountDownLatch(1)
        val locked = CountDownLatch(1)
        var outcome: Any? = null
        val worker = Thread {
            outcome = try {
                session.withVaultKey { reader ->
                    holding.countDown()
                    locked.await()
                    reader.withBytes { it.toList() }
                }
            } catch (error: VaultLockedException) {
                error
            }
        }
        worker.start()
        holding.await()
        session.lock()
        locked.countDown()
        worker.join()
        assertTrue("debió ver VaultLockedException, no una llave", outcome is VaultLockedException)
    }

    @Test
    fun `lock espera a que termine un withBytes en curso`() {
        val session = VaultSession(clock)
        session.unlock(userId, key(7))
        val reading = CountDownLatch(1)
        var seen: List<Byte> = emptyList()
        val worker = Thread {
            session.withVaultKey { reader ->
                reader.withBytes { bytes ->
                    reading.countDown()
                    Thread.sleep(200)
                    seen = bytes.toList()
                }
            }
        }
        worker.start()
        reading.await()
        session.lock()
        worker.join()
        assertEquals(List<Byte>(32) { 7 }, seen)
    }
}
