package com.luis.end_security.core.crypto

import java.security.GeneralSecurityException
import java.util.UUID
import javax.crypto.Cipher

/** El bloque biométrico no se pudo abrir: alterado, otra AAD o llave distinta. */
class BiometricUnwrapFailed : Exception()

/**
 * Envuelve y desenvuelve la `vault_key` con el `Cipher` del Keystore que ya autenticó el usuario con su huella
 * (DEC-09, OP-35/OP-20). Aquí vive el único código que toca los bytes de la `vault_key` para esto (DEC-27).
 *
 * AAD = `end-security/bio|userId|keyVersion`: ata el bloque a la cuenta y a la versión de llaves. El IV lo elige el
 * Keystore (aleatorio) y se guarda aparte, en `account.bio_iv`.
 */
object BiometricWrap {

    fun aad(userId: UUID, keyVersion: Int): ByteArray = "end-security/bio|$userId|$keyVersion".toByteArray(Charsets.UTF_8)

    /** Cifra [vaultKey] con [cipher] (en modo cifrado y ya autenticado). Devuelve el texto cifrado con su etiqueta. */
    fun wrap(cipher: Cipher, vaultKey: SecretReader, userId: UUID, keyVersion: Int): ByteArray =
        vaultKey.withBytes { bytes ->
            cipher.updateAAD(aad(userId, keyVersion))
            cipher.doFinal(bytes)
        }

    /**
     * Descifra [wrapped] con [cipher] (en modo descifrado y ya autenticado) y devuelve la `vault_key` en un
     * [SecureBytes]. El arreglo intermedio se sobrescribe.
     * @throws BiometricUnwrapFailed si el bloque no abre (etiqueta GCM, AAD o largo incorrectos).
     */
    fun unwrap(cipher: Cipher, wrapped: ByteArray, userId: UUID, keyVersion: Int): SecureBytes {
        val plain = try {
            cipher.updateAAD(aad(userId, keyVersion))
            cipher.doFinal(wrapped)
        } catch (_: GeneralSecurityException) {
            throw BiometricUnwrapFailed()
        } catch (_: IllegalStateException) {
            throw BiometricUnwrapFailed()
        }
        try {
            if (plain.size != VAULT_KEY_SIZE) throw BiometricUnwrapFailed()
            return SecureBytes.copyOf(plain)
        } finally {
            plain.wipe()
        }
    }

    private const val VAULT_KEY_SIZE = 32
}
