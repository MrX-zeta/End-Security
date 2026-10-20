package com.luis.end_security.feature.vault.domain

import com.luis.end_security.core.common.ApiResult
import com.luis.end_security.core.crypto.Sealed
import java.time.Instant
import java.util.UUID

/** Un elemento como lo guarda el servidor (`VaultItem` del contrato): solo el bloque cifrado. */
class RemoteVaultItem(
    val id: UUID,
    val version: Long,
    val revision: Long,
    val formatVersion: Int,
    val deleted: Boolean,
    /** `null` en las lápidas. */
    val sealed: Sealed?,
    val updatedAt: Instant,
) {
    override fun toString(): String = "RemoteVaultItem(id=$id, version=$version, revision=$revision, deleted=$deleted)"
}

/** Una página de `GET /vault/items` (EP-10). */
class VaultChanges(
    val items: List<RemoteVaultItem>,
    val nextSince: Long,
    val hasMore: Boolean,
    val currentRevision: Long,
)

/** `WriteResult` del contrato. */
class WriteResult(
    val result: Kind,
    val version: Long,
    val revision: Long,
) {
    enum class Kind { Created, Updated, Deleted }
}

/** Sincronización de bloques cifrados (contrato `docs/api`, EP-10…EP-12). Requieren sesión. */
interface VaultApi {
    /** EP-10: cambios desde [since] (0 = todo), ordenados por `revision`. `cursor_expired` → descargar todo. */
    suspend fun listChanges(since: Long, limit: Int = DEFAULT_PAGE_SIZE): ApiResult<VaultChanges>

    /** EP-11: crear ([expectedVersion] `null`) o editar con concurrencia optimista. */
    suspend fun putItem(id: UUID, expectedVersion: Long?, formatVersion: Int, sealed: Sealed): ApiResult<WriteResult>

    /** EP-12: lápida. */
    suspend fun deleteItem(id: UUID, expectedVersion: Long): ApiResult<WriteResult>

    companion object {
        const val DEFAULT_PAGE_SIZE = 200
    }
}
