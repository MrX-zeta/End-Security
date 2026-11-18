package com.luis.end_security.core.network.backend

import com.luis.end_security.core.network.ApiTransport
import com.luis.end_security.core.network.HttpRequest
import com.luis.end_security.core.network.HttpResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Transporte de la variante `prod` (DEC-65): el contrato de la app pasa por [BackendTranslator] y sale con OkHttp al
 * backend real. Sin logs: ni cuerpos, ni tokens, ni URL con consulta (INV-06).
 */
class BackendTransport(
    private val baseUrl: String,
    private val client: OkHttpClient,
    private val translator: BackendTranslator,
) : ApiTransport {

    override suspend fun execute(request: HttpRequest): HttpResponse {
        // TODO(BR-26): sin URL de producción el release no tiene servidor: «sin conexión».
        if (baseUrl.isEmpty()) throw IOException("Sin servidor configurado")
        return translator.translate(request) { call -> send(call, request.requestId.toString()) }
    }

    private suspend fun send(call: BackendCall, requestId: String): BackendReply = withContext(Dispatchers.IO) {
        val body = call.body?.toRequestBody(JSON)
        val builder = Request.Builder()
            .url(baseUrl + call.path)
            .method(call.method, body ?: if (call.method in BODY_REQUIRED) EMPTY else null)
            // Para unir la línea de syslog del servidor con esta petición (DEC-62).
            .header("X-Request-Id", requestId)
        call.bearer?.let { builder.header("Authorization", "Bearer $it") }
        client.newCall(builder.build()).execute().use { response ->
            BackendReply(
                status = response.code,
                body = response.body?.string(),
                retryAfterSeconds = response.header("Retry-After")?.toIntOrNull(),
            )
        }
    }

    private companion object {
        val JSON = "application/json".toMediaType()
        val EMPTY = ByteArray(0).toRequestBody(JSON)
        val BODY_REQUIRED = setOf("POST", "PUT", "PATCH")
    }
}
