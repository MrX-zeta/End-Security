package com.luis.end_security.core.crypto.keystore

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/** La huella o el rostro cambiaron y el Keystore invalidó la llave (DEC-09): hay que usar la contraseña maestra. */
class BiometricKeyInvalidatedException : Exception()

/**
 * Llave AES-256-GCM del Android Keystore que envuelve la `vault_key` para el desbloqueo biométrico (DEC-09, OP-35).
 * Solo entrega un `Cipher` listo para usarse con `BiometricPrompt.CryptoObject`: la llave nunca sale del Keystore y
 * no se puede usar sin que el usuario se autentique con la huella en ese mismo uso.
 */
interface BiometricKeyStore {
    /** Crea la llave de [userId] (reemplazando la anterior) y devuelve un `Cipher` de cifrado; el Keystore elige el IV. */
    fun newEncryptCipher(userId: UUID): Cipher

    /**
     * `Cipher` de descifrado con [iv] sobre la llave de [userId].
     * @throws BiometricKeyInvalidatedException si la llave se invalidó o ya no existe.
     */
    fun decryptCipher(userId: UUID, iv: ByteArray): Cipher

    /** Borra la llave de [userId], si existe. */
    fun delete(userId: UUID)

    /** Borra todas las llaves biométricas de la app (cerrar sesión). */
    fun deleteAll()
}

/**
 * Implementación sobre `AndroidKeyStore`:
 * - `setUserAuthenticationRequired(true)` con autenticación **por uso** y **solo `BIOMETRIC_STRONG`**: el PIN o el
 *   patrón del teléfono nunca abren la bóveda;
 * - `setInvalidatedByBiometricEnrollment(true)`: registrar otra huella la invalida;
 * - StrongBox si el teléfono lo tiene, sin fallar si no.
 */
@Singleton
class AndroidBiometricKeyStore @Inject constructor() : BiometricKeyStore {

    @Synchronized
    override fun newEncryptCipher(userId: UUID): Cipher {
        val alias = aliasOf(userId)
        keyStore().deleteEntry(alias)
        val key = generate(alias)
        return Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }
    }

    @Synchronized
    override fun decryptCipher(userId: UUID, iv: ByteArray): Cipher {
        val key = (keyStore().getEntry(aliasOf(userId), null) as? KeyStore.SecretKeyEntry)?.secretKey
            ?: throw BiometricKeyInvalidatedException()
        return try {
            Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv)) }
        } catch (_: KeyPermanentlyInvalidatedException) {
            throw BiometricKeyInvalidatedException()
        }
    }

    @Synchronized
    override fun delete(userId: UUID) {
        keyStore().deleteEntry(aliasOf(userId))
    }

    @Synchronized
    override fun deleteAll() {
        val store = keyStore()
        store.aliases().toList().filter { it.startsWith(ALIAS_PREFIX) }.forEach { store.deleteEntry(it) }
    }

    private fun generate(alias: String): SecretKey {
        val strongBox = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
        return try {
            generate(alias, strongBox = strongBox)
        } catch (_: StrongBoxUnavailableException) {
            generate(alias, strongBox = false)
        }
    }

    private fun generate(alias: String, strongBox: Boolean): SecretKey {
        val builder = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Autenticación en cada uso (timeout 0) y solo biometría fuerte.
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(-1)
        }
        if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) builder.setIsStrongBoxBacked(true)
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(builder.build())
            generateKey()
        }
    }

    private fun aliasOf(userId: UUID) = "$ALIAS_PREFIX$userId"

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS_PREFIX = "end-security/bio|"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BiometricKeyStoreModule {
    @Binds
    abstract fun bindBiometricKeyStore(impl: AndroidBiometricKeyStore): BiometricKeyStore
}
