package com.luis.end_security.feature.vault.data

import com.luis.end_security.core.common.ApiError
import com.luis.end_security.core.common.ApiResult
import com.luis.end_security.core.sync.SyncEngine
import com.luis.end_security.core.sync.SyncOutcome
import com.luis.end_security.feature.vault.domain.ConflictRepository
import com.luis.end_security.feature.vault.domain.DeletionUndo
import com.luis.end_security.feature.vault.domain.GeneratorRepository
import com.luis.end_security.feature.vault.domain.PasswordHandoff
import com.luis.end_security.feature.vault.domain.VaultRepository
import com.luis.end_security.feature.vault.domain.VaultSyncRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

/**
 * [VaultSyncRepository] sobre el motor de sincronización: la descarga inicial (OP-32) es la primera ejecución
 * de [SyncEngine] (sube lo pendiente, si hay, y baja desde el cursor 0). Solo mueve bloques cifrados.
 */
class VaultSyncRepositoryImpl @Inject constructor(
    private val engine: SyncEngine,
) : VaultSyncRepository {

    override suspend fun initialDownload(): ApiResult<Unit> = when (engine.sync()) {
        SyncOutcome.Synced, SyncOutcome.NotSignedIn -> ApiResult.Success(Unit)
        SyncOutcome.Offline -> ApiResult.Failure(ApiError.NetworkUnavailable)
        SyncOutcome.RetryLater -> ApiResult.Failure(ApiError.ServerError)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class VaultDataModule {

    @Binds
    abstract fun bindVaultSyncRepository(impl: VaultSyncRepositoryImpl): VaultSyncRepository

    @Binds
    abstract fun bindVaultRepository(impl: VaultRepositoryImpl): VaultRepository

    @Binds
    abstract fun bindConflictRepository(impl: ConflictResolver): ConflictRepository

    @Binds
    abstract fun bindDeletionUndo(impl: DeletionUndoController): DeletionUndo

    @Binds
    abstract fun bindGeneratorRepository(impl: GeneratorRepositoryImpl): GeneratorRepository

    @Binds
    abstract fun bindPasswordHandoff(impl: PasswordHandoffImpl): PasswordHandoff
}
