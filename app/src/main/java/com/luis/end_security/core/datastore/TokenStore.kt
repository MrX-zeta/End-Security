package com.luis.end_security.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.luis.end_security.core.crypto.keystore.TokenKeyManager
import com.luis.end_security.core.crypto.wipe
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.io.IOException
import java.security.GeneralSecurityException
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/** Los dos tokens de la sesión (DEC-16). Opacos; nunca en logs. */
class StoredTokens(val accessToken: String, val refreshToken: String) {
    override fun toString(): String = "StoredTokens(***)"
}

/**
 * `access_token` y `refresh_token` (KV-DATASTORE) cifrados con [TokenKeyManager] (Keystore) en el
 * archivo `settings` de DataStore. Sin `EncryptedSharedPreferences` (DEC-16).
 */
@Singleton
class TokenStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val keyManager: TokenKeyManager,
) {

    /** `null` sin sesión o si no se pudieron descifrar (llave borrada o bloque alterado). */
    suspend fun read(): StoredTokens? {
        val preferences = dataStore.data
            .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
            .first()
        val access = preferences[Keys.ACCESS] ?: return null
        val refresh = preferences[Keys.REFRESH] ?: return null
        return try {
            StoredTokens(open(access), open(refresh))
        } catch (_: GeneralSecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: java.security.ProviderException) {
            // Keystore invalidado o no disponible: sin sesión, en lugar de cerrar la app.
            null
        }
    }

    suspend fun save(tokens: StoredTokens) {
        val access = seal(tokens.accessToken)
        val refresh = seal(tokens.refreshToken)
        dataStore.edit { preferences ->
            preferences[Keys.ACCESS] = access
            preferences[Keys.REFRESH] = refresh
        }
    }

    suspend fun clear() {
        try {
            dataStore.edit { preferences ->
                preferences.remove(Keys.ACCESS)
                preferences.remove(Keys.REFRESH)
            }
        } finally {
            keyManager.deleteKey()
        }
    }

    private fun seal(token: String): String {
        val plaintext = token.encodeToByteArray()
        try {
            return Base64.getEncoder().encodeToString(keyManager.encrypt(plaintext))
        } finally {
            plaintext.wipe()
        }
    }

    private fun open(stored: String): String {
        val plaintext = keyManager.decrypt(Base64.getDecoder().decode(stored))
        try {
            return plaintext.decodeToString()
        } finally {
            plaintext.wipe()
        }
    }

    private object Keys {
        val ACCESS = stringPreferencesKey("access_token")
        val REFRESH = stringPreferencesKey("refresh_token")
    }
}
