package com.luis.end_security.core.network.api

import com.luis.end_security.core.common.ApiResult
import com.luis.end_security.core.crypto.Sealed
import com.luis.end_security.core.network.ApiClient
import com.luis.end_security.core.network.ApiMappers.toDomain
import com.luis.end_security.core.network.Base64Url
import com.luis.end_security.core.network.dto.PutItemRequestDto
import com.luis.end_security.core.network.dto.VaultChangesDto
import com.luis.end_security.core.network.dto.WriteResultDto
import com.luis.end_security.feature.vault.domain.VaultApi
import com.luis.end_security.feature.vault.domain.VaultChanges
import com.luis.end_security.feature.vault.domain.WriteResult
import java.util.UUID
import javax.inject.Inject

/** [VaultApi] sobre el contrato (EP-10…EP-12). */
class VaultApiImpl @Inject constructor(
    private val client: ApiClient,
) : VaultApi {

    override suspend fun listChanges(since: Long, limit: Int): ApiResult<VaultChanges> {
        require(since >= 0 && limit in 1..MAX_LIMIT) { "Parámetros de página inválidos" }
        return client.call<Unit, VaultChangesDto, VaultChanges>(
            "GET", "/vault/items?since=$since&limit=$limit", null, null, VaultChangesDto.serializer(), authenticated = true,
        ) { page -> VaultChanges(page.items.map { it.toDomain() }, page.nextSince, page.hasMore, page.currentRevision) }
    }

    override suspend fun putItem(id: UUID, expectedVersion: Long?, formatVersion: Int, sealed: Sealed): ApiResult<WriteResult> {
        val body = PutItemRequestDto(expectedVersion, formatVersion, Base64Url.encode(sealed.nonce), Base64Url.encode(sealed.ciphertext))
        return client.call("PUT", "/vault/items/$id", body, PutItemRequestDto.serializer(), WriteResultDto.serializer(), true) {
            it.toDomain()
        }
    }

    override suspend fun deleteItem(id: UUID, expectedVersion: Long): ApiResult<WriteResult> =
        client.call<Unit, WriteResultDto, WriteResult>(
            "DELETE", "/vault/items/$id?expectedVersion=$expectedVersion", null, null, WriteResultDto.serializer(), true,
        ) { it.toDomain() }

    private fun WriteResultDto.toDomain() = WriteResult(
        result = when (result) {
            "created" -> WriteResult.Kind.Created
            "updated" -> WriteResult.Kind.Updated
            "deleted" -> WriteResult.Kind.Deleted
            else -> throw IllegalArgumentException("result desconocido")
        },
        version = version,
        revision = revision,
    )

    private companion object {
        const val MAX_LIMIT = 500
    }
}
