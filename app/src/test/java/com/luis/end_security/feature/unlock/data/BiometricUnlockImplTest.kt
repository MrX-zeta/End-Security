package com.luis.end_security.feature.unlock.data

import com.luis.end_security.core.crypto.FakeBiometricAvailability
import com.luis.end_security.core.crypto.FakeBiometricKeyStore
import com.luis.end_security.core.crypto.FixedClock
import com.luis.end_security.core.crypto.InMemoryPreferencesDataStore
import com.luis.end_security.core.crypto.SecureBytes
import com.luis.end_security.core.database.entity.AccountEntity
import com.luis.end_security.core.datastore.SettingsStore
import com.luis.end_security.core.session.VaultSession
import com.luis.end_security.core.session.VaultState
import com.luis.end_security.feature.unlock.domain.BiometricPreparation
import com.luis.end_security.feature.unlock.domain.BiometricResult
import com.luis.end_security.feature.unlock.domain.BiometricStatus
import com.luis.end_security.feature.vault.data.FakeAccountDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/** OP-35 (activar/desactivar) y OP-20 (desbloquear) con un Keystore falso en software. */
class BiometricUnlockImplTest {

    private val userId = UUID.fromString("0b9f3c2e-5d1a-4f7e-9a2b-6c8d1e4f7a90")
    private val vaultKeyBytes = ByteArray(32) { (it + 5).toByte() }
    private val keyStore = FakeBiometricKeyStore()
    private val availability = FakeBiometricAvailability()
    private val settings = SettingsStore(InMemoryPreferencesDataStore())
    private val session = VaultSession(FixedClock)
    private val accounts = FakeAccountDao(
        AccountEntity(userId.toString(), "ana@correo.com", null, ByteArray(16), 65_536, 3, 1, ByteArray(12), ByteArray(48), 1, 0, null, null, null),
    )
    private val biometric = BiometricUnlockImpl(keyStore, accounts, settings, session, availability)

    private fun openVault() = session.unlock(userId, SecureBytes.copyOf(vaultKeyBytes))

    private fun enable() = runBlocking {
        openVault()
        val ready = biometric.prepareEnable() as BiometricPreparation.Ready
        assertEquals(BiometricResult.Done, biometric.completeEnable(ready.cipher))
    }

    private fun readKey(): ByteArray {
        var copy = ByteArray(0)
        session.withVaultKey { it.withBytes { bytes -> copy = bytes.copyOf() } }
        return copy
    }

    @Test
    fun `activar guarda bio_iv, la vault_key envuelta y la bandera`() = runBlocking {
        enable()
        val account = accounts.current!!
        assertEquals(12, account.bioIv!!.size)
        assertEquals(32 + 16, account.bioWrappedVaultKey!!.size)
        assertTrue(settings.biometricEnabled.first())
        assertTrue(keyStore.has(userId))
        // El envoltorio no es la vault_key en claro.
        assertFalse(account.bioWrappedVaultKey!!.copyOfRange(0, 32).contentEquals(vaultKeyBytes))
    }

    @Test
    fun `sin huellas registradas no se activa`() = runBlocking {
        openVault()
        availability.status = BiometricStatus.NoneEnrolled
        assertEquals(BiometricPreparation.NoneEnrolled, biometric.prepareEnable())
        assertFalse(keyStore.hasKeys)
        assertFalse(settings.biometricEnabled.first())
    }

    @Test
    fun `sin sensor o sin boveda abierta no se activa`() = runBlocking {
        openVault()
        availability.status = BiometricStatus.NoHardware
        assertEquals(BiometricPreparation.Failed, biometric.prepareEnable())
        availability.status = BiometricStatus.Available
        session.lock()
        assertEquals(BiometricPreparation.Failed, biometric.prepareEnable())
    }

    @Test
    fun `desactivar borra la llave, el envoltorio y la bandera`() = runBlocking {
        enable()
        biometric.disable()
        val account = accounts.current!!
        assertNull(account.bioIv)
        assertNull(account.bioWrappedVaultKey)
        assertFalse(settings.biometricEnabled.first())
        assertFalse(keyStore.has(userId))
    }

    @Test
    fun `desbloquear abre la sesion con la misma vault_key`() = runBlocking {
        enable()
        session.lock()
        val ready = biometric.prepareUnlock() as BiometricPreparation.Ready
        assertEquals(BiometricResult.Done, biometric.completeUnlock(ready.cipher))
        assertEquals(VaultState.Unlocked(userId), session.state.value)
        assertArrayEquals(vaultKeyBytes, readKey())
    }

    @Test
    fun `sin la huella activada no hay nada que desbloquear`() = runBlocking {
        assertEquals(BiometricPreparation.NotEnabled, biometric.prepareUnlock())
    }

    @Test
    fun `huella invalidada desactiva todo y pide la contrasena`() = runBlocking {
        enable()
        session.lock()
        keyStore.invalidate(userId)
        assertEquals(BiometricPreparation.Invalidated, biometric.prepareUnlock())
        assertNull(accounts.current!!.bioIv)
        assertFalse(settings.biometricEnabled.first())
        assertFalse(keyStore.has(userId))
        assertEquals(VaultState.Locked, session.state.value)
    }

    @Test
    fun `datos que no descifran desactivan todo`() = runBlocking {
        enable()
        session.lock()
        val ready = biometric.prepareUnlock() as BiometricPreparation.Ready
        // Cambió la version de llaves: la AAD ya no coincide.
        val old = accounts.current!!
        accounts.replace(
            AccountEntity(
                old.userId, old.email, old.displayName, old.kdfSalt, old.kdfMemoryKiB, old.kdfIterations, old.kdfParallelism,
                old.pvkNonce, old.pvkCiphertext, 2, old.syncCursor, old.lastSyncAt, old.bioIv, old.bioWrappedVaultKey,
            ),
        )
        assertEquals(BiometricResult.Invalidated, biometric.completeUnlock(ready.cipher))
        assertEquals(VaultState.Locked, session.state.value)
        assertNull(accounts.current!!.bioWrappedVaultKey)
        assertFalse(settings.biometricEnabled.first())
    }

    @Test
    fun `un bloque alterado no abre la sesion`() = runBlocking {
        enable()
        session.lock()
        val old = accounts.current!!
        val tampered = old.bioWrappedVaultKey!!.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
        accounts.setBiometric(old.bioIv!!, tampered)
        val ready = biometric.prepareUnlock() as BiometricPreparation.Ready
        assertEquals(BiometricResult.Invalidated, biometric.completeUnlock(ready.cipher))
        assertEquals(VaultState.Locked, session.state.value)
    }

    @Test
    fun `activar con la boveda bloqueada a medias no deja la bandera`() = runBlocking {
        openVault()
        val ready = biometric.prepareEnable() as BiometricPreparation.Ready
        session.lock()
        assertEquals(BiometricResult.Failed, biometric.completeEnable(ready.cipher))
        assertFalse(settings.biometricEnabled.first())
        assertNotNull(accounts.current)
        assertNull(accounts.current!!.bioIv)
    }
}
