package com.luis.end_security.core.crypto

import java.nio.charset.CharacterCodingException

/**
 * Jerarquía de llaves de DEC-05 con [SecureBytes], para usar fuera de `core/crypto`:
 * contraseña normalizada → `master_key` → `enc_key` / `auth_key`. Los arreglos intermedios se
 * borran aquí mismo.
 */
object KeyDerivation {

    /**
     * `master_key = Argon2id(UTF-8(contraseña), sal, parámetros)`.
     *
     * @param normalizedPassword ya normalizada con `MasterPasswordNormalizer` (DEC-31).
     * @throws CharacterCodingException si no se puede codificar en UTF-8 (al entrar o
     *   desbloquear se muestra como el error genérico).
     */
    fun deriveMasterKey(normalizedPassword: CharSequence, salt: ByteArray, params: KdfParams): SecureBytes {
        val password = MasterPasswordEncoder.encode(normalizedPassword)
        try {
            return SecureBytes.adopt(Argon2idKdf.derive(password, salt, params))
        } finally {
            password.wipe()
        }
    }

    /** `enc_key = HKDF-SHA256(master_key, "end-security/enc")`; envuelve la `vault_key`. */
    fun encKey(masterKey: SecureBytes): SecureBytes = masterKey.use { SecureBytes.adopt(Hkdf.encKey(it)) }

    /** `auth_key = HKDF-SHA256(master_key, "end-security/auth")`; lo único que viaja al servidor. */
    fun authKey(masterKey: SecureBytes): SecureBytes = masterKey.use { SecureBytes.adopt(Hkdf.authKey(it)) }
}
