package com.luis.end_security.core.crypto

/**
 * Acceso de solo lectura a un secreto: se puede leer con [withBytes], pero no borrar ni cerrar.
 * `VaultSession` presta la `vault_key` así, para que quien la usa no la borre por error.
 */
interface SecretReader {
    val size: Int

    /** Presta los bytes a un bloque corto; no se debe guardar la referencia. */
    fun <R> withBytes(block: (ByteArray) -> R): R
}
