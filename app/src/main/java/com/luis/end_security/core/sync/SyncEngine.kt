package com.luis.end_security.core.sync

import com.luis.end_security.core.common.ApiError
import com.luis.end_security.core.common.ApiResult
import com.luis.end_security.core.common.DeviceClock
import com.luis.end_security.core.crypto.Sealed
import com.luis.end_security.core.database.dao.AccountDao
import com.luis.end_security.core.database.dao.RemoteRow
import com.luis.end_security.core.database.dao.VaultItemDao
import com.luis.end_security.core.database.entity.VaultItemEntity
import com.luis.end_security.feature.vault.domain.RemoteVaultItem
import com.luis.end_security.feature.vault.domain.VaultApi
import com.luis.end_security.feature.vault.domain.WriteResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Cómo terminó una ejecución del motor de sincronización. */
sealed interface SyncOutcome {
    /** Todo subido y al día. */
    data object Synced : SyncOutcome

    /** Sin red: se reintenta cuando vuelva, sin aviso intrusivo (DEC-58). */
    data object Offline : SyncOutcome

    /** Un `5xx` u otro fallo pasajero: reintentar con espera exponencial. */
    data object RetryLater : SyncOutcome

    /** No hay cuenta o la sesión se cerró: no hay nada que sincronizar. */
    data object NotSignedIn : SyncOutcome
}

/** Avisa que algo cambió en Room y conviene sincronizar pronto (el repositorio lo llama al guardar o eliminar). */
interface SyncTrigger {
    fun changed()
}

/** ¿Este elemento se está subiendo ahora mismo? Si sí, borrarlo no puede quitar la fila (queda lápida). */
interface UploadGuard {
    fun isUploading(id: UUID): Boolean
}

/** Si la última sincronización falló por falta de red (SCR-44). */
interface SyncStatus {
    val offline: StateFlow<Boolean>
}

/**
 * Motor de sincronización (OP-33, OP-34; modelo de datos §8.2). **Solo mueve bloques cifrados**: nunca
 * descifra nada ni usa la `vault_key`, así que corre igual con la bóveda bloqueada (DEC-47, `SyncWorker`).
 *
 * Una sola ejecución a la vez ([Mutex]):
 * 1. **Subir** los `pending` de uno en uno, en orden de `local_seq`, con `PUT` o `DELETE` y la versión esperada.
 * 2. **Bajar** los cambios desde `sync_cursor`, paginados, aplicándolos con [VaultItemDao.applyRemotePage]
 *    (filas y cursor en la misma transacción de Room).
 *
 * Respuestas de la subida: `created`/`updated` → `synced` solo si el `local_seq` no cambió; `deleted` → se borra
 * la fila; `409` → `conflict`; `404` al editar → se vuelve a crear. `410 cursor_expired` → descarga completa
 * y reconciliación sin perder `pending` ni `conflict`.
 */
