package com.luis.end_security.feature.account.domain

import com.luis.end_security.core.common.ApiResult

/**
 * Verificación en dos pasos desde Ajustes (OP-22…OP-25; EP-19…EP-22). Todo pasa por el servidor: la app **no** genera
 * códigos TOTP (DEC-11). Los secretos (clave, códigos de respaldo) se devuelven al ViewModel y no se guardan aquí.
 */
interface TwoFactorRepository {
    /** `twoFactorEnabled` de la cuenta; `null` sin red o con error. */
    suspend fun isEnabled(): Boolean?

    /** EP-19: clave pendiente (una nueva en cada llamada si aún no se confirmó). */
    suspend fun startSetup(): ApiResult<TwoFactorSetup>

    /** EP-20: confirma con el primer código y devuelve los 10 códigos de respaldo (`XXXX-XXXX`). */
    suspend fun confirm(code: String): ApiResult<List<String>>

    /** EP-21: desactiva; reautentica con la contraseña maestra y el código de la app. */
    suspend fun disable(password: CharSequence, totpCode: String): ApiResult<Unit>

    /** EP-22: códigos de respaldo nuevos (los anteriores dejan de funcionar). */
    suspend fun regenerateRecoveryCodes(password: CharSequence, totpCode: String): ApiResult<List<String>>
}

/**
 * Reautenticación (`Reauth` del contrato) para operaciones sensibles: deriva la `auth_key` **en el teléfono** con los
 * parámetros guardados en `account`, igual que al entrar, y la presta a [block]; después la borra. La contraseña maestra
 * nunca sale del teléfono. La usan 2 pasos (T17), Contraseña maestra (T18) y Eliminar cuenta (T19).
 */
interface ReauthRunner {
    suspend fun <T> run(
        password: CharSequence,
        totpCode: String?,
        block: suspend (Reauth) -> ApiResult<T>,
    ): ApiResult<T>
}
