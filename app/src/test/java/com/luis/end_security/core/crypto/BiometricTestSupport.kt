package com.luis.end_security.core.crypto

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.luis.end_security.core.common.DeviceClock
import com.luis.end_security.core.crypto.keystore.BiometricKeyInvalidatedException
import com.luis.end_security.core.crypto.keystore.BiometricKeyStore
import com.luis.end_security.feature.unlock.domain.BiometricAvailability
import com.luis.end_security.feature.unlock.domain.BiometricStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * [BiometricKeyStore] en software para las pruebas de la JVM: una llave AES-256 por usuario con la JCA (sin
 * Keystore ni huella), con IV aleatorio. [invalidate] simula que la huella cambió.
 */
class FakeBiometricKeyStore : BiometricKeyStore {
    private val keys = mutableMapOf<UUID, SecretKey>()
    private val invalidated = mutableSetOf<UUID>()

    val hasKeys: Boolean get() = keys.isNotEmpty()

    fun has(userId: UUID) = userId in keys

    fun invalidate(userId: UUID) {
        invalidated += userId
    }

    override fun newEncryptCipher(userId: UUID): Cipher {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        keys[userId] = key
        invalidated -= userId
        return Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
    }

    override fun decryptCipher(userId: UUID, iv: ByteArray): Cipher {
        val key = keys[userId]
        if (key == null || userId in invalidated) throw BiometricKeyInvalidatedException()
        return Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv)) }
    }

    override fun delete(userId: UUID) {
        keys.remove(userId)
        invalidated -= userId
    }

    override fun deleteAll() {
        keys.clear()
        invalidated.clear()
    }
}

class FakeBiometricAvailability(var status: BiometricStatus = BiometricStatus.Available) : BiometricAvailability {
    override fun status(): BiometricStatus = status
}

/** DataStore en memoria (el de archivo no sirve en la JVM de Windows: falla al renombrar). */
class InMemoryPreferencesDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    private val mutex = Mutex()

    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        mutex.withLock { transform(state.value).also { state.value = it } }
}

object FixedClock : DeviceClock {
    override fun wallMillis(): Long = 0L

    override fun elapsedRealtimeMillis(): Long = 0L
}
