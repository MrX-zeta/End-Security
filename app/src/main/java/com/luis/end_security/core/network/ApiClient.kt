package com.luis.end_security.core.network

import com.luis.end_security.core.common.ApiError
import com.luis.end_security.core.common.ApiResult
import com.luis.end_security.core.network.dto.ProblemDto
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Tokens de la sesión para las llamadas autenticadas. Lo implementa `TokenManager`. */
interface TokenProvider {
    /** El `accessToken` vigente, o `null` sin sesión. */
    suspend fun accessToken(): String?

    /**
     * Renueva después de un `token_expired` (EP-08), una renovación a la vez (regla 9). Si otra
     * llamada ya renovó mientras tanto, devuelve ese token sin renovar otra vez.
     */
    suspend fun refreshAfterExpiry(expiredAccessToken: String): RefreshOutcome

    /** `session_revoked`: borra los tokens y avisa para cerrar la sesión local. */
    suspend fun onSessionRevoked()
}

/** Resultado de una renovación de tokens. */
sealed interface RefreshOutcome {
    class Renewed(val accessToken: String) : RefreshOutcome {
        override fun toString(): String = "Renewed(***)"
    }

    /** La sesión ya no sirve (`session_revoked` o sin tokens). */
    data object Revoked : RefreshOutcome

    /** Sin red u otro error: se reintenta después, sin cerrar la sesión. */
    data object Unavailable : RefreshOutcome
}

/**
 * Hace las llamadas del contrato sobre un [ApiTransport]: JSON ↔ DTO, `Authorization`, errores
 * *problem+json* → [ApiError], y la renovación de tokens. Lo usan las implementaciones de
 * `AuthApi`, `VaultApi`, `AccountApi` y `SessionApi`.
 */
@Singleton
class ApiClient @Inject constructor(
    private val transport: ApiTransport,
    private val tokens: TokenProvider,
) {

    /**
     * [transform] convierte el DTO al dominio dentro del manejo de errores: una respuesta mal
     * formada (Base64url, UUID, fecha o campo faltante) es `ServerError`, no una excepción.
     */
    suspend fun <Req, Res, T> call(
        method: String,
        path: String,
        request: Req?,
        requestSerializer: KSerializer<Req>?,
        responseSerializer: KSerializer<Res>,
        authenticated: Boolean,
        transform: (Res) -> T,
    ): ApiResult<T> = send(method, path, encode(request, requestSerializer), authenticated) { body ->
        transform(ApiJson.decodeFromString(responseSerializer, body))
    }

    /** Para respuestas sin cuerpo útil (202 `accepted`, 204). */
    suspend fun <Req> callNoContent(
        method: String,
        path: String,
        request: Req?,
        requestSerializer: KSerializer<Req>?,
        authenticated: Boolean,
    ): ApiResult<Unit> = send(method, path, encode(request, requestSerializer), authenticated) { }

    private fun <Req> encode(request: Req?, serializer: KSerializer<Req>?): String? =
        if (request == null || serializer == null) null else ApiJson.encodeToString(serializer, request)

    private suspend fun <T> send(
        method: String,
        path: String,
        body: String?,
        authenticated: Boolean,
        parse: (String) -> T,
    ): ApiResult<T> {
        try {
            var token: String? = null
            if (authenticated) {
                token = tokens.accessToken() ?: return ApiResult.Failure(ApiError.SessionRevoked)
            }
            var response = transport.execute(HttpRequest(method, path, body, token))
            var error = errorOf(response)
            if (authenticated && error == ApiError.TokenExpired) {
                val renewed = when (val outcome = tokens.refreshAfterExpiry(token!!)) {
                    is RefreshOutcome.Renewed -> outcome.accessToken
                    RefreshOutcome.Revoked -> return ApiResult.Failure(ApiError.SessionRevoked)
                    RefreshOutcome.Unavailable -> return ApiResult.Failure(ApiError.NetworkUnavailable)
                }
                response = transport.execute(HttpRequest(method, path, body, renewed))
                error = errorOf(response)
            }
            if (error == ApiError.SessionRevoked && authenticated) tokens.onSessionRevoked()
            if (error != null) return ApiResult.Failure(error)
            return ApiResult.Success(parse(response.body.orEmpty()))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: IOException) {
            return ApiResult.Failure(ApiError.NetworkUnavailable)
        } catch (_: SerializationException) {
            return ApiResult.Failure(ApiError.ServerError)
        } catch (_: IllegalArgumentException) {
            // Base64url, UUID, fecha o campo obligatorio inválidos en la respuesta.
            return ApiResult.Failure(ApiError.ServerError)
        } catch (_: IllegalStateException) {
            return ApiResult.Failure(ApiError.ServerError)
        }
    }

    companion object {
        /** `null` si la respuesta es 2xx. */
        fun errorOf(response: HttpResponse): ApiError? {
            if (response.status in 200..299) return null
            val problem = try {
                response.body?.let { ApiJson.decodeFromString(ProblemDto.serializer(), it) }
            } catch (_: SerializationException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
            return when (problem?.code) {
                "validation_failed" -> ApiError.ValidationFailed
                "invalid_credentials" -> ApiError.InvalidCredentials
                "invalid_code" -> ApiError.InvalidCode(problem.attemptsRemaining)
                "challenge_expired" -> ApiError.ChallengeExpired
                "token_expired" -> ApiError.TokenExpired
                "session_revoked" -> ApiError.SessionRevoked
                "email_not_verified" -> ApiError.EmailNotVerified
                "reauth_required" -> ApiError.ReauthRequired
                "not_found" -> ApiError.NotFound
                "version_conflict" -> ApiError.VersionConflict(problem.currentVersion ?: 0, problem.currentRevision)
                "key_version_conflict" -> ApiError.KeyVersionConflict
                "two_factor_already_enabled" -> ApiError.TwoFactorAlreadyEnabled
                "cursor_expired" -> ApiError.CursorExpired
                "rate_limited" -> ApiError.RateLimited(
                    (problem.retryAfterSeconds ?: response.retryAfterSeconds ?: DEFAULT_RETRY_SECONDS).coerceAtLeast(1),
                )
                else -> ApiError.ServerError
            }
        }

        private const val DEFAULT_RETRY_SECONDS = 60
    }
}
