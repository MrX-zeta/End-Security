package com.luis.end_security.feature.health.data

import com.luis.end_security.core.crypto.AesGcm
import com.luis.end_security.core.crypto.AuthenticationFailed
import com.luis.end_security.core.crypto.Sealed
import com.luis.end_security.core.crypto.wipe
import com.luis.end_security.core.database.dao.BreachCheckDao
import com.luis.end_security.core.database.dao.HealthSnapshotDao
import com.luis.end_security.core.database.entity.BreachCheckEntity
import com.luis.end_security.core.database.entity.HealthSnapshotEntity
import com.luis.end_security.core.session.VaultSession
import com.luis.end_security.feature.health.domain.HealthTotals
import com.luis.end_security.feature.health.domain.SnapshotPoint
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Lo que dice la caché de filtraciones de un elemento. */
class CachedBreach(val count: Int, val checkedLocalSeq: Long, val checkedAtMillis: Long) {
    override fun toString(): String = "CachedBreach(***)"
}

/**
 * Caché de HIBP por elemento (TBL-L-BREACH, DEC-21): `{ "pwned_count": n }` **cifrado con la `vault_key`** y su propia
 * AAD `breach|itemId|userId|v`, para que no se confunda con un elemento (`.claude/rules/crypto.md`).
 */
@Singleton
class BreachCache @Inject constructor(
    private val dao: BreachCheckDao,
    private val session: VaultSession,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun readAll(userId: UUID): Map<String, CachedBreach> {
        val result = HashMap<String, CachedBreach>()
        for (row in dao.getAll()) {
            val count = open(row, userId) ?: continue
            result[row.itemId] = CachedBreach(count, row.checkedLocalSeq, row.checkedAt)
        }
        return result
    }

    suspend fun write(itemId: UUID, userId: UUID, localSeq: Long, count: Int, nowMillis: Long) {
        val plaintext = json.encodeToString(BreachJson.serializer(), BreachJson(count, localSeq, nowMillis)).toByteArray(Charsets.UTF_8)
        val sealed = try {
            session.withVaultKey { key -> key.withBytes { AesGcm.seal(it, plaintext, aad(itemId, userId)) } }
        } finally {
            plaintext.wipe()
        }
        dao.upsert(BreachCheckEntity(itemId.toString(), localSeq, sealed.nonce, sealed.ciphertext, nowMillis))
    }

    /** `null` si no abre (alterada) o no se entiende: se vuelve a consultar. */
    private fun open(row: BreachCheckEntity, userId: UUID): Int? {
        val id = runCatching { UUID.fromString(row.itemId) }.getOrNull() ?: return null
        val plaintext = try {
            session.withVaultKey { key -> key.withBytes { AesGcm.open(it, Sealed(row.nonce, row.ciphertext), aad(id, userId)) } }
        } catch (_: AuthenticationFailed) {
            return null
        }
        return try {
            val decoded = json.decodeFromString(BreachJson.serializer(), plaintext.toString(Charsets.UTF_8))
            // `checked_local_seq` y `checked_at` de la fila están en claro: deben coincidir con lo autenticado, y la hora no
            // puede ser del futuro (un bloque viejo no se «rejuvenece» cambiando las columnas).
            if (decoded.localSeq != row.checkedLocalSeq || decoded.checkedAt != row.checkedAt) null else decoded.pwnedCount
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        } finally {
            plaintext.wipe()
        }
    }

    private fun aad(itemId: UUID, userId: UUID): ByteArray = "breach|$itemId|$userId|$VERSION".toByteArray(Charsets.US_ASCII)

    private companion object {
        const val VERSION = 1
    }
}

@Serializable
private class BreachJson(
    @SerialName("pwned_count") val pwnedCount: Int,
    @SerialName("local_seq") val localSeq: Long,
    @SerialName("checked_at") val checkedAt: Long,
)

/**
 * La foto diaria de los **totales** (TBL-L-HEALTH, DEC-55, INV-29): una fila por día local, cifrada con la `vault_key`
 * con AAD `health|día|userId|v`. Nunca contraseñas ni la fuerza de cada una.
 */
@Singleton
class SnapshotStore @Inject constructor(
    private val dao: HealthSnapshotDao,
    private val session: VaultSession,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Guarda la foto de [day] (reemplaza la de ese día) y borra las de más de 12 meses. */
    suspend fun save(day: LocalDate, userId: UUID, totals: HealthTotals, nowMillis: Long) {
        val plaintext = json.encodeToString(SnapshotJson.serializer(), SnapshotJson.from(totals)).toByteArray(Charsets.UTF_8)
        val sealed = try {
            session.withVaultKey { key -> key.withBytes { AesGcm.seal(it, plaintext, aad(day, userId)) } }
        } finally {
            plaintext.wipe()
        }
        dao.upsert(HealthSnapshotEntity(day.toString(), VERSION, sealed.nonce, sealed.ciphertext, nowMillis))
        dao.deleteBefore(day.minusMonths(RETENTION_MONTHS).toString())
    }

    /** Las fotos que abren; una alterada o de otra versión se salta. */
    fun readAll(rows: List<HealthSnapshotEntity>, userId: UUID): List<SnapshotPoint> = rows.mapNotNull { row ->
        val day = try {
            LocalDate.parse(row.day)
        } catch (_: DateTimeParseException) {
            return@mapNotNull null
        }
        if (row.formatVersion != VERSION) return@mapNotNull null
        val plaintext = try {
            session.withVaultKey { key -> key.withBytes { AesGcm.open(it, Sealed(row.nonce, row.ciphertext), aad(day, userId)) } }
        } catch (_: AuthenticationFailed) {
            return@mapNotNull null
        }
        try {
            SnapshotPoint(day, json.decodeFromString(SnapshotJson.serializer(), plaintext.toString(Charsets.UTF_8)).toTotals())
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        } finally {
            plaintext.wipe()
        }
    }

    private fun aad(day: LocalDate, userId: UUID): ByteArray = "health|$day|$userId|$VERSION".toByteArray(Charsets.US_ASCII)

    companion object {
        const val VERSION = 1
        const val RETENTION_MONTHS = 12L
    }
}

@Serializable
private class SnapshotJson(
    @SerialName("score") val score: Int,
    @SerialName("total") val total: Int,
    @SerialName("weak") val weak: Int,
    @SerialName("fair") val fair: Int,
    @SerialName("strong") val strong: Int,
    @SerialName("repeated") val repeated: Int,
    @SerialName("breached") val breached: Int,
    @SerialName("old") val old: Int,
) {
    fun toTotals() = HealthTotals(score, total, weak, fair, strong, repeated, breached, old)

    companion object {
        fun from(t: HealthTotals) = SnapshotJson(t.score, t.total, t.weak, t.fair, t.strong, t.repeated, t.breached, t.old)
    }
}