@Singleton
class SyncEngine @Inject constructor(
    private val api: VaultApi,
    private val items: VaultItemDao,
    private val accounts: AccountDao,
    private val clock: DeviceClock,
) : UploadGuard, SyncStatus {

    private val mutex = Mutex()
    private val inFlight: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val _offline = MutableStateFlow(false)
    override val offline: StateFlow<Boolean> = _offline.asStateFlow()

    override fun isUploading(id: UUID): Boolean = id.toString() in inFlight

    /** Espera a que no haya una sincronización en curso y ejecuta [block] sin que empiece otra (cerrar sesión). */
    suspend fun <T> whileIdle(block: suspend () -> T): T = mutex.withLock { block() }

    suspend fun sync(): SyncOutcome = mutex.withLock {
        val outcome = run()
        when (outcome) {
            SyncOutcome.Synced -> _offline.value = false
            SyncOutcome.Offline -> _offline.value = true
            else -> Unit
        }
        outcome
    }

    private sealed interface Step {
        data object Continue : Step

        class Stop(val outcome: SyncOutcome) : Step
    }

    private suspend fun run(): SyncOutcome {
        if (accounts.get() == null) return SyncOutcome.NotSignedIn
        (upload() as? Step.Stop)?.let { return it.outcome }
        (download() as? Step.Stop)?.let { return it.outcome }
        return SyncOutcome.Synced
    }

    // --- Subir pendientes (OP-34) ---

    private suspend fun upload(): Step {
        // Cada (elemento, local_seq) se intenta una vez por ejecución: así un rechazo que no cambia nada no hace un ciclo.
        val attempted = mutableSetOf<Pair<String, Long>>()
        while (true) {
            val next = items.pendingRows().firstOrNull { (it.id to it.localSeq) !in attempted } ?: return Step.Continue
            attempted += next.id to next.localSeq
            val step = uploadOne(next)
            if (step is Step.Stop) return step
        }
    }

    private suspend fun uploadOne(row: VaultItemEntity): Step {
        val id = row.id
        // Primero se marca «en curso» y después se lee la fila: un borrado que llegue desde ahora deja lápida.
        inFlight += id
        try {
            val fresh = items.get(id)?.takeIf { it.syncState == com.luis.end_security.core.database.entity.SyncState.Pending }
                ?: return Step.Continue
            val uuid = runCatching { UUID.fromString(id) }.getOrNull() ?: return Step.Continue
            return if (fresh.ciphertext == null) uploadTombstone(fresh, uuid) else uploadLive(fresh, uuid, allowRecreate = true)
        } finally {
            inFlight -= id
        }
    }

    private suspend fun uploadLive(row: VaultItemEntity, uuid: UUID, allowRecreate: Boolean): Step {
        val nonce = row.nonce
        val ciphertext = row.ciphertext
        val sealed = try {
            Sealed(requireNotNull(nonce), requireNotNull(ciphertext))
        } catch (_: IllegalArgumentException) {
            return Step.Continue // fila corrupta: no se puede subir; la lista la muestra como dañada
        }
        return when (val result = api.putItem(uuid, row.serverVersion, row.formatVersion, sealed)) {
            is ApiResult.Success -> {
                val written = result.value
                items.markUploaded(row.id, row.localSeq, written.version, written.revision)
                // Si la fila se borró físicamente mientras subía, el servidor quedó con un elemento que ya no existe aquí.
                if (items.get(row.id) == null) rescueDelete(row, uuid, written)
                Step.Continue
            }
            is ApiResult.Failure -> when (val error = result.error) {
                is ApiError.VersionConflict -> {
                    items.markConflict(row.id)
                    Step.Continue
                }
                ApiError.NotFound -> if (row.serverVersion != null && allowRecreate) {
                    // Se purgó en el servidor: sin versión, se vuelve a crear (§8.2).
                    items.clearServerVersion(row.id)
                    uploadLive(row.withoutServerVersion(), uuid, allowRecreate = false)
                } else {
                    Step.Continue
                }
                ApiError.ValidationFailed -> Step.Continue
                else -> Step.Stop(error.toOutcome())
            }
        }
    }

    /**
     * La fila se borró mientras subía y el servidor ya la tiene: se le manda el DELETE. Si no llega a confirmarse (sin
     * red, 5xx, cancelación), **no se ignora**: queda una lápida `pending` con la versión del servidor y la siguiente
     * ejecución la reintenta; así el elemento eliminado no reaparece en la siguiente descarga.
     */
    private suspend fun rescueDelete(row: VaultItemEntity, uuid: UUID, written: WriteResult) {
        // Primero la lápida y sin poder cancelarse: si algo corta el DELETE (red, WorkManager, cerrar sesión), ya está
        // escrita y la siguiente ejecución la reintenta; el elemento eliminado no reaparece.
        withContext(NonCancellable) {
            val now = clock.wallMillis()
            items.insert(
                VaultItemEntity(
                    row.id, row.formatVersion, null, null, written.version, written.revision, row.localSeq + 1,
                    com.luis.end_security.core.database.entity.SyncState.Pending, now, now,
                ),
            )
        }
        val result = api.deleteItem(uuid, written.version)
        val done = result is ApiResult.Success || (result is ApiResult.Failure && result.error == ApiError.NotFound)
        if (done) withContext(NonCancellable) { items.removeTombstone(row.id) }
    }

    private suspend fun uploadTombstone(row: VaultItemEntity, uuid: UUID): Step {
        val version = row.serverVersion
        if (version == null) {
            // Nunca llegó al servidor: no hay nada que borrar allá.
            items.removeTombstone(row.id)
            return Step.Continue
        }
        return when (val result = api.deleteItem(uuid, version)) {
            is ApiResult.Success -> {
                items.removeTombstone(row.id)
                Step.Continue
            }
            is ApiResult.Failure -> when (val error = result.error) {
                is ApiError.VersionConflict -> {
                    items.markConflict(row.id)
                    Step.Continue
                }
                // Ya no existe en el servidor: la lápida local cumplió su trabajo.
                ApiError.NotFound -> {
                    items.removeTombstone(row.id)
                    Step.Continue
                }
                ApiError.ValidationFailed -> Step.Continue
                else -> Step.Stop(error.toOutcome())
            }
        }
    }

    // --- Bajar cambios (OP-33) ---

    private suspend fun download(): Step {
        var since = accounts.get()?.syncCursor ?: return Step.Stop(SyncOutcome.NotSignedIn)
        // El cursor con que se empezó: durante una descarga completa no avanza hasta la última página, así que si se
        // interrumpe a la mitad la siguiente ejecución vuelve a recibir `410` y empieza la descarga completa desde cero.
        val startCursor = since
        var full = false
        val remoteIds = mutableSetOf<String>()
        while (true) {
            when (val result = api.listChanges(since)) {
                is ApiResult.Failure -> {
                    if (result.error == ApiError.CursorExpired && !full) {
                        // 410 (DEC-40): descarga completa y reconciliación; pending y conflict no se pierden.
                        full = true
                        since = 0
                        remoteIds.clear()
                        continue
                    }
                    return Step.Stop(result.error.toOutcome())
                }
                is ApiResult.Success -> {
                    val page = result.value
                    // Un servidor que dice «hay más» sin avanzar el cursor no debe dejarnos dando vueltas.
                    if (page.hasMore && page.nextSince <= since) return Step.Stop(SyncOutcome.RetryLater)
                    if (full) page.items.forEach { remoteIds += it.id.toString() }
                    items.applyRemotePage(
                        changes = page.items.map { it.toRow() },
                        cursor = if (full && page.hasMore) startCursor else page.nextSince,
                        now = clock.wallMillis(),
                        keepOnly = if (full && !page.hasMore) remoteIds else null,
                    )
                    since = page.nextSince
                    if (!page.hasMore) return Step.Continue
                }
            }
        }
    }

    // --- Ayudantes ---

    private fun ApiError.toOutcome(): SyncOutcome = when (this) {
        ApiError.NetworkUnavailable -> SyncOutcome.Offline
        // `session_revoked` ya cierra la sesión local por su cuenta (TokenManager); aquí solo se corta la ejecución.
        ApiError.SessionRevoked -> SyncOutcome.NotSignedIn
        else -> SyncOutcome.RetryLater
    }

    private fun VaultItemEntity.withoutServerVersion() = VaultItemEntity(
        id, formatVersion, nonce, ciphertext, null, null, localSeq, syncState, deletedAt, updatedAt,
    )

    private fun RemoteVaultItem.toRow() = RemoteRow(
        id = id.toString(),
        version = version,
        revision = revision,
        formatVersion = formatVersion,
        nonce = sealed?.nonce,
        ciphertext = sealed?.ciphertext,
        deleted = deleted,
        updatedAt = updatedAt.toEpochMilli(),
    )
}
