package com.luis.end_security.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import com.luis.end_security.core.crypto.GeneratorOptions
import com.luis.end_security.core.security.ClipboardSettings
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
) : AutoLockSettings, ClipboardSettings {
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

    /** Segundos hasta borrar lo copiado del portapapeles (DEC-25): 15, 30, 60 o 120; 30 por defecto. */
    override val clipboardClearSeconds: Flow<Int> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { preferences -> ClipboardSettings.sanitize(preferences[Keys.CLIPBOARD_CLEAR_SECONDS]) }

    /** Opciones del Generador (DEC-24): se recuerdan; la contraseña generada nunca se guarda. */
    val generatorOptions: Flow<GeneratorOptions> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { preferences ->
            GeneratorOptions(
                length = preferences[Keys.GENERATOR_LENGTH] ?: GeneratorOptions.DEFAULT_LENGTH,
                uppercase = preferences[Keys.GENERATOR_UPPER] ?: true,
                digits = preferences[Keys.GENERATOR_DIGITS] ?: true,
                symbols = preferences[Keys.GENERATOR_SYMBOLS] ?: true,
                avoidAmbiguous = preferences[Keys.GENERATOR_AVOID_AMBIGUOUS] ?: false,
            ).sanitized()
        }

    suspend fun setGeneratorOptions(options: GeneratorOptions) {
        dataStore.edit { preferences ->
            preferences[Keys.GENERATOR_LENGTH] = options.length
            preferences[Keys.GENERATOR_UPPER] = options.uppercase
            preferences[Keys.GENERATOR_DIGITS] = options.digits
            preferences[Keys.GENERATOR_SYMBOLS] = options.symbols
            preferences[Keys.GENERATOR_AVOID_AMBIGUOUS] = options.avoidAmbiguous
        }
    }

    suspend fun setOnboardingCompleted() {
        dataStore.edit { preferences -> preferences[Keys.ONBOARDING_COMPLETED] = true }
    }

    private object Keys {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val AUTOLOCK_MINUTES = intPreferencesKey("autolock_minutes")
        val CLIPBOARD_CLEAR_SECONDS = intPreferencesKey("clipboard_clear_seconds")
        val GENERATOR_LENGTH = intPreferencesKey("generator_length")
        val GENERATOR_UPPER = booleanPreferencesKey("generator_upper")
        val GENERATOR_DIGITS = booleanPreferencesKey("generator_digits")
        val GENERATOR_SYMBOLS = booleanPreferencesKey("generator_symbols")
        val GENERATOR_AVOID_AMBIGUOUS = booleanPreferencesKey("generator_avoid_ambiguous")
    }
}
