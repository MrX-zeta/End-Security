package com.luis.end_security.feature.vault.data

import com.luis.end_security.core.common.ApiError
import com.luis.end_security.core.common.ApiResult
import com.luis.end_security.core.common.DefaultDispatcher
import com.luis.end_security.core.common.DeviceClock
import com.luis.end_security.core.crypto.AuthenticationFailed
import com.luis.end_security.core.crypto.Sealed
import com.luis.end_security.core.database.dao.RemoteRow
import com.luis.end_security.core.database.dao.VaultItemDao
import com.luis.end_security.core.database.entity.SyncState
import com.luis.end_security.core.database.entity.VaultItemEntity
import com.luis.end_security.core.session.VaultLockedException
import com.luis.end_security.core.session.VaultSession
import com.luis.end_security.core.session.VaultState
import com.luis.end_security.core.sync.SyncTrigger
import com.luis.end_security.feature.vault.domain.ConflictDetail
import com.luis.end_security.feature.vault.domain.ConflictLoad
import com.luis.end_security.feature.vault.domain.ConflictRepository
import com.luis.end_security.feature.vault.domain.ConflictRow
import com.luis.end_security.feature.vault.domain.ItemDraft
import com.luis.end_security.feature.vault.domain.KeepVersion
import com.luis.end_security.feature.vault.domain.ResolveResult
import com.luis.end_security.feature.vault.domain.RemoteVaultItem
import com.luis.end_security.feature.vault.domain.SaveResult
import com.luis.end_security.feature.vault.domain.VaultApi
import com.luis.end_security.feature.vault.domain.VaultItem
import com.luis.end_security.feature.vault.domain.VaultRepository
import com.luis.end_security.feature.vault.domain.VersionSide
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton


/**
 * Resuelve los conflictos de sincronización (OP-46, DEC-57; modelo de datos §8.2). **Con la bóveda abierta**:
 * descarga la versión del servidor de cada elemento (EP-26), descifra las dos solo en memoria y, según lo
 * que la persona elija:
 * - **la del servidor:** reemplaza la fila local y queda `synced`;
 * - **la de este teléfono:** se vuelve a cifrar (nonce nuevo), `server_version` = la del servidor y queda
 *   `pending` para subirse; si el servidor la eliminó, se guarda como un elemento nuevo.
 * No se guardan copias ni versiones anteriores (FDA-11).
 */
