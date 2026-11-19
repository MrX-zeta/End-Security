package com.luis.end_security.core.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/** Detiene la sincronización y, con el motor en reposo, ejecuta el borrado de los datos locales (cerrar sesión). */
interface SyncShutdown {
    suspend fun stop(whileIdle: suspend () -> Unit)
}

/**
 * Cuándo sincronizar (tarea 12). Dispara [SyncEngine] en primer plano:
 * - al desbloquear la bóveda;
 * - al guardar o eliminar, con 2 s de espera para juntar cambios ([SyncTrigger.changed]);
 * - al volver a primer plano si pasaron más de 5 min desde la última;
 * - cuando vuelve la red, o cuando Android la da por usable (`VALIDATED`); solo dispara el reintento, nunca es la verdad: lo que manda es la respuesta del servidor.
 * Con un `5xx` reintenta con espera exponencial (30 s, 1 min, 2 min … hasta 15 min). Sin conexión reintenta más corto (15 s, 30 s, 1 min, hasta
 * 2 min), solo con la app en primer plano y la bóveda abierta; un cambio o el regreso de la red cortan la espera ([SyncLoop]).
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
) : SyncTrigger, SyncShutdown, DefaultLifecycleObserver {

    private val lock = Any()
    private var job: Job? = null
    private var running = false
    private var rerun = false
    private var closing = false

    @Volatile
    private var foreground = false
    private val wake = Channel<Unit>(Channel.CONFLATED)

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

                    // Con red débil `onAvailable` llega antes de que Internet sirva: el sistema avisa después con VALIDATED.
                    override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                        if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) && engine.offline.value) requestNow()
                    }
                },
            )
        }
    }

    /**
     * Cerrar sesión: se cancela lo que espera o corre, se quita la ejecución periódica y se espera a que el motor
     * quede libre. Después, `clearAllTables()` no compite con una sincronización que escriba en Room.
     */
    override suspend fun stop(whileIdle: suspend () -> Unit) {
        val running = synchronized(lock) {
            // Mientras se cierra, ningún disparador (cambio, red, primer plano, desbloqueo) arranca otra pasada.
            closing = true
            rerun = false
            job?.also { it.cancel() }
        }
        try {
            SyncWorker.cancel(context)
            running?.join()
            // El borrado va **dentro** del mutex del motor: ninguna sincronización puede escribir en Room mientras tanto.
            engine.whileIdle(whileIdle)
        } finally {
            synchronized(lock) { closing = false }
        }
    }

    override fun changed() = request(CHANGE_DEBOUNCE_MILLIS)

    fun requestNow() = request(0)

    override fun onStop(owner: LifecycleOwner) {
        foreground = false
    }

    override fun onStart(owner: LifecycleOwner) {
        foreground = true
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
            if (closing) return
            if (running) {
                rerun = true
                wake.trySend(Unit) // si espera para reintentar sin conexión, reintenta ya
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
        wake.tryReceive() // un aviso viejo no debe saltarse la primera espera
        try {
            SyncLoop(
                sync = engine::sync,
                // Sin conexión solo se insiste con la app a la vista y la bóveda abierta; con la app cerrada lo cubre SyncWorker.
                canRetryOffline = { foreground && !synchronized(lock) { closing } && session.state.value is VaultState.Unlocked },
                takeRerun = { synchronized(lock) { rerun.also { rerun = false } } },
                waitOffline = { millis -> withTimeoutOrNull(millis) { wake.receive() } },
                waitServer = { millis -> delay(millis) },
            ).run()
        } finally {
            synchronized(lock) { running = false }
        }
    }

    companion object {
        const val CHANGE_DEBOUNCE_MILLIS = 2_000L
        const val FOREGROUND_STALE_MILLIS = 5 * 60_000L
        const val BACKOFF_START_MILLIS = 30_000L
        const val BACKOFF_MAX_MILLIS = 15 * 60_000L
        const val OFFLINE_START_MILLIS = 15_000L
        const val OFFLINE_MAX_MILLIS = 2 * 60_000L
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

    @Binds
    abstract fun bindSyncShutdown(impl: SyncScheduler): SyncShutdown
}
