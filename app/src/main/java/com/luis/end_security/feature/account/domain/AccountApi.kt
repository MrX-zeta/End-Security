package com.luis.end_security.feature.account.domain

import com.luis.end_security.core.common.ApiResult

/** Cuenta y verificación en dos pasos (contrato `docs/api`, EP-13…EP-22). Requieren sesión. */
interface AccountApi {
    /** EP-13 `GET /account`. */
    suspend fun getAccount(): ApiResult<AccountInfo>

    /** EP-14 `PATCH /account`. `null` borra el nombre visible. */
    suspend fun updateDisplayName(displayName: String?): ApiResult<AccountInfo>

    /** EP-15 `POST /account/email`: 202 aunque el correo nuevo ya exista. */
    suspend fun requestEmailChange(newEmail: String, reauth: Reauth): ApiResult<Unit>

    /** EP-16 `POST /account/email/verify`. */
    suspend fun confirmEmailChange(code: String): ApiResult<AccountInfo>

    /** EP-17 `POST /account/master-password`: devuelve la `keyVersion` nueva. */
    suspend fun changeMasterPassword(change: MasterPasswordChange): ApiResult<Int>

    /** EP-18 `POST /account/delete`. */
    suspend fun deleteAccount(reauth: Reauth): ApiResult<Unit>

    /** EP-19 `POST /account/2fa/setup`. */
    suspend fun startTwoFactorSetup(): ApiResult<TwoFactorSetup>

    /** EP-20 `POST /account/2fa/confirm`: los 10 códigos de respaldo, en formato `XXXX-XXXX`. */
    suspend fun confirmTwoFactor(code: String): ApiResult<List<String>>

    /** EP-21 `POST /account/2fa/disable`. */
    suspend fun disableTwoFactor(reauth: Reauth): ApiResult<Unit>

    /** EP-22 `POST /account/2fa/recovery-codes`. */
    suspend fun regenerateRecoveryCodes(reauth: Reauth): ApiResult<List<String>>
}
