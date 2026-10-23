package com.luis.end_security.core.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SyncWorkerEntryPoint {
    fun syncEngine(): SyncEngine
}

/**
 * Sincronización periódica con la app cerrada (cada 15 min, con red). Puede correr **con la bóveda bloqueada**:
 * solo mueve bloques cifrados y marca `conflict`, sin la `vault_key` (DEC-47). Un fallo de red o un `5xx` se
 * reintenta con la espera exponencial de WorkManager.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val engine = EntryPointAccessors.fromApplication(applicationContext, SyncWorkerEntryPoint::class.java).syncEngine()
        return when (engine.sync()) {
            SyncOutcome.Synced, SyncOutcome.NotSignedIn -> Result.success()
            SyncOutcome.Offline, SyncOutcome.RetryLater -> Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_NAME = "end_security_sync"

        /** Programa la ejecución periódica (una sola, aunque se llame en cada arranque). */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** Al cerrar sesión no queda nada que sincronizar. */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        }
    }
}
