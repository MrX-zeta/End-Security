package com.luis.end_security.core.common

/** Resultado de una llamada al backend (`docs/api`): el valor o un [ApiError] tipado. */
sealed interface ApiResult<out T> {
    data class Success<out T>(val value: T) : ApiResult<T>

    data class Failure(val error: ApiError) : ApiResult<Nothing>
}

inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Success -> ApiResult.Success(transform(value))
    is ApiResult.Failure -> this
}

/**
 * Los `code` del contrato (README de `docs/api`, tabla de errores) más la falta de red. La app
 * decide el texto a partir de esto; nunca usa el `title` del servidor.
 */
sealed interface ApiError {
    data object ValidationFailed : ApiError

    /** Correo o `authKey` incorrectos, o el correo no existe (no se distingue). */
    data object InvalidCredentials : ApiError

    /** `attemptsRemaining` solo viene en la verificación de correo. */
    data class InvalidCode(val attemptsRemaining: Int?) : ApiError

    data object ChallengeExpired : ApiError

    data object TokenExpired : ApiError

    data object SessionRevoked : ApiError

    data object EmailNotVerified : ApiError

    data object ReauthRequired : ApiError

    data object NotFound : ApiError

    data class VersionConflict(val currentVersion: Long, val currentRevision: Long?) : ApiError

    data object KeyVersionConflict : ApiError

    data object TwoFactorAlreadyEnabled : ApiError

    data object CursorExpired : ApiError

    data class RateLimited(val retryAfterSeconds: Int) : ApiError

    data object ServerError : ApiError

    /** Sin conexión o tiempo agotado. */
    data object NetworkUnavailable : ApiError
}
