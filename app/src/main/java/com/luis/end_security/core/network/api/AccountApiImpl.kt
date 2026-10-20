package com.luis.end_security.core.network.api

import com.luis.end_security.core.common.ApiResult
import com.luis.end_security.core.network.ApiClient
import com.luis.end_security.core.network.ApiMappers.toDomain
import com.luis.end_security.core.network.ApiMappers.toDto
import com.luis.end_security.core.network.ApiMappers.toPvkDto
import com.luis.end_security.core.network.Base64Url
import com.luis.end_security.core.network.dto.AccountDto
import com.luis.end_security.core.network.dto.AcceptedDto
import com.luis.end_security.core.network.dto.CodeRequestDto
import com.luis.end_security.core.network.dto.EmailChangeRequestDto
import com.luis.end_security.core.network.dto.KeyVersionDto
import com.luis.end_security.core.network.dto.MasterPasswordRequestDto
import com.luis.end_security.core.network.dto.ReauthDto
import com.luis.end_security.core.network.dto.ReauthRequestDto
import com.luis.end_security.core.network.dto.RecoveryCodesDto
import com.luis.end_security.core.network.dto.TwoFactorSetupDto
import com.luis.end_security.core.network.dto.UpdateAccountRequestDto
import com.luis.end_security.feature.account.domain.AccountApi
import com.luis.end_security.feature.account.domain.AccountInfo
import com.luis.end_security.feature.account.domain.MasterPasswordChange
import com.luis.end_security.feature.account.domain.Reauth
import com.luis.end_security.feature.account.domain.TwoFactorSetup
import javax.inject.Inject

/** [AccountApi] sobre el contrato (EP-13…EP-22). */
class AccountApiImpl @Inject constructor(
    private val client: ApiClient,
) : AccountApi {

    override suspend fun getAccount(): ApiResult<AccountInfo> =
        client.call<Unit, AccountDto, AccountInfo>("GET", "/account", null, null, AccountDto.serializer(), true) { it.toDomain() }

    override suspend fun updateDisplayName(displayName: String?): ApiResult<AccountInfo> = client.call(
        "PATCH", "/account", UpdateAccountRequestDto(displayName), UpdateAccountRequestDto.serializer(),
        AccountDto.serializer(), true,
    ) { it.toDomain() }

    override suspend fun requestEmailChange(newEmail: String, reauth: Reauth): ApiResult<Unit> = client.call(
        "POST", "/account/email", EmailChangeRequestDto(newEmail, reauth.toDto()), EmailChangeRequestDto.serializer(),
        AcceptedDto.serializer(), true,
    ) { }

    override suspend fun confirmEmailChange(code: String): ApiResult<AccountInfo> = client.call(
        "POST", "/account/email/verify", CodeRequestDto(code), CodeRequestDto.serializer(), AccountDto.serializer(), true,
    ) { it.toDomain() }

    override suspend fun changeMasterPassword(change: MasterPasswordChange): ApiResult<Int> {
        val body = MasterPasswordRequestDto(
            reauth = change.reauth.toDto(),
            expectedKeyVersion = change.expectedKeyVersion,
            newAuthKey = change.newAuthKey.withBytes(Base64Url::encode),
            newKdf = change.newKdf.toDto(),
            newProtectedVaultKey = change.newProtectedVaultKey.toPvkDto(),
        )
        return client.call(
            "POST", "/account/master-password", body, MasterPasswordRequestDto.serializer(), KeyVersionDto.serializer(), true,
        ) { it.keyVersion }
    }

    override suspend fun deleteAccount(reauth: Reauth): ApiResult<Unit> = client.callNoContent(
        "POST", "/account/delete", ReauthRequestDto(reauth.toDto()), ReauthRequestDto.serializer(), true,
    )

    override suspend fun startTwoFactorSetup(): ApiResult<TwoFactorSetup> =
        client.call<Unit, TwoFactorSetupDto, TwoFactorSetup>(
            "POST", "/account/2fa/setup", null, null, TwoFactorSetupDto.serializer(), true,
        ) { TwoFactorSetup(it.secret, it.issuer, it.accountName, it.algorithm, it.digits, it.periodSeconds) }

    override suspend fun confirmTwoFactor(code: String): ApiResult<List<String>> = client.call(
        "POST", "/account/2fa/confirm", CodeRequestDto(code), CodeRequestDto.serializer(), RecoveryCodesDto.serializer(), true,
    ) { it.recoveryCodes }

    override suspend fun disableTwoFactor(reauth: Reauth): ApiResult<Unit> = client.callNoContent(
        "POST", "/account/2fa/disable", ReauthRequestDto(reauth.toDto()), ReauthRequestDto.serializer(), true,
    )

    override suspend fun regenerateRecoveryCodes(reauth: Reauth): ApiResult<List<String>> = client.call(
        "POST", "/account/2fa/recovery-codes", ReauthRequestDto(reauth.toDto()), ReauthRequestDto.serializer(),
        RecoveryCodesDto.serializer(), true,
    ) { it.recoveryCodes }

    private fun Reauth.toDto() = ReauthDto(authKey.withBytes(Base64Url::encode), totpCode)
}
