package com.luis.end_security.core.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.luis.end_security.core.common.ApplicationScope
import com.luis.end_security.core.common.DeviceClock
import com.luis.end_security.core.database.dao.AccountDao
import com.luis.end_security.core.session.VaultSession
import com.luis.end_security.core.session.VaultState
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cuándo sincronizar (tarea 12). Dispara [SyncEngine] en primer plano:
 * - al desbloquear la bóveda;
 * - al guardar o eliminar, con 2 s de espera para juntar cambios ([SyncTrigger.changed]);
 * - al volver a primer plano si pasaron más de 5 min desde la última;
 * - cuando vuelve la red (solo para disparar el reintento, nunca como verdad: lo que manda es la respuesta del servidor).
 * Con un `5xx` reintenta con espera exponencial (30 s, 1 min, 2 min … hasta 15 min).
 * La ejecución periódica con la app cerrada es de [SyncWorker].
 */
@Singleton
class SyncScheduler @Inject constructor(
    private val engine: SyncEngine,
    private val accounts: AccountDao,
    private val session: VaultSession,
    private val clock: DeviceClock,
    @param:ApplicationScope private val scope: CoroutineScope,
    @param:ApplicationContext private val context: Context,
) : SyncTrigger, DefaultLifecycleObserver {

    private val lock = Any()
    private var job: Job? = null
    private var running = false
    private var rerun = false

    init {
        var previous: VaultState = session.state.value
        scope.launch {
            session.state.collect { state ->
                if (previous is VaultState.Locked && state is VaultState.Unlocked) requestNow()
                previous = state
            }
        }
        // Volvió la red: reintentar. Si no hay permiso o servicio, se sigue sin esto (el Worker y los demás disparadores cubren).
        runCatching {
            val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            manager.registerDefaultNetworkCallback(
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        if (engine.offline.value) requestNow()
                    }
                },
            )
        }
    }

    override fun changed() = request(CHANGE_DEBOUNCE_MILLIS)

    fun requestNow() = request(0)

    override fun onStart(owner: LifecycleOwner) {
        scope.launch {
            val last = accounts.get()?.lastSyncAt
            if (last == null || clock.wallMillis() - last > FOREGROUND_STALE_MILLIS) requestNow()
        }
    }

    /**
     * Solo se cancela la **espera** del debounce. Una sincronización que ya empezó no se interrumpe (cortar un
     * `PUT` a la mitad dejaría la fila `pending` con un servidor que ya la aceptó): un cambio nuevo pide una
     * pasada más cuando termine.
     */
    private fun request(delayMillis: Long) {
        synchronized(lock) {
            if (running) {
                rerun = true
                return
            }
            job?.cancel()
            job = scope.launch {
                delay(delayMillis)
                runSyncs()
            }
        }
    }

    private suspend fun runSyncs() {
        synchronized(lock) {
            running = true
            rerun = false
        }
        try {
            var wait = BACKOFF_START_MILLIS
            while (true) {
                val outcome = engine.sync()
                if (outcome == SyncOutcome.RetryLater) {
                    delay(wait)
                    wait = (wait * 2).coerceAtMost(BACKOFF_MAX_MILLIS)
                    continue
                }
                val again = synchronized(lock) { rerun.also { rerun = false } }
                if (!again) return
            }
        } finally {
            synchronized(lock) { running = false }
        }
    }

    companion object {
        const val CHANGE_DEBOUNCE_MILLIS = 2_000L
        const val FOREGROUND_STALE_MILLIS = 5 * 60_000L
        const val BACKOFF_START_MILLIS = 30_000L
        const val BACKOFF_MAX_MILLIS = 15 * 60_000L
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {

    @Binds
    abstract fun bindUploadGuard(impl: SyncEngine): UploadGuard

    @Binds
    abstract fun bindSyncStatus(impl: SyncEngine): SyncStatus

    @Binds
    abstract fun bindSyncTrigger(impl: SyncScheduler): SyncTrigger
}
