package com.luis.end_security.feature.unlock.data

import com.luis.end_security.core.crypto.BiometricUnwrapFailed
import com.luis.end_security.core.crypto.BiometricWrap
import com.luis.end_security.core.crypto.keystore.BiometricKeyInvalidatedException
import com.luis.end_security.core.crypto.keystore.BiometricKeyStore
import com.luis.end_security.core.database.dao.AccountDao
import com.luis.end_security.core.datastore.SettingsStore
import com.luis.end_security.core.session.SessionLifecycleLock
import com.luis.end_security.core.session.VaultLockedException
import com.luis.end_security.core.session.VaultSession
import com.luis.end_security.core.session.VaultState
import com.luis.end_security.feature.unlock.domain.BiometricAvailability
import com.luis.end_security.feature.unlock.domain.BiometricPreparation
import com.luis.end_security.feature.unlock.domain.BiometricResult
import com.luis.end_security.feature.unlock.domain.BiometricStatus
import com.luis.end_security.feature.unlock.domain.BiometricUnlock
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.GeneralSecurityException
import java.util.UUID
import javax.crypto.Cipher
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BiometricUnlockImpl @Inject constructor(
    private val keyStore: BiometricKeyStore,
    private val accounts: AccountDao,
    private val settings: SettingsStore,
    private val session: VaultSession,
    private val availability: BiometricAvailability,
    private val sessionLock: SessionLifecycleLock,
) : BiometricUnlock {

    override val enabled: Flow<Boolean> = settings.biometricEnabled

    override suspend fun prepareEnable(): BiometricPreparation = withContext(Dispatchers.IO) {
        when (availability.status()) {
            BiometricStatus.Available -> Unit
            BiometricStatus.NoneEnrolled -> return@withContext BiometricPreparation.NoneEnrolled
            else -> return@withContext BiometricPreparation.Failed
        }
        val userId = (session.state.value as? VaultState.Unlocked)?.userId
            ?: return@withContext BiometricPreparation.Failed
        try {
            BiometricPreparation.Ready(keyStore.newEncryptCipher(userId))
        } catch (_: GeneralSecurityException) {
            BiometricPreparation.Failed
        } catch (_: RuntimeException) {
            BiometricPreparation.Failed
        }
    }

    override suspend fun completeEnable(cipher: Cipher): BiometricResult = withContext(Dispatchers.IO) {
        val userId = (session.state.value as? VaultState.Unlocked)?.userId
            ?: return@withContext BiometricResult.Failed
        val account = accounts.get()?.takeIf { it.userId == userId.toString() }
            ?: return@withContext BiometricResult.Failed
        try {
            // El texto de la vault_key solo se lee aquí dentro, durante el cifrado (DEC-27).
            val wrapped = session.withVaultKey { key -> BiometricWrap.wrap(cipher, key, userId, account.keyVersion) }
            withContext(NonCancellable) {
                accounts.setBiometric(cipher.iv, wrapped)
                settings.setBiometricEnabled(true)
            }
            BiometricResult.Done
        } catch (_: VaultLockedException) {
            BiometricResult.Failed
        } catch (_: GeneralSecurityException) {
            // Sin dejar la llave a medias.
            keyStore.delete(userId)
            BiometricResult.Failed
        } catch (_: RuntimeException) {
            keyStore.delete(userId)
            BiometricResult.Failed
        }
    }

    override suspend fun disable() {
        withContext(NonCancellable + Dispatchers.IO) {
            accounts.get()?.let { keyStore.delete(UUID.fromString(it.userId)) }
            accounts.clearBiometric()
            settings.setBiometricEnabled(false)
        }
    }

    override suspend fun prepareUnlock(): BiometricPreparation = withContext(Dispatchers.IO) {
        val account = accounts.get() ?: return@withContext BiometricPreparation.Failed
        val iv = account.bioIv
        if (iv == null || account.bioWrappedVaultKey == null) return@withContext BiometricPreparation.NotEnabled
        try {
            BiometricPreparation.Ready(keyStore.decryptCipher(UUID.fromString(account.userId), iv))
        } catch (_: BiometricKeyInvalidatedException) {
            // Huella cambiada o llave ausente: se apaga todo y se pide la contraseña.
            disable()
            BiometricPreparation.Invalidated
        } catch (_: GeneralSecurityException) {
            // Error pasajero del Keystore: no se borra la huella del usuario; queda la contraseña.
            BiometricPreparation.Failed
        } catch (_: RuntimeException) {
            BiometricPreparation.Failed
        }
    }

    override suspend fun completeUnlock(cipher: Cipher): BiometricResult = withContext(Dispatchers.IO) {
        val account = accounts.get() ?: return@withContext BiometricResult.Failed
        val wrapped = account.bioWrappedVaultKey ?: return@withContext BiometricResult.Invalidated
        val userId = UUID.fromString(account.userId)
        val vaultKey = try {
            BiometricWrap.unwrap(cipher, wrapped, userId, account.keyVersion)
        } catch (_: BiometricUnwrapFailed) {
            // Datos que no descifran: se apaga todo y se pide la contraseña.
            disable()
            return@withContext BiometricResult.Invalidated
        }
        // Ni cuenta ni reinicia los intentos de la contraseña.
        withContext(NonCancellable) {
            // Mismo candado que `signOutLocally`: comprobar la cuenta y abrir son un solo paso (INV-07).
            sessionLock.mutex.withLock {
                try {
                    val still = accounts.get()
                    if (still == null || still.userId != account.userId || still.bioWrappedVaultKey == null) {
                        vaultKey.close()
                        BiometricResult.Failed
                    } else {
                        session.unlock(userId, vaultKey)
                        BiometricResult.Done
                    }
                } catch (_: RuntimeException) {
                    vaultKey.close()
                    BiometricResult.Failed
                }
            }
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BiometricUnlockModule {
    @Binds
    abstract fun bindBiometricUnlock(impl: BiometricUnlockImpl): BiometricUnlock
}
