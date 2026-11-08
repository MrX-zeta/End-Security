package com.luis.end_security.feature.vault.domain

import java.util.UUID

/**
 * Un elemento de la bóveda tal como lo ve la lista (OP-04): ya descifrado, o la razón por la que
 * no se pudo. Solo existe en memoria con la bóveda abierta; ninguno imprime su contenido.
 *
 * [localSeq] es la versión local que hay que devolver al editar o borrar (regla no negociable 8).
 */
sealed interface VaultEntry {
    val id: UUID
    val localSeq: Long
}

/** Se descifró y es un elemento v1. */
class ReadableEntry(
    override val id: UUID,
    override val localSeq: Long,
    val item: VaultItem,
) : VaultEntry {
    override fun toString(): String = "ReadableEntry(***)"
}

/** La etiqueta GCM no coincide (bloque alterado) o el JSON no es válido: «Elemento dañado». */
class DamagedEntry(
    override val id: UUID,
    override val localSeq: Long,
) : VaultEntry {
    override fun toString(): String = "DamagedEntry(***)"
}

/**
 * Lo escribió una versión más nueva de la app (`format_version` mayor, DEC-04): se muestra en solo
 * lectura, «Actualiza la app para editarla». Nunca se reescribe con el formato viejo.
 */
class UnsupportedEntry(
    override val id: UUID,
    override val localSeq: Long,
    val formatVersion: Int,
) : VaultEntry {
    override fun toString(): String = "UnsupportedEntry(***)"
}

/**
 * Lo que necesita la lista (SCR-10): nombre, usuario y sitio, **sin la contraseña ni las notas**. Se arma
 * descifrando el bloque y tomando solo esos tres campos; el resto del JSON se borra al terminar.
 */
sealed interface VaultSummary {
    val id: UUID
    val localSeq: Long

    class Readable(
        override val id: UUID,
        override val localSeq: Long,
        val name: String,
        val username: String,
        val url: String?,
        /** Hay una versión distinta en otro dispositivo (SCR-18…21). */
        val inConflict: Boolean,
    ) : VaultSummary {
        override fun toString(): String = "VaultSummary.Readable(***)"
    }

    class Damaged(override val id: UUID, override val localSeq: Long) : VaultSummary {
        override fun toString(): String = "VaultSummary.Damaged(***)"
    }

    class Unsupported(override val id: UUID, override val localSeq: Long) : VaultSummary {
        override fun toString(): String = "VaultSummary.Unsupported(***)"
    }
}

/** Los campos que escribe la persona al crear o editar (SCR-14). La contraseña es una `String` (DEC-27). */
class ItemDraft(
    val name: String,
    val username: String,
    val password: String,
    val url: String?,
    val notes: String?,
) {
    override fun toString(): String = "ItemDraft(***)"
}

/** Resultado de guardar un elemento (OP-01, OP-02). */
sealed interface SaveResult {
    data class Saved(val id: UUID) : SaveResult

    /** Otra escritura se adelantó (el `local_seq` esperado ya no coincide): hay que recargar. */
    data object Conflict : SaveResult

    /** El elemento ya no existe o no se puede leer. */
    data object NotFound : SaveResult

    /** La bóveda se bloqueó mientras se guardaba. */
    data object Locked : SaveResult
}

/** Un borrado que espera su «Deshacer» (SCR-17). [name] es `null` para un elemento dañado o de otra versión. */
class PendingDeletion(
    val id: UUID,
    val name: String?,
    val localSeq: Long,
) {
    override fun equals(other: Any?): Boolean = other is PendingDeletion && id == other.id && localSeq == other.localSeq

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "PendingDeletion(***)"
}
