package com.luis.end_security.feature.vault.domain

import java.net.URI
import java.time.Duration
import java.time.Instant

/** «editada hace …» del detalle (SCR-12), a partir de `password_changed_at`. */
sealed interface EditedAge {
    data object Now : EditedAge

    data class Minutes(val count: Int) : EditedAge

    data class Hours(val count: Int) : EditedAge

    data class Days(val count: Int) : EditedAge

    data class Months(val count: Int) : EditedAge

    data class Years(val count: Int) : EditedAge

    companion object {
        /** Redondea hacia abajo a la unidad más grande que cabe; una fecha futura (reloj movido) cuenta como «ahora». */
        fun of(changedAt: Instant, now: Instant): EditedAge {
            val elapsed = Duration.between(changedAt, now)
            return when {
                elapsed < Duration.ofMinutes(1) -> Now
                elapsed < Duration.ofHours(1) -> Minutes(elapsed.toMinutes().toInt())
                elapsed < Duration.ofDays(1) -> Hours(elapsed.toHours().toInt())
                elapsed < Duration.ofDays(30) -> Days(elapsed.toDays().toInt())
                elapsed < Duration.ofDays(365) -> Months((elapsed.toDays() / 30).toInt())
                else -> Years((elapsed.toDays() / 365).toInt())
            }
        }
    }
}

/**
 * El «Sitio web» que se guarda es texto libre («netflix.com»). Al abrirlo solo se permiten `http` y
 * `https`: nada de `javascript:`, `intent:`, `file:` ni `content:`.
 */
object SiteUrl {
    /** La dirección para abrir en el navegador, o `null` si no es una web. */
    fun toOpenable(raw: String?): String? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty() || text.any { it.isWhitespace() || it.isISOControl() }) return null
        val withScheme = if (SCHEME.containsMatchIn(text)) text else "https://$text"
        val uri = try {
            URI(withScheme)
        } catch (_: Exception) {
            return null
        }
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return null
        return withScheme.takeIf { !uri.host.isNullOrEmpty() }
    }

    // `algo:` seguido de algo que no es un puerto: «netflix.com:8080» no cuenta como esquema.
    private val SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]*:(?!\\d+(/|$))")
}
