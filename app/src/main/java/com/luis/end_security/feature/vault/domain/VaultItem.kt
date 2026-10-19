package com.luis.end_security.feature.vault.domain

import java.time.Instant

/**
 * Un elemento de la bóveda ya descifrado (DEC-29, `json_elemento_v1`). Solo existe en memoria
 * con la bóveda abierta; en disco y en el servidor va cifrado. Las `String` no se pueden borrar
 * de memoria (limitación aceptada en DEC-27).
 *
 * No es `data class`: su `toString()` no muestra nada.
 */
class VaultItem(
    val name: String,
    val username: String,
    val password: String,
    val url: String?,
    val notes: String?,
    val createdAt: Instant,
    /** Para «Antiguas» en Salud (DEC-54). */
    val passwordChangedAt: Instant,
) {
    override fun toString(): String = "VaultItem(***)"

    override fun equals(other: Any?): Boolean =
        other is VaultItem && name == other.name && username == other.username && password == other.password &&
            url == other.url && notes == other.notes && createdAt == other.createdAt &&
            passwordChangedAt == other.passwordChangedAt

    override fun hashCode(): Int = name.hashCode()
}
