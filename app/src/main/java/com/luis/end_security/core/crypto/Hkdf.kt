package com.luis.end_security.core.crypto

import androidx.annotation.VisibleForTesting
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters

/**
 * HKDF-SHA256 (RFC 5869) con Bouncy Castle, para separar la `master_key` en dos llaves
 * independientes (DEC-05):
 * - `enc_key = HKDF-SHA256(master_key, info = "end-security/enc")`: envuelve la `vault_key`;
 * - `auth_key = HKDF-SHA256(master_key, info = "end-security/auth")`: es lo único que viaja al
 *   servidor.
 *
 * Sin sal (RFC 5869 §2.2: equivale a 32 ceros): la `master_key` ya sale de Argon2id con la sal
 * de la cuenta.
 *
 * Copias que no se pueden borrar (DEC-27): `HKDFParameters` clona el IKM y `init` lo vuelve a
 * copiar; el PRK queda en un `KeyParameter` y en los estados de `HMac`; con 32 bytes de salida,
 * el bloque interno `T(1)` es la llave misma. Los generadores son locales para que todo eso
 * quede sin referencias al terminar.
 */
object Hkdf {
    const val KEY_LENGTH = 32

    private val ENC_INFO = "end-security/enc".toByteArray(Charsets.US_ASCII)
    private val AUTH_INFO = "end-security/auth".toByteArray(Charsets.US_ASCII)

    fun encKey(masterKey: ByteArray): ByteArray =
        sha256(requireMasterKey(masterKey), salt = null, info = ENC_INFO, length = KEY_LENGTH)

    fun authKey(masterKey: ByteArray): ByteArray =
        sha256(requireMasterKey(masterKey), salt = null, info = AUTH_INFO, length = KEY_LENGTH)

    /**
     * Una `master_key` de 32 bytes que no esté en ceros: si alguien la sobrescribe antes de
     * tiempo, HKDF daría una llave que cualquiera puede calcular, sin ningún error.
     */
    private fun requireMasterKey(masterKey: ByteArray): ByteArray {
        require(masterKey.size == Argon2idKdf.KEY_LENGTH) { "La master_key debe tener ${Argon2idKdf.KEY_LENGTH} bytes" }
        require(masterKey.any { it != 0.toByte() }) { "La master_key está en ceros" }
        return masterKey
    }

    /** HKDF-SHA256 completo (extraer y expandir). `internal` para los vectores de RFC 5869. */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun sha256(ikm: ByteArray, salt: ByteArray?, info: ByteArray, length: Int): ByteArray {
        val generator = HKDFBytesGenerator(SHA256Digest())
        generator.init(HKDFParameters(ikm, salt, info))
        val output = ByteArray(length)
        try {
            generator.generateBytes(output, 0, length)
            return output
        } catch (error: Throwable) {
            output.wipe()
            throw error
        }
    }
}
