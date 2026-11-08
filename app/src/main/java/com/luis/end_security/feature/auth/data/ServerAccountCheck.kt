package com.luis.end_security.feature.auth.data

import com.luis.end_security.core.crypto.AuthenticationFailed
import com.luis.end_security.core.crypto.KdfParams
import com.luis.end_security.core.crypto.SecretReader
import com.luis.end_security.core.crypto.Sealed
import com.luis.end_security.core.database.dao.VaultItemDao
import com.luis.end_security.core.database.entity.AccountEntity
import com.luis.end_security.feature.account.domain.AccountInfo
import com.luis.end_security.feature.account.domain.ServerKdf
import com.luis.end_security.feature.vault.data.ItemCipher
import com.luis.end_security.feature.vault.data.ItemFormatException
import java.util.UUID
import javax.inject.Inject

/**
 * Qué se acepta del servidor al entrar cuando este teléfono ya tiene la cuenta (hallazgo 1 de la tarea 07 y recuperación de la
 * tarea 18).
 *
 * Un servidor que cambia los parámetros de Argon2 o la sal de una cuenta conocida podría hacer que la app derive y mande una
 * `auth_key` más barata de atacar. Por eso, con la cuenta de este correo en el teléfono:
 * - los parámetros **nunca** pueden costar menos que los locales ([lowersCost], antes de derivar nada);
 * - una **sal distinta** solo se acepta como recuperación de un cambio de contraseña que el servidor aplicó y el teléfono no llegó
 *   a guardar (el proceso murió entre el 200 de EP-17 y Room). Se acepta si se cumplen **las tres**:
 *   (a) mismos parámetros o mayores que los locales y que el piso del cliente (DEC-05);
 *   (b) la `key_version` del servidor es **mayor** que la local;
 *   (c) la pvk del servidor abrió con la `enc_key` derivada (el que llama ya lo comprobó) y la `vault_key` resultante abre lo que
 *   hay en el teléfono: la `vault_key` no cambió, así que los elementos y los cambios sin subir se conservan.
 *
 * Aquí nada es secreto salvo la `vault_key` que se presta para (c); no se guarda ni se registra.
 */
class ServerAccountCheck @Inject constructor(
    private val items: VaultItemDao,
) {

    /** Compara con ambos lados normalizados: una fila local con otra forma del mismo correo (NFD, mayúsculas) sigue siendo la misma. */
    private fun sameEmail(local: String, typed: String) =
        com.luis.end_security.core.common.EmailNormalizer.normalize(local) == com.luis.end_security.core.common.EmailNormalizer.normalize(typed)

    /** Antes de derivar: ¿los parámetros que manda el servidor son más baratos que los de la cuenta local de ese correo? */
    fun lowersCost(local: AccountEntity?, email: String, kdf: ServerKdf): Boolean {
        val known = local?.takeIf { sameEmail(it.email, email) } ?: return false
        return kdf.memoryKiB < known.kdfMemoryKiB || kdf.iterations < known.kdfIterations || kdf.parallelism < known.kdfParallelism
    }

    /**
     * Antes de derivar: ¿el servidor manda otra sal para la cuenta de [email] que hay en el teléfono? Entonces, antes de mandar
     * nada, se comprueba que la contraseña tecleada no sea la vigente aquí (ver `AuthRepositoryImpl.login`).
     */
    fun saltDiffers(local: AccountEntity?, email: String, kdf: ServerKdf): Boolean {
        val known = local?.takeIf { sameEmail(it.email, email) } ?: return false
        return !known.kdfSalt.contentEquals(kdf.salt)
    }

    /**
     * ¿Es [server] una recuperación en vez de un acceso normal? La cuenta local se identifica por el correo que **tecleó la
     * persona** ([typedEmail], normalizado), no por el que dice el servidor: así un servidor que cambia el `email` de la respuesta
     * no esquiva las comprobaciones.
     */
    fun isRecovery(local: AccountEntity?, typedEmail: String, server: AccountInfo): Boolean =
        local != null && sameEmail(local.email, typedEmail) && !local.kdfSalt.contentEquals(server.kdf.salt)

    /**
     * Después del acceso y de abrir la pvk con [vaultKey]: ¿se acepta la cuenta del servidor? Sin cuenta local, con otro correo o
     * con la misma sal es un acceso normal (lo de siempre). Con otra sal, solo si se cumplen (a), (b) y (c).
     */
    suspend fun accept(local: AccountEntity?, typedEmail: String, server: AccountInfo, vaultKey: SecretReader): Boolean {
        if (!isRecovery(local, typedEmail, server)) return true
        val known = checkNotNull(local)
        // Otra cuenta con el mismo correo (borrada y creada de nuevo), o un servidor que cambió el correo de la respuesta: no es
        // una recuperación de esta cuenta.
        if (known.userId != server.userId.toString() || server.email != known.email) return false
        return costIsNotLower(known, server.kdf) && server.keyVersion > known.keyVersion && opensLocalData(known, vaultKey)
    }

    /** (a) El piso del cliente se aplica con [KdfParams], que lanza por debajo de él. */
    private fun costIsNotLower(known: AccountEntity, kdf: ServerKdf): Boolean {
        try {
            KdfParams(kdf.memoryKiB, kdf.iterations, kdf.parallelism)
        } catch (_: IllegalArgumentException) {
            return false
        }
        return kdf.memoryKiB >= known.kdfMemoryKiB && kdf.iterations >= known.kdfIterations && kdf.parallelism >= known.kdfParallelism
    }

    /** (c) Sin elementos locales no hay nada que comprobar; con alguno, la etiqueta GCM debe ser válida con esta `vault_key`. */
    private suspend fun opensLocalData(known: AccountEntity, vaultKey: SecretReader): Boolean {
        val item = items.anySealed() ?: return true
        val nonce = item.nonce ?: return true
        val ciphertext = item.ciphertext ?: return true
        return try {
            ItemCipher.decryptSummary(
                vaultKey,
                UUID.fromString(item.id),
                UUID.fromString(known.userId),
                item.formatVersion,
                Sealed(nonce, ciphertext),
            )
            true
        } catch (_: ItemFormatException) {
            // La etiqueta abrió (es la misma llave); lo que dice dentro no es un elemento v1: no es asunto de este acceso.
            true
        } catch (_: AuthenticationFailed) {
            false
        } catch (_: IllegalArgumentException) {
            // Fila local ilegible (id o tamaños inválidos): no se puede comprobar, así que no se acepta.
            false
        }
    }
}