@Singleton
class ConflictResolver @Inject constructor(
    private val dao: VaultItemDao,
    private val api: VaultApi,
    private val session: VaultSession,
    private val repository: VaultRepository,
    private val clock: DeviceClock,
    private val syncTrigger: SyncTrigger,
    @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ConflictRepository {

    private val _lastResolved = MutableStateFlow<String?>(null)
    override val lastResolved: StateFlow<String?> = _lastResolved.asStateFlow()

    override fun clearResolved() {
        _lastResolved.value = null
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeConflicts(): Flow<List<ConflictRow>> = session.state
        .flatMapLatest { state ->
            when (state) {
                VaultState.Locked -> {
                    _lastResolved.value = null
                    flowOf(emptyList())
                }
                is VaultState.Unlocked -> dao.observeConflicts().map { rows ->
                    // Si el autobloqueo llega mientras se descifra, la lista se vacía (como en VaultRepositoryImpl).
                    try {
                        rows.mapNotNull { toRow(it, state.userId) }
                    } catch (_: VaultLockedException) {
                        emptyList()
                    }
                }
            }
        }
        .flowOn(defaultDispatcher)

    override suspend fun load(id: UUID): ConflictLoad = withContext(defaultDispatcher) {
        val userId = (session.state.value as? VaultState.Unlocked)?.userId ?: return@withContext ConflictLoad.Locked
        val row = dao.get(id.toString())?.takeIf { it.syncState == SyncState.Conflict } ?: return@withContext ConflictLoad.Gone
        val remote = when (val result = api.getItem(id)) {
            is ApiResult.Success -> result.value
            is ApiResult.Failure -> return@withContext when (result.error) {
                ApiError.NetworkUnavailable -> ConflictLoad.Offline
                ApiError.NotFound -> {
                    // El servidor ya no lo tiene (purgado): no hay contra qué resolver; se vuelve a crear solo.
                    dao.reopenAsPending(row.id, row.localSeq)
                    syncTrigger.changed()
                    ConflictLoad.Gone
                }
                else -> ConflictLoad.Failed
            }
        }
        try {
            ConflictLoad.Ready(
                ConflictDetail(
                    id = id,
                    local = localSide(row, userId),
                    remote = remoteSide(remote, userId),
                    localSeq = row.localSeq,
                    remoteVersion = remote.version,
                    remoteRevision = remote.revision,
                    remoteFormatVersion = remote.formatVersion,
                    remoteSealed = remote.sealed,
                    remoteUpdatedAt = remote.updatedAt,
                ),
            )
        } catch (_: VaultLockedException) {
            ConflictLoad.Locked
        }
    }

    override suspend fun resolve(detail: ConflictDetail, keep: KeepVersion): ResolveResult = withContext(defaultDispatcher) {
        val userId = (session.state.value as? VaultState.Unlocked)?.userId ?: return@withContext ResolveResult.Locked
        val id = detail.id
        val localLive = detail.local as? VersionSide.Live
        val remoteLive = detail.remote as? VersionSide.Live
        // El aviso lleva el nombre de la versión que se conserva; si esa es una eliminación, el de la otra.
        val kept = if (keep == KeepVersion.Local) localLive else remoteLive
        val name = (kept ?: localLive ?: remoteLive)?.item?.name
        try {
            val done = when (keep) {
                KeepVersion.Remote -> when {
                    // Un bloque del servidor que no abre (alterado o de otro elemento) no reemplaza lo único que se lee.
                    // Una versión de formato más nueva sí se acepta: queda en solo lectura (DEC-04).
                    detail.remote is VersionSide.Unreadable && detail.remoteFormatVersion <= ItemCipher.FORMAT_VERSION ->
                        return@withContext ResolveResult.Unavailable
                    else -> dao.resolveWithRemote(detail.toRemoteRow(), detail.localSeq)
                }
                KeepVersion.Local -> when {
                    localLive != null && detail.remote is VersionSide.Deleted -> keepMineAsNew(detail, localLive.item)
                    localLive != null -> {
                        // La misma versión de este teléfono, con nonce nuevo (nunca se reutiliza uno).
                        val sealed = session.withVaultKey { key -> ItemCipher.encrypt(key, id, userId, localLive.item) }
                        dao.resolveKeepingLocal(
                            id.toString(), sealed.nonce, sealed.ciphertext, detail.remoteVersion, detail.remoteRevision,
                            detail.localSeq, clock.wallMillis(),
                        ) == 1
                    }
                    // Aquí se eliminó y allá cambió: conservar lo mío = que la eliminación llegue al servidor.
                    detail.local is VersionSide.Deleted ->
                        dao.resolveRekeep(id.toString(), detail.remoteVersion, detail.remoteRevision, detail.localSeq, clock.wallMillis()) == 1
                    else -> return@withContext ResolveResult.Unavailable
                }
            }
            if (!done) return@withContext ResolveResult.Stale
            // «» = se resolvió uno sin nombre (eliminado o ilegible); `null` = ningún aviso.
            _lastResolved.value = name ?: ""
            syncTrigger.changed()
            ResolveResult.Resolved(name)
        } catch (_: VaultLockedException) {
            ResolveResult.Locked
        }
    }

    /**
     * El servidor eliminó el elemento y la persona quiere conservar el suyo: el servidor no acepta editar una
     * lápida, así que se guarda como un elemento nuevo (id nuevo, cifrado con su propia AAD) y la fila vieja se quita.
     * Primero se crea el nuevo: si algo falla, no se pierde el elemento.
     */
    private suspend fun keepMineAsNew(detail: ConflictDetail, item: VaultItem): Boolean {
        // Si ya cambió algo, no se crea nada: un reintento no dejaría duplicados.
        val current = dao.get(detail.id.toString())
        if (current == null || current.syncState != SyncState.Conflict || current.localSeq != detail.localSeq) return false
        val created = repository.create(
            ItemDraft(item.name, item.username, item.password, item.url, item.notes),
            createdAt = item.createdAt,
            passwordChangedAt = item.passwordChangedAt,
        )
        if (created !is SaveResult.Saved) return false
        if (dao.resolveWithRemote(detail.toRemoteRow(), detail.localSeq)) return true
        // Cambió algo entre la comprobación y aquí: se quita el nuevo (nunca se subió) para que un reintento no duplique.
        repository.delete(created.id, expectedLocalSeq = 1)
        return false
    }

    // --- Ayudantes ---

    private fun toRow(row: VaultItemEntity, userId: UUID): ConflictRow? {
        val id = runCatching { UUID.fromString(row.id) }.getOrNull() ?: return null
        val name = try {
            val nonce = row.nonce
            val ciphertext = row.ciphertext
            if (nonce == null || ciphertext == null || row.formatVersion > ItemCipher.FORMAT_VERSION) {
                null
            } else {
                session.withVaultKey { key ->
                    ItemCipher.decryptSummary(key, id, userId, row.formatVersion, Sealed(nonce, ciphertext)).name
                }
            }
        } catch (_: AuthenticationFailed) {
            null
        } catch (_: ItemFormatException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
        return ConflictRow(id, name, Instant.ofEpochMilli(row.updatedAt))
    }

    private fun localSide(row: VaultItemEntity, userId: UUID): VersionSide {
        val id = UUID.fromString(row.id)
        val at = Instant.ofEpochMilli(row.updatedAt)
        val nonce = row.nonce
        val ciphertext = row.ciphertext
        if (nonce == null || ciphertext == null) return VersionSide.Deleted(Instant.ofEpochMilli(row.deletedAt ?: row.updatedAt))
        return decryptSide(id, userId, row.formatVersion, Sealed(nonce, ciphertext), at)
    }

    private fun remoteSide(remote: RemoteVaultItem, userId: UUID): VersionSide {
        val sealed = remote.sealed
        if (remote.deleted || sealed == null) return VersionSide.Deleted(remote.updatedAt)
        return decryptSide(remote.id, userId, remote.formatVersion, sealed, remote.updatedAt)
    }

    private fun decryptSide(id: UUID, userId: UUID, formatVersion: Int, sealed: Sealed, at: Instant): VersionSide {
        if (formatVersion > ItemCipher.FORMAT_VERSION) return VersionSide.Unreadable(at)
        return try {
            VersionSide.Live(session.withVaultKey { key -> ItemCipher.decrypt(key, id, userId, formatVersion, sealed) }, at)
        } catch (_: AuthenticationFailed) {
            VersionSide.Unreadable(at)
        } catch (_: ItemFormatException) {
            VersionSide.Unreadable(at)
        } catch (_: IllegalArgumentException) {
            VersionSide.Unreadable(at)
        }
    }

    private fun ConflictDetail.toRemoteRow() = RemoteRow(
        id = id.toString(),
        version = remoteVersion,
        revision = remoteRevision,
        formatVersion = remoteFormatVersion,
        nonce = remoteSealed?.nonce,
        ciphertext = remoteSealed?.ciphertext,
        deleted = remote is VersionSide.Deleted,
        updatedAt = remoteUpdatedAt.toEpochMilli(),
    )
}
