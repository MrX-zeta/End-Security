package com.luis.end_security.feature.account.domain

/** Cómo terminó el cambio de contraseña maestra (OP-27, EP-17). */
sealed interface ChangePasswordResult {
    /**
     * Cambiada en el servidor y en este teléfono; la sesión sigue abierta con la misma `vault_key`. Con
     * [biometricWasEnabled], la huella hay que volver a envolverla con la `key_version` nueva (DEC-09).
     */
    data class Success(val biometricWasEnabled: Boolean) : ChangePasswordResult

    /** `reauth_required`: la contraseña actual o el código de la app no son los correctos. */
    data object WrongCredentials : ChangePasswordResult

    /** 409 `key_version_conflict`: la contraseña cambió en otro dispositivo; no se toca nada local. */
    data object KeyVersionConflict : ChangePasswordResult

    /** Demasiados intentos de reautenticación. */
    data class RateLimited(val retryAfterSeconds: Int) : ChangePasswordResult

    /** Sin red, 5xx u otro error: no se cambió nada. */
    data object Generic : ChangePasswordResult

    /** La nueva es igual a la actual (texto ya normalizado): no se manda nada. */
    data object SameAsCurrent : ChangePasswordResult

    /** La nueva no cumple la política de DEC-31: no se manda nada. */
    data object InvalidNewPassword : ChangePasswordResult

    /**
     * El servidor respondió 200 pero no se pudo guardar en Room (ni al reintentar): se cerró la sesión local para no
     * dejar parámetros viejos. El usuario entra con la contraseña nueva.
     */
    data object LocalSaveFailedSignedOut : ChangePasswordResult
}

/**
 * Cambiar la contraseña maestra (OP-27, DEC-05): la **misma `vault_key`** se vuelve a envolver con una sal y una
 * `enc_key` nuevas; los elementos no se vuelven a cifrar. Las contraseñas llegan como `CharSequence` y no se guardan.
 */
interface ChangeMasterPassword {
    suspend fun change(current: CharSequence, totpCode: String?, newPassword: CharSequence): ChangePasswordResult
}
