package com.luis.end_security.core.crypto

import androidx.annotation.VisibleForTesting
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters

/**
 * `master_key = Argon2id(contraseña, sal_kdf, parámetros)` (DEC-05), con Bouncy Castle
 * (DEC-28, PEND-01): tipo Argon2id, versión 0x13 (1.3) y 32 bytes de salida.
 *
 * Bouncy Castle limpia sus bloques de memoria al terminar. Se usa un generador nuevo por
 * llamada para que no retenga esos bloques entre derivaciones. Quedan sin borrar, dentro de la
 * librería y hasta que pase el recolector, su H0 (72 B) y el último bloque en bytes (1 KiB):
 * limitación de DEC-27.
 */
object Argon2idKdf {
    const val KEY_LENGTH = 32

    /**
     * DEC-06: 16 bytes aleatorios por cuenta (también lo exige `users.kdf_salt` en el servidor).
     * Exacto, no «al menos»: la sal falsa del prelogin (DEC-07) no puede delatarse por su largo.
     */
    const val SALT_LENGTH = 16

    /**
     * Deriva la `master_key`. No toca [password] ni [salt]: quien llama los sobrescribe. Quien
     * recibe el resultado lo sobrescribe con [wipe] cuando ya no lo usa.
     */
    fun derive(password: ByteArray, salt: ByteArray, params: KdfParams): ByteArray {
        require(salt.size == SALT_LENGTH) { "La sal debe tener $SALT_LENGTH bytes" }
        return argon2id(
            password = password,
            salt = salt,
            memoryKiB = params.memoryKiB,
            iterations = params.iterations,
            parallelism = params.parallelism,
            outputLength = KEY_LENGTH,
        )
    }

    /**
     * Argon2id sin las validaciones de [KdfParams]: solo para [derive] y para el vector de
     * RFC 9106 (que usa 32 KiB, un secreto y datos asociados).
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun argon2id(
        password: ByteArray,
        salt: ByteArray,
        memoryKiB: Int,
        iterations: Int,
        parallelism: Int,
        outputLength: Int,
        secret: ByteArray? = null,
        associatedData: ByteArray? = null,
    ): ByteArray {
        val builder = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withMemoryAsKB(memoryKiB)
            .withIterations(iterations)
            .withParallelism(parallelism)
            .withSalt(salt)
        if (secret != null) builder.withSecret(secret)
        if (associatedData != null) builder.withAdditional(associatedData)
        val parameters = builder.build()
        val output = ByteArray(outputLength)
        try {
            Argon2BytesGenerator().apply { init(parameters) }.generateBytes(password, output)
            return output
        } catch (error: Throwable) {
            output.wipe()
            throw error
        } finally {
            // Las copias de la sal, el secreto y los datos asociados que guardó Bouncy Castle.
            parameters.clear()
            builder.clear()
        }
    }
}
