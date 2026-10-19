package com.luis.end_security.core.session

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.luis.end_security.core.common.ApplicationScope
import com.luis.end_security.core.common.DeviceClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** El ajuste `autolock_minutes` (KV-DATASTORE). */
interface AutoLockSettings {
    /** 1, 5, 15 o 30 min; 0 = al salir de la app. */
    val autolockMinutes: Flow<Int>

    companion object {
        /** DEC-26 / PEND-04. */
        const val DEFAULT_MINUTES = 5

        val ALLOWED_MINUTES = setOf(0, 1, 5, 15, 30)

        /** Un valor fuera de las opciones (error o migración) no debe desactivar el autobloqueo. */
        fun sanitize(minutes: Int?): Int = minutes?.takeIf { it in ALLOWED_MINUTES } ?: DEFAULT_MINUTES
    }
}

/**
 * Autobloqueo (DEC-26, PEND-04): al pasar la app a segundo plano empieza la cuenta de
 * `autolock_minutes`; al volver antes de tiempo se cancela. Con 0 bloquea al salir.
 *
 * - Al volver se compara con `elapsedRealtime` (cuenta también el tiempo dormido): Android
 *   congela las apps en segundo plano y el temporizador puede no dispararse a tiempo, pero la
 *   bóveda se bloquea antes de que se vea.
 * - El plazo también se deja en [VaultSession], que lo revisa antes de prestar la llave.
 * - Si la bóveda se abre con la app ya en segundo plano (el desbloqueo terminó después de salir),
 *   la cuenta empieza en ese momento.
 *
 * Lo registra `EndSecurityApp` en `ProcessLifecycleOwner`.
 */
@Singleton
class AutoLocker @Inject constructor(
    private val session: VaultSession,
    settings: AutoLockSettings,
    private val clock: DeviceClock,
    @param:ApplicationScope private val scope: CoroutineScope,
) : DefaultLifecycleObserver {

    private val minutes = settings.autolockMinutes.stateIn(scope, SharingStarted.Eagerly, AutoLockSettings.DEFAULT_MINUTES)

    private var inForeground = false
    private var backgroundedAt: Long? = null
    private var timeoutMillis = 0L
    private var timer: Job? = null

    init {
        scope.launch {
            session.state.collect { state ->
                if (state is VaultState.Unlocked) onUnlocked()
            }
        }
    }

    override fun onStop(owner: LifecycleOwner) = onBackground()

    override fun onStart(owner: LifecycleOwner) = onForeground()

    @Synchronized
    fun onBackground() {
        inForeground = false
        startCountdown()
    }

    @Synchronized
    fun onForeground() {
        inForeground = true
        timer?.cancel()
        timer = null
        session.setLockDeadline(null)
        val since = backgroundedAt ?: return
        backgroundedAt = null
        if (clock.elapsedRealtimeMillis() - since >= timeoutMillis) session.lock()
    }

    /** La bóveda se abrió: si la app ya no está a la vista, la cuenta empieza ahora. */
    @Synchronized
    private fun onUnlocked() {
        if (!inForeground && timer?.isActive != true) startCountdown()
    }

    // Llamar con el monitor tomado.
    private fun startCountdown() {
        timer?.cancel()
        timer = null
        if (session.state.value is VaultState.Locked) {
            backgroundedAt = null
            return
        }
        timeoutMillis = AutoLockSettings.sanitize(minutes.value) * 60_000L
        val now = clock.elapsedRealtimeMillis()
        backgroundedAt = now
        if (timeoutMillis == 0L) {
            session.lock()
            return
        }
        session.setLockDeadline(now + timeoutMillis)
        val timeout = timeoutMillis
        timer = scope.launch {
            delay(timeout)
            session.lock()
        }
    }
}
