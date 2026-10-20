package com.luis.end_security.feature.vault.data

import com.luis.end_security.core.common.ApiError
import com.luis.end_security.core.common.ApiResult
import com.luis.end_security.core.common.DeviceClock
import com.luis.end_security.core.database.dao.AccountDao
import com.luis.end_security.core.database.dao.VaultItemDao
import com.luis.end_security.core.database.entity.SyncState
import com.luis.end_security.core.database.entity.VaultItemEntity
import com.luis.end_security.feature.vault.domain.VaultApi
import com.luis.end_security.feature.vault.domain.VaultSyncRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

/** [VaultSyncRepository] con [VaultApi] y Room. Solo mueve bloques cifrados; no descifra nada. */
class VaultSyncRepositoryImpl @Inject constructor(
    private val api: VaultApi,
    private val items: VaultItemDao,
    private val accounts: AccountDao,
    private val clock: DeviceClock,
) : VaultSyncRepository {

    override suspend fun initialDownload(): ApiResult<Unit> {
        var since = 0L
        do {
            val page = when (val result = api.listChanges(since)) {
                is ApiResult.Success -> result.value
                is ApiResult.Failure -> return result
            }
            val (tombstones, live) = page.items.partition { it.deleted || it.sealed == null }
            items.applyRemote(
                items = live.map { remote ->
                    val sealed = requireNotNull(remote.sealed)
                    VaultItemEntity(
                        id = remote.id.toString(),
                        formatVersion = remote.formatVersion,
                        nonce = sealed.nonce,
                        ciphertext = sealed.ciphertext,
                        serverVersion = remote.version,
                        serverRevision = remote.revision,
                        localSeq = 0,
                        syncState = SyncState.Synced,
                        deletedAt = null,
                        updatedAt = remote.updatedAt.toEpochMilli(),
                    )
                },
                tombstoneIds = tombstones.map { it.id.toString() },
            )
            // Un servidor que no avanza el cursor no debe dejar la descarga dando vueltas.
            if (page.hasMore && page.nextSince <= since) return ApiResult.Failure(ApiError.ServerError)
            since = page.nextSince
        } while (page.hasMore)
        accounts.updateSyncCursor(since, clock.wallMillis())
        return ApiResult.Success(Unit)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class VaultDataModule {

    @Binds
    abstract fun bindVaultSyncRepository(impl: VaultSyncRepositoryImpl): VaultSyncRepository
}
