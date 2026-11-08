package com.luis.end_security.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Estado de sincronización de un elemento (DEC-47). Se guarda como texto. */
enum class SyncState(val dbValue: String) {
    Synced("synced"),
    Pending("pending"),
    Conflict("conflict"),
    ;

    companion object {
        fun fromDb(value: String): SyncState = entries.first { it.dbValue == value }
    }
}

/**
 * `vault_items` local (TBL-L-ITEMS, modelo de datos §8.1-8.2): espejo de los bloques cifrados del
 * servidor más el estado de sincronización. **Solo `nonce` y `ciphertext`**: nombre, usuario,
 * contraseña, URL y notas van dentro del bloque cifrado (DEC-29, INV-02).
 *
 * `nonce` y `ciphertext` en `null` = lápida local pendiente de subir.
 */
@Entity(tableName = "vault_items", indices = [Index("sync_state")])
class VaultItemEntity(
    /** UUID canónico generado en el teléfono (DEC-42). */
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "format_version") val formatVersion: Int,
    @ColumnInfo(name = "nonce") val nonce: ByteArray?,
    @ColumnInfo(name = "ciphertext") val ciphertext: ByteArray?,
    /** Última versión confirmada por el servidor: la «versión esperada» al subir. `null` = nunca se subió. */
    @ColumnInfo(name = "server_version") val serverVersion: Long?,
    @ColumnInfo(name = "server_revision") val serverRevision: Long?,
    /** Sube con cada edición local; evita marcar como sincronizado algo editado mientras se subía. */
    @ColumnInfo(name = "local_seq") val localSeq: Long,
    @ColumnInfo(name = "sync_state") val syncState: SyncState,
    /** epoch ms; `null` si no está borrado. */
    @ColumnInfo(name = "deleted_at") val deletedAt: Long?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
) {
    val isTombstone: Boolean get() = ciphertext == null

    override fun toString(): String =
        "VaultItemEntity(id=$id, localSeq=$localSeq, syncState=$syncState, tombstone=$isTombstone)"
}
