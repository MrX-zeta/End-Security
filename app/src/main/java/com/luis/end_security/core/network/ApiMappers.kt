package com.luis.end_security.core.network

import com.luis.end_security.core.crypto.Sealed
import com.luis.end_security.core.network.dto.AccountDto
import com.luis.end_security.core.network.dto.KdfDto
import com.luis.end_security.core.network.dto.ProtectedVaultKeyDto
import com.luis.end_security.core.network.dto.TokensDto
import com.luis.end_security.core.network.dto.VaultItemDto
import com.luis.end_security.feature.account.domain.AccountInfo
import com.luis.end_security.feature.account.domain.ServerKdf
import com.luis.end_security.feature.account.domain.SessionTokens
import com.luis.end_security.feature.vault.domain.RemoteVaultItem
import java.time.Instant
import java.util.UUID

/** Conversión entre los DTO del contrato y el dominio. Un valor mal formado lanza `IllegalArgumentException`. */
object ApiMappers {
    const val KDF_ALGORITHM = "argon2id"

    /** UUID en forma canónica (DEC-29): minúsculas y guiones; cualquier otra forma se rechaza. */
    fun uuid(text: String): UUID {
        val parsed = UUID.fromString(text)
        require(parsed.toString() == text) { "UUID no canónico" }
        return parsed
    }

    fun instant(text: String): Instant = try {
        Instant.parse(text)
    } catch (error: java.time.format.DateTimeParseException) {
        throw IllegalArgumentException("Fecha inválida", error)
    }

    fun KdfDto.toDomain(): ServerKdf {
        require(algorithm == KDF_ALGORITHM) { "Algoritmo KDF desconocido" }
        return ServerKdf(memoryKiB, iterations, parallelism, Base64Url.decode(salt))
    }

    fun ServerKdf.toDto() = KdfDto(KDF_ALGORITHM, memoryKiB, iterations, parallelism, Base64Url.encode(salt))

    fun ProtectedVaultKeyDto.toDomain() = Sealed(Base64Url.decode(nonce), Base64Url.decode(ciphertext))

    fun Sealed.toPvkDto() = ProtectedVaultKeyDto(Base64Url.encode(nonce), Base64Url.encode(ciphertext))

    fun AccountDto.toDomain() = AccountInfo(
        userId = uuid(userId),
        email = email,
        emailVerified = emailVerified,
        displayName = displayName,
        keyVersion = keyVersion,
        kdf = kdf.toDomain(),
        protectedVaultKey = protectedVaultKey.toDomain(),
        twoFactorEnabled = twoFactorEnabled,
    )

    fun TokensDto.toDomain() = SessionTokens(
        accessToken = accessToken,
        accessTokenExpiresInSeconds = accessTokenExpiresIn,
        refreshToken = refreshToken,
        refreshTokenExpiresAt = instant(refreshTokenExpiresAt),
        sessionId = uuid(sessionId),
    )

    fun VaultItemDto.toDomain(): RemoteVaultItem {
        val sealed = if (deleted || nonce == null || ciphertext == null) {
            null
        } else {
            Sealed(Base64Url.decode(nonce), Base64Url.decode(ciphertext))
        }
        return RemoteVaultItem(uuid(id), version, revision, formatVersion, deleted, sealed, instant(updatedAt))
    }
}
