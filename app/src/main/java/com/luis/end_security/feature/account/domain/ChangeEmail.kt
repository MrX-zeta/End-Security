package com.luis.end_security.feature.account.domain

/** Resultado de pedir el código al correo nuevo (EP-15). */
sealed interface RequestEmailResult {
    /** 202: «Revisa tu correo». También si el correo ya estaba en uso (anti-enumeración): no se distingue. */
    data object Accepted : RequestEmailResult

    /** `reauth_required`: «Contraseña o código incorrectos.» */
    data object WrongCredentials : RequestEmailResult

    /** La reautenticación falló y el servidor tiene una `key_version` mayor: la contraseña cambió en otro dispositivo. */
    data object KeyVersionConflict : RequestEmailResult

    /** `429`: límites de DEC-63 (3 por hora, 5 por día, 24 h tras un cambio). [retryAfterSeconds] es lo que falta. */
    data class RateLimited(val retryAfterSeconds: Int) : RequestEmailResult

    /** `400 validation_failed`: el servidor lo considera igual al actual. */
    data object SameAsCurrent : RequestEmailResult

    data object Generic : RequestEmailResult
}

/** Resultado de confirmar el código (EP-16). */
sealed interface ConfirmEmailResult {
    /** 200: el correo ya cambió; [email] es el que devolvió el servidor y ya está en Room. */
    data class Changed(val email: String) : ConfirmEmailResult

    data class WrongCode(val attemptsLeft: Int) : ConfirmEmailResult

    /** Vencido o sin intentos: «Este código ya no sirve. Pide uno nuevo.» */
    data object Expired : ConfirmEmailResult

    data class RateLimited(val retryAfterSeconds: Int) : ConfirmEmailResult

    data object Generic : ConfirmEmailResult
}

/**
 * Cambiar el correo de la cuenta (OP-26; EP-15, EP-16; DEC-63). La reautenticación deriva la `auth_key` en el teléfono
 * ([ReauthRunner]); la contraseña maestra nunca sale de aquí. El correo **no** cambia hasta confirmar el código.
 */
interface ChangeEmail {
    /** El correo de la cuenta guardado en este teléfono (Room); `null` sin cuenta. */
    suspend fun currentEmail(): String?

    /** `twoFactorEnabled` de la cuenta; `null` sin red. */
    suspend fun twoFactorEnabled(): Boolean?

    /** EP-15. [newEmail] ya normalizado ([com.luis.end_security.core.common.EmailNormalizer]); [totpCode] solo con 2FA activa. */
    suspend fun request(newEmail: String, password: CharSequence, totpCode: String?): RequestEmailResult

    /** EP-16. Con el 200 actualiza `account.email` en Room (solo si el `userId` coincide). */
    suspend fun confirm(code: String): ConfirmEmailResult
}
