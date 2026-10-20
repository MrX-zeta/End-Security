package com.luis.end_security.core.network

import kotlinx.serialization.json.Json
import java.io.IOException
import java.util.Base64
import java.util.UUID

/** Base64url sin relleno (RFC 4648 §5), como todos los bytes del contrato. */
object Base64Url {
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun encode(bytes: ByteArray): String = encoder.encodeToString(bytes)

    /** @throws IllegalArgumentException si no es Base64url válido (incluye relleno o `+`/`/`). */
    fun decode(text: String): ByteArray {
        require(text.all { it.isLetterOrDigit() && it.code < 128 || it == '-' || it == '_' }) { "Base64url inválido" }
        return decoder.decode(text)
    }
}

/** JSON del contrato: ignora campos nuevos del servidor y no envía los opcionales vacíos. */
val ApiJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
    explicitNulls = true
}

/** Una petición HTTP ya armada. El cuerpo es JSON; nunca se registra (README, regla 9). */
class HttpRequest(
    val method: String,
    /** Ruta con consulta, relativa a `/v1`, p. ej. `/vault/items?since=0`. */
    val path: String,
    val body: String?,
    val bearerToken: String?,
    val requestId: UUID = UUID.randomUUID(),
) {
    override fun toString(): String = "HttpRequest($method ${path.substringBefore('?')})"
}

class HttpResponse(
    val status: Int,
    val body: String?,
    /** Encabezado `Retry-After`, en segundos. */
    val retryAfterSeconds: Int? = null,
) {
    override fun toString(): String = "HttpResponse($status)"
}

/**
 * El canal hacia el backend. Hoy es el backend falso (`core/network/fake`); cuando exista el
 * servidor será OkHttp con pinning (DEC-22). Las implementaciones de las API no cambian.
 */
interface ApiTransport {
    /** @throws IOException sin red o tiempo agotado. */
    suspend fun execute(request: HttpRequest): HttpResponse
}
