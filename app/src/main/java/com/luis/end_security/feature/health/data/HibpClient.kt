package com.luis.end_security.feature.health.data

import com.luis.end_security.core.crypto.Hashes
import com.luis.end_security.feature.health.domain.BreachApi
import com.luis.end_security.feature.health.domain.BreachLookup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cliente mínimo de la range API de Have I Been Pwned (DEC-21), aparte de la API propia (no usa `ApiTransport`):
 * `GET {base}/range/{5 hex de SHA-1 en mayúsculas}` con `Add-Padding: true` y un `User-Agent` propio. El sufijo se busca
 * en memoria y **las líneas con cuenta 0 son relleno**. Tiempo de espera de 10 s. Nada de esto va a logs.
 */
@Singleton
class HibpClient(
    private val baseUrl: String,
    private val timeoutMillis: Int,
) : BreachApi {

    @Inject
    constructor() : this(BASE_URL, TIMEOUT_MILLIS)

    override suspend fun lookup(password: String): BreachLookup = withContext(Dispatchers.IO) {
        val bytes = password.toByteArray(Charsets.UTF_8)
        val raw = Hashes.sha1(bytes)
        bytes.fill(0)
        val digest = raw.joinToString("") { "%02X".format(it) }
        raw.fill(0)
        val prefix = digest.substring(0, PREFIX_LENGTH)
        val suffix = digest.substring(PREFIX_LENGTH)
        try {
            val connection = (URL("$baseUrl/range/$prefix").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = timeoutMillis
                readTimeout = timeoutMillis
                instanceFollowRedirects = false
                setRequestProperty("Add-Padding", "true")
                setRequestProperty("User-Agent", USER_AGENT)
            }
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext BreachLookup.Unavailable
                val body = readLimited(connection.inputStream) ?: return@withContext BreachLookup.Unavailable
                match(body, suffix)
            } finally {
                connection.disconnect()
            }
        } catch (_: IOException) {
            // Sin red, DNS, tiempo de espera o corte a la mitad.
            BreachLookup.Offline
        }
    }

    internal companion object {
        const val BASE_URL = "https://api.pwnedpasswords.com"
        const val TIMEOUT_MILLIS = 10_000
        const val PREFIX_LENGTH = 5
        const val USER_AGENT = "EndSecurity-Android"

        /** Tope del cuerpo (una respuesta real, con relleno, pesa decenas de KB). */
        const val MAX_BODY_BYTES = 2 * 1024 * 1024

        /** Lee hasta [MAX_BODY_BYTES]; `null` si se pasa (respuesta sospechosa). */
        fun readLimited(input: java.io.InputStream): String? {
            input.use { stream ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8 * 1024)
                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    if (out.size() > MAX_BODY_BYTES) return null
                }
                return out.toString(Charsets.UTF_8.name())
            }
        }

        /**
         * Busca el [suffix] en las líneas `SUFIJO:CUENTA`; una cuenta 0 es relleno. Una respuesta sin una sola línea bien
         * formada (vacía, de otro formato) o con una cuenta negativa **no se toma por «limpia»**: es `Unavailable`.
         */
        fun match(body: String, suffix: String): BreachLookup {
            var wellFormed = 0
            var result: BreachLookup? = null
            for (line in body.lineSequence()) {
                val separator = line.indexOf(':')
                if (separator != suffix.length) continue
                val count = line.substring(separator + 1).trim().toIntOrNull() ?: continue
                if (count < 0) return BreachLookup.Unavailable
                wellFormed++
                if (result == null && line.regionMatches(0, suffix, 0, suffix.length, ignoreCase = true)) {
                    result = if (count > 0) BreachLookup.Found(count) else BreachLookup.Clean
                }
            }
            if (wellFormed == 0) return BreachLookup.Unavailable
            return result ?: BreachLookup.Clean
        }
    }
}
