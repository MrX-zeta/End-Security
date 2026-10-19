package com.luis.end_security.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import com.luis.end_security.core.session.AutoLockSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ajustes locales sin secretos (KV-DATASTORE), en el archivo `settings`. Los intentos de
 * Desbloquear están en [UnlockAttemptStore] (mismo archivo); los tokens irán en un TokenStore
 * aparte, cifrados con el Keystore (DEC-16).
 */
@Singleton
class SettingsStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : AutoLockSettings {
    /** `true` cuando el usuario ya vio el onboarding (SCR-01…SCR-03). */
    val onboardingCompleted: Flow<Boolean> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { preferences -> preferences[Keys.ONBOARDING_COMPLETED] ?: false }

    /**
     * `true` si el usuario activó el desbloqueo con huella (DEC-09). Desactivado por defecto;
     * se activa desde Ajustes cuando exista OP-20.
     */
    val biometricEnabled: Flow<Boolean> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { preferences -> preferences[Keys.BIOMETRIC_ENABLED] ?: false }

    /** Minutos de autobloqueo (DEC-26): 1, 5, 15, 30 o 0 = al salir; 5 por defecto. */
    override val autolockMinutes: Flow<Int> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { preferences -> AutoLockSettings.sanitize(preferences[Keys.AUTOLOCK_MINUTES]) }

    suspend fun setOnboardingCompleted() {
        dataStore.edit { preferences -> preferences[Keys.ONBOARDING_COMPLETED] = true }
    }

    private object Keys {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val AUTOLOCK_MINUTES = intPreferencesKey("autolock_minutes")
    }
}
