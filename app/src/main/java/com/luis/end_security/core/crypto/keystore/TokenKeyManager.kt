package com.luis.end_security.core.crypto.keystore

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Llave AES-256-GCM del Android Keystore para cifrar los tokens en DataStore (DEC-16): no se
 * puede exportar y no pide huella (`setUserAuthenticationRequired(false)`), porque la app renueva
 * tokens sin el usuario. Sin `EncryptedSharedPreferences`. El nonce lo elige el Keystore en cada
 * cifrado y va delante del cifrado.
 */
@Singleton
class TokenKeyManager @Inject constructor() {

    fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val iv = cipher.iv
        check(iv.size == IV_LENGTH)
        return iv + cipher.doFinal(plaintext)
    }

    /** @throws javax.crypto.AEADBadTagException si el bloque se alteró o la llave cambió. */
    fun decrypt(blob: ByteArray): ByteArray {
        require(blob.size > IV_LENGTH + TAG_BITS / 8) { "Bloque de token inválido" }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, blob, 0, IV_LENGTH))
        }
        return cipher.doFinal(blob, IV_LENGTH, blob.size - IV_LENGTH)
    }

    /** Borra la llave (p. ej. al cerrar sesión); la siguiente se crea al volver a cifrar. */
    fun deleteKey() {
        keyStore().deleteEntry(ALIAS)
    }

    @Synchronized
    private fun key(): SecretKey {
        (keyStore().getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false)
            .setRandomizedEncryptionRequired(true)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(spec)
            generateKey()
        }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "end_security_tokens"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val TAG_BITS = 128
    }
}
