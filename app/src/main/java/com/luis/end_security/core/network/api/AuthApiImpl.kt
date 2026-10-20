package com.luis.end_security.core.network.api

import com.luis.end_security.core.common.ApiResult
import com.luis.end_security.core.crypto.SecretReader
import com.luis.end_security.core.network.ApiClient
import com.luis.end_security.core.network.ApiMappers.toDomain
import com.luis.end_security.core.network.ApiMappers.toDto
import com.luis.end_security.core.network.ApiMappers.toPvkDto
import com.luis.end_security.core.network.Base64Url
import com.luis.end_security.core.network.dto.AcceptedDto
import com.luis.end_security.core.network.dto.AuthResponseDto
import com.luis.end_security.core.network.dto.EmailRequestDto
import com.luis.end_security.core.network.dto.LoginRequestDto
import com.luis.end_security.core.network.dto.PreloginResponseDto
import com.luis.end_security.core.network.dto.RecoveryCodeRequestDto
import com.luis.end_security.core.network.dto.RefreshRequestDto
import com.luis.end_security.core.network.dto.RegisterRequestDto
import com.luis.end_security.core.network.dto.TokensDto
import com.luis.end_security.core.network.dto.TwoFactorVerifyRequestDto
import com.luis.end_security.core.network.dto.VerifyEmailRequestDto
import com.luis.end_security.feature.account.domain.Authenticated
import com.luis.end_security.feature.account.domain.ServerKdf
import com.luis.end_security.feature.account.domain.SessionTokens
import com.luis.end_security.feature.auth.domain.AuthApi
import com.luis.end_security.feature.auth.domain.LoginResult
import com.luis.end_security.feature.auth.domain.RecoveryLogin
import com.luis.end_security.feature.auth.domain.RegisterRequest
import javax.inject.Inject

/** [AuthApi] sobre el contrato (EP-01…EP-09). */
class AuthApiImpl @Inject constructor(
    private val client: ApiClient,
) : AuthApi {

    override suspend fun prelogin(email: String): ApiResult<ServerKdf> = client.call(
        "POST", "/auth/prelogin", EmailRequestDto(email), EmailRequestDto.serializer(),
        PreloginResponseDto.serializer(), authenticated = false,
    ) { it.kdf.toDomain() }

    override suspend fun register(request: RegisterRequest): ApiResult<Unit> {
        val body = RegisterRequestDto(
            userId = request.userId.toString(),
            email = request.email,
            displayName = request.displayName,
            authKey = request.authKey.withBytes(Base64Url::encode),
            kdf = request.kdf.toDto(),
            protectedVaultKey = request.protectedVaultKey.toPvkDto(),
        )
        return client.call("POST", "/auth/register", body, RegisterRequestDto.serializer(), AcceptedDto.serializer(), false) { }
    }

    override suspend fun verifyEmail(email: String, code: String, deviceName: String): ApiResult<Authenticated> =
        client.call(
            "POST", "/auth/email/verify", VerifyEmailRequestDto(email, code, deviceName),
            VerifyEmailRequestDto.serializer(), AuthResponseDto.serializer(), authenticated = false,
        ) { it.toAuthenticated() }

    override suspend fun resendVerification(email: String): ApiResult<Unit> = client.call(
        "POST", "/auth/email/resend", EmailRequestDto(email), EmailRequestDto.serializer(),
        AcceptedDto.serializer(), authenticated = false,
    ) { }

    override suspend fun login(email: String, authKey: SecretReader, deviceName: String): ApiResult<LoginResult> {
        val body = LoginRequestDto(email, authKey.withBytes(Base64Url::encode), deviceName)
        return client.call("POST", "/auth/login", body, LoginRequestDto.serializer(), AuthResponseDto.serializer(), false) { response ->
                when (response.status) {
                    STATUS_AUTHENTICATED -> LoginResult.Success(response.toAuthenticated())
                    STATUS_TWO_FACTOR -> LoginResult.TwoFactorRequired(
                        challengeToken = requireNotNull(response.challengeToken),
                        expiresInSeconds = requireNotNull(response.challengeExpiresIn),
                    )
                    else -> throw IllegalArgumentException("status desconocido")
                }
            }
    }

    override suspend fun verifyTwoFactor(challengeToken: String, code: String): ApiResult<Authenticated> = client.call(
        "POST", "/auth/2fa/verify", TwoFactorVerifyRequestDto(challengeToken, code),
        TwoFactorVerifyRequestDto.serializer(), AuthResponseDto.serializer(), authenticated = false,
    ) { it.toAuthenticated() }

    override suspend fun verifyRecoveryCode(challengeToken: String, recoveryCode: String): ApiResult<RecoveryLogin> =
        client.call(
            "POST", "/auth/2fa/recovery", RecoveryCodeRequestDto(challengeToken, recoveryCode),
            RecoveryCodeRequestDto.serializer(), AuthResponseDto.serializer(), authenticated = false,
        ) { RecoveryLogin(it.toAuthenticated(), requireNotNull(it.recoveryCodesRemaining)) }

    override suspend fun refresh(refreshToken: String): ApiResult<SessionTokens> = client.call(
        "POST", "/auth/refresh", RefreshRequestDto(refreshToken), RefreshRequestDto.serializer(),
        TokensDto.serializer(), authenticated = false,
    ) { it.toDomain() }

    override suspend fun logout(): ApiResult<Unit> =
        client.callNoContent<Unit>("POST", "/auth/logout", null, null, authenticated = true)

    private fun AuthResponseDto.toAuthenticated(): Authenticated {
        require(status == STATUS_AUTHENTICATED) { "Se esperaba authenticated" }
        return Authenticated(requireNotNull(tokens).toDomain(), requireNotNull(account).toDomain())
    }

    private companion object {
        const val STATUS_AUTHENTICATED = "authenticated"
        const val STATUS_TWO_FACTOR = "two_factor_required"
    }
}
