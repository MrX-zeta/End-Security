package com.luis.end_security.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * `breach_checks` (TBL-L-BREACH): caché **cifrada** del resultado de HIBP por elemento
 * (`{ "pwned_count": n }`). Si `checked_local_seq` no coincide con el `local_seq` del elemento,
 * la contraseña cambió y hay que volver a consultar. Se borra con el elemento.
 */
@Entity(
    tableName = "breach_checks",
    foreignKeys = [
        ForeignKey(
            entity = VaultItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["item_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
class BreachCheckEntity(
    @PrimaryKey @ColumnInfo(name = "item_id") val itemId: String,
    @ColumnInfo(name = "checked_local_seq") val checkedLocalSeq: Long,
    @ColumnInfo(name = "nonce") val nonce: ByteArray,
    @ColumnInfo(name = "ciphertext") val ciphertext: ByteArray,
    @ColumnInfo(name = "checked_at") val checkedAt: Long,
) {
    override fun toString(): String = "BreachCheckEntity(itemId=$itemId, checkedLocalSeq=$checkedLocalSeq)"
}

/**
 * `health_snapshots` (TBL-L-HEALTH, DEC-55): una foto diaria de los **totales** de la salud de la
 * bóveda, cifrada con la `vault_key`. Nunca contraseñas ni su fuerza individual (INV-29). No
 * existe en el servidor.
 */
@Entity(tableName = "health_snapshots")
class HealthSnapshotEntity(
    /** Fecha local `AAAA-MM-DD`; una fila por día como máximo. */
    @PrimaryKey @ColumnInfo(name = "day") val day: String,
    @ColumnInfo(name = "format_version") val formatVersion: Int,
    @ColumnInfo(name = "nonce") val nonce: ByteArray,
    @ColumnInfo(name = "ciphertext") val ciphertext: ByteArray,
    @ColumnInfo(name = "created_at") val createdAt: Long,
) {
    override fun toString(): String = "HealthSnapshotEntity(day=$day)"
}
