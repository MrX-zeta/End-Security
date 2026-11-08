package com.luis.end_security.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * `account` (TBL-L-ACCOUNT, modelo de datos §8.1): una sola fila, la cuenta con sesión en este
 * teléfono. Guarda lo necesario para desbloquear sin red (sal y parámetros de Argon2 y la `pvk`
 * cifrada); nada de esto es secreto en claro.
 *
 * No es `data class` (tiene arreglos) y su `toString()` no muestra nada de la cuenta.
 */
@Entity(tableName = "account")
class AccountEntity(
    /** UUID canónico (minúsculas y guiones). */
    @PrimaryKey @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "email") val email: String,
    @ColumnInfo(name = "display_name") val displayName: String?,
    @ColumnInfo(name = "kdf_salt") val kdfSalt: ByteArray,
    @ColumnInfo(name = "kdf_memory_kib") val kdfMemoryKiB: Int,
    @ColumnInfo(name = "kdf_iterations") val kdfIterations: Int,
    @ColumnInfo(name = "kdf_parallelism") val kdfParallelism: Int,
    @ColumnInfo(name = "pvk_nonce") val pvkNonce: ByteArray,
    @ColumnInfo(name = "pvk_ciphertext") val pvkCiphertext: ByteArray,
    @ColumnInfo(name = "key_version") val keyVersion: Int,
    /** Última `revision` del servidor aplicada. */
    @ColumnInfo(name = "sync_cursor") val syncCursor: Long,
    /** epoch ms; `null` si nunca se sincronizó. */
    @ColumnInfo(name = "last_sync_at") val lastSyncAt: Long?,
    /** `vault_key` envuelta con la llave biométrica del Keystore (DEC-09); `null` sin huella. */
    @ColumnInfo(name = "bio_iv") val bioIv: ByteArray?,
    @ColumnInfo(name = "bio_wrapped_vault_key") val bioWrappedVaultKey: ByteArray?,
) {
    override fun toString(): String = "AccountEntity(***)"
}
