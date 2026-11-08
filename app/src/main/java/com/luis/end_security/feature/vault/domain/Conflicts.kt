package com.luis.end_security.feature.vault.domain

import com.luis.end_security.core.crypto.Sealed
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant
import java.util.UUID

/** Una fila de «Por resolver» (SCR-19): el elemento y cuándo se editó aquí. */
class ConflictRow(
    val id: UUID,
    /** `null` si la versión de este teléfono es una lápida o no se pudo leer. */
    val name: String?,
    val localAt: Instant,
) {
    override fun toString(): String = "ConflictRow(***)"
}

/** Una de las dos versiones de «Elegir versión» (SCR-21). */
sealed interface VersionSide {
    val at: Instant

    class Live(val item: VaultItem, override val at: Instant) : VersionSide {
        override fun toString(): String = "VersionSide.Live(***)"
    }

    /** «Se eliminó» en ese lado. */
    class Deleted(override val at: Instant) : VersionSide

    /** No se pudo descifrar o es de una versión de formato más nueva. */
    class Unreadable(override val at: Instant) : VersionSide
}

/**
 * Las dos versiones de un elemento en conflicto, descifradas **solo en memoria** (DEC-57). Lleva lo que hace
 * falta para resolver sin volver a pedir nada: el `local_seq` leído y la versión del servidor.
 */
class ConflictDetail(
    val id: UUID,
    val local: VersionSide,
    val remote: VersionSide,
    internal val localSeq: Long,
    internal val remoteVersion: Long,
    internal val remoteRevision: Long,
    internal val remoteFormatVersion: Int,
    internal val remoteSealed: Sealed?,
    internal val remoteUpdatedAt: Instant,
) {
    override fun toString(): String = "ConflictDetail(***)"
}

sealed interface ConflictLoad {
    class Ready(val detail: ConflictDetail) : ConflictLoad

    /** Ya no está en conflicto (se resolvió desde otro lado o el elemento cambió). */
    data object Gone : ConflictLoad

    /** No se pudo pedir la versión del servidor por falta de red. */
    data object Offline : ConflictLoad

    data object Failed : ConflictLoad

    /** La bóveda está bloqueada: sin la `vault_key` no se pueden descifrar las versiones. */
    data object Locked : ConflictLoad
}

/** Qué versión se conserva. */
enum class KeepVersion { Local, Remote }

sealed interface ResolveResult {
    /** Resuelto: [name] es el nombre del elemento para «{nombre} quedó resuelta». */
    class Resolved(val name: String?) : ResolveResult

    /** Cambió algo mientras tanto (el `local_seq` ya no coincide): hay que volver a cargar. */
    data object Stale : ResolveResult

    /** La versión elegida no se puede conservar (no se puede leer). */
    data object Unavailable : ResolveResult

    data object Locked : ResolveResult
}

/**
 * Conflictos de sincronización (OP-46, DEC-57): dos dispositivos editaron lo mismo. Resolver exige la
 * `vault_key`, porque hay que descifrar las dos versiones y volver a cifrar la elegida. No se guardan copias
 * ni versiones anteriores (FDA-11).
 */
interface ConflictRepository {
    /** Los elementos que esperan que se elija una versión, con la bóveda abierta. */
    fun observeConflicts(): Flow<List<ConflictRow>>

    /** `GET /vault/items/{id}` (EP-26) y descifrado de las dos versiones. */
    suspend fun load(id: UUID): ConflictLoad

    suspend fun resolve(detail: ConflictDetail, keep: KeepVersion): ResolveResult

    /** El último elemento resuelto, para «{nombre} quedó resuelta» (SCR-20). Se quita con [clearResolved]. */
    val lastResolved: StateFlow<String?>

    fun clearResolved()
}
