package com.luis.end_security.feature.account.domain

/** Resultado de eliminar la cuenta (OP-28, EP-18). */
sealed interface DeleteAccountResult {
    /**
     * El servidor respondió 204: la cuenta ya no existe. [cleanupComplete] es `false` si parte de la limpieza local no se pudo
     * hacer; queda una marca y se reintenta al siguiente arranque. En los dos casos se llega a «Cuenta eliminada».
     */
    data class Deleted(val cleanupComplete: Boolean) : DeleteAccountResult

    /** `reauth_required`: «Contraseña o código incorrectos.» */
    data object WrongCredentials : DeleteAccountResult

    /** La reautenticación falló y el servidor tiene una `key_version` mayor: la contraseña cambió en otro dispositivo. */
    data object KeyVersionConflict : DeleteAccountResult

    /** Límite de intentos: la cuenta regresiva de siempre. */
    data class RateLimited(val retryAfterSeconds: Int) : DeleteAccountResult

    /** Sin red, 5xx o cualquier otro fallo: nada local se tocó. */
    data object Generic : DeleteAccountResult
}

/**
 * Eliminar la cuenta (OP-28; EP-18). Reautentica con la contraseña maestra (y el código de la app si hay 2FA) derivando la
 * `auth_key` en el teléfono ([ReauthRunner]); la contraseña nunca sale de aquí. Con el 204 deja el teléfono limpio.
 */
interface DeleteAccount {
    /** Elementos locales no borrados: «Se borrarán tus N contraseñas…». */
    suspend fun itemCount(): Int

    suspend fun delete(password: CharSequence, totpCode: String?): DeleteAccountResult
}
