package com.luis.end_security.core.sync

/**
 * La repetición de una pasada de sincronización (sin Android, para probarla con tiempo virtual):
 * - `RetryLater` (5xx): espera 30 s, 1 min, 2 min … hasta 15 min y reintenta;
 * - `Offline`: espera 15 s, 30 s, 1 min, hasta 2 min y reintenta, mientras [canRetryOffline] lo permita (app a la vista, bóveda
 *   abierta, sin cerrar sesión). [waitOffline] puede volver antes si algo pide sincronizar ya;
 * - `Synced` y `NotSignedIn` terminan; si pidieron otra pasada mientras corría ([takeRerun]), se repite.
 */
internal class SyncLoop(
    private val sync: suspend () -> SyncOutcome,
    private val canRetryOffline: () -> Boolean,
    private val takeRerun: () -> Boolean,
    private val waitOffline: suspend (Long) -> Unit,
    private val waitServer: suspend (Long) -> Unit,
) {
    suspend fun run() {
        var serverWait = SyncScheduler.BACKOFF_START_MILLIS
        var offlineWait = SyncScheduler.OFFLINE_START_MILLIS
        while (true) {
            when (sync()) {
                SyncOutcome.RetryLater -> {
                    waitServer(serverWait)
                    serverWait = (serverWait * 2).coerceAtMost(SyncScheduler.BACKOFF_MAX_MILLIS)
                    continue
                }
                SyncOutcome.Offline -> {
                    if (!canRetryOffline()) return
                    waitOffline(offlineWait)
                    offlineWait = (offlineWait * 2).coerceAtMost(SyncScheduler.OFFLINE_MAX_MILLIS)
                    // Tras esperar puede haberse cerrado sesión o bloqueado: no se sincroniza una vez más.
                    if (!canRetryOffline()) return
                    continue
                }
                else -> if (!takeRerun()) return
            }
        }
    }
}
