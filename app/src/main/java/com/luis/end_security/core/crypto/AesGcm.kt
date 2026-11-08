package com.luis.end_security.core.crypto

import androidx.annotation.VisibleForTesting
import java.security.SecureRandom
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.IllegalBlockSizeException
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM con JCA (DEC-05, DEC-28, DEC-29): en Android lo da Conscrypt; en la JVM, SunJCE.
 * - El nonce de 12 B sale de [SecureRandom] en cada [seal]; nunca de un contador ni del llamador.
 * - La etiqueta es de 128 bits y va al final del cifrado.
 * - Si la etiqueta no coincide, [open] lanza [AuthenticationFailed] sin más detalles y no
 *   devuelve nada del texto.
 *
 * `SecretKeySpec` guarda su propia copia de la llave y no se puede borrar (DEC-27). Se crea un
 * `Cipher` nuevo por llamada.
 */
object AesGcm {
    const val KEY_LENGTH = 32
    const val NONCE_LENGTH = 12
    const val TAG_LENGTH = 16

    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private val random = SecureRandom()

    /**
     * Cifra [plaintext] con un nonce nuevo. No toca [key] ni [plaintext]. Rechaza una llave en
     * ceros: sería una llave borrada antes de tiempo y el resultado lo podría abrir cualquiera.
     */
    fun seal(key: ByteArray, plaintext: ByteArray, aad: ByteArray): Sealed {
        requireNotZero(key)
        val nonce = ByteArray(NONCE_LENGTH).also(random::nextBytes)
        return sealWithNonce(key, nonce, plaintext, aad)
    }

    /** Solo para los vectores de prueba: en la app el nonce lo elige siempre [seal]. */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun sealWithNonce(key: ByteArray, nonce: ByteArray, plaintext: ByteArray, aad: ByteArray): Sealed {
        val cipher = cipher(Cipher.ENCRYPT_MODE, key, nonce, aad)
        return Sealed(nonce.copyOf(), cipher.doFinal(plaintext))
    }

    /**
     * Descifra y comprueba la etiqueta. Quien recibe el resultado lo sobrescribe con [wipe].
     *
     * @throws AuthenticationFailed si la llave, el nonce, el cifrado, la etiqueta o la AAD no
     *   corresponden, o si el cifrado viene truncado: dato alterado o contraseña incorrecta.
     */
    fun open(key: ByteArray, sealed: Sealed, aad: ByteArray): ByteArray {
        requireNotZero(key)
        // Truncado = alterado: el mismo error, sin detalles.
        if (sealed.ciphertext.size < TAG_LENGTH) throw AuthenticationFailed()
        val cipher = cipher(Cipher.DECRYPT_MODE, key, sealed.nonce, aad)
        try {
            return cipher.doFinal(sealed.ciphertext)
        } catch (_: BadPaddingException) {
            // Incluye AEADBadTagException, que es lo que lanzan Conscrypt y SunJCE.
            throw AuthenticationFailed()
        } catch (_: IllegalBlockSizeException) {
            throw AuthenticationFailed()
        }
    }

    private fun requireNotZero(key: ByteArray) {
        require(key.any { it != 0.toByte() }) { "La llave está en ceros" }
    }

    private fun cipher(mode: Int, key: ByteArray, nonce: ByteArray, aad: ByteArray): Cipher {
        require(key.size == KEY_LENGTH) { "La llave debe tener $KEY_LENGTH bytes" }
        require(nonce.size == NONCE_LENGTH) { "El nonce debe tener $NONCE_LENGTH bytes" }
        return Cipher.getInstance(TRANSFORMATION).apply {
            init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_LENGTH * 8, nonce))
            updateAAD(aad)
        }
    }
}

/**
 * Resultado de [AesGcm.seal]: nonce de 12 B y cifrado seguido de la etiqueta de 16 B. Guarda
 * copias propias. No es `data class` y su `toString()` solo da tamaños (INV-06: los bloques
 * cifrados tampoco van a los logs).
 */
class Sealed(nonce: ByteArray, ciphertext: ByteArray) {
    val nonce: ByteArray = nonce.copyOf()
    val ciphertext: ByteArray = ciphertext.copyOf()

    init {
        require(this.nonce.size == AesGcm.NONCE_LENGTH) { "El nonce debe tener ${AesGcm.NONCE_LENGTH} bytes" }
    }

    override fun toString(): String = "Sealed(nonce=${nonce.size} B, ciphertext=${ciphertext.size} B)"
}

/** La etiqueta GCM no coincide. Sin mensaje ni causa: no se dice qué falló. */
class AuthenticationFailed : Exception()
