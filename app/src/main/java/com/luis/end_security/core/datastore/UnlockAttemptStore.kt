package com.luis.end_security.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import com.luis.end_security.core.common.DeviceClock
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration

/**
 * Fallos seguidos en Desbloquear y fin del bloqueo (DEC-08), en el archivo `settings` de
 * DataStore para que cerrar la app no los reinicie. El fin se guarda en los dos relojes de
 * [DeviceClock]; [UnlockLockout] decide cuál manda. No guarda nada secreto.
 */
@Singleton
class UnlockAttemptStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val clock: DeviceClock,
) {
    /** Lo que falta del bloqueo; [Duration.ZERO] si se puede intentar. */
    suspend fun remainingLockout(): Duration {
        val preferences = dataStore.data
            .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
            .first()
        return UnlockLockout.remaining(
            failures = preferences[Keys.FAILURES] ?: 0,
            lockedUntilWallMillis = preferences[Keys.LOCKED_UNTIL_WALL] ?: 0L,
            lockedUntilElapsedMillis = preferences[Keys.LOCKED_UNTIL_ELAPSED] ?: 0L,
            nowWallMillis = clock.wallMillis(),
            nowElapsedMillis = clock.elapsedRealtimeMillis(),
        )
    }

    /** Cuenta un fallo y devuelve el bloqueo que empieza ahora ([Duration.ZERO] si no hay). */
    suspend fun recordFailure(): Duration {
        var lockout = Duration.ZERO
        try {
            dataStore.edit { preferences ->
                val failures = (preferences[Keys.FAILURES] ?: 0) + 1
                lockout = UnlockLockout.durationAfter(failures)
                preferences[Keys.FAILURES] = failures
                if (lockout > Duration.ZERO) {
                    preferences[Keys.LOCKED_UNTIL_WALL] = clock.wallMillis() + lockout.inWholeMilliseconds
                    preferences[Keys.LOCKED_UNTIL_ELAPSED] = clock.elapsedRealtimeMillis() + lockout.inWholeMilliseconds
                }
            }
        } catch (_: IOException) {
            // Sin disco no se puede contar; el costo de Argon2 por intento sigue frenando.
        }
        return lockout
    }

    /** Contraseña correcta: se olvidan los fallos. */
    suspend fun recordSuccess() {
        try {
            dataStore.edit { preferences ->
                preferences.remove(Keys.FAILURES)
                preferences.remove(Keys.LOCKED_UNTIL_WALL)
                preferences.remove(Keys.LOCKED_UNTIL_ELAPSED)
            }
        } catch (_: IOException) {
            // Quedan los fallos viejos: en el peor caso el siguiente error bloquea antes.
        }
    }

    private object Keys {
        val FAILURES = intPreferencesKey("unlock_failures")
        val LOCKED_UNTIL_WALL = longPreferencesKey("unlock_locked_until_wall")
        val LOCKED_UNTIL_ELAPSED = longPreferencesKey("unlock_locked_until_elapsed")
    }
}
