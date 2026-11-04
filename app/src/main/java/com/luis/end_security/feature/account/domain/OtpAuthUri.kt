package com.luis.end_security.feature.account.domain

import java.net.URLEncoder

/**
 * URI `otpauth://totp/...` (Key URI Format de Google Authenticator) con la clave pendiente. Contiene el secreto: se
 * arma al tocar «Abrir app de autenticación» y no se guarda ni se registra.
 */
object OtpAuthUri {
    /** Un URI sin secreto, solo para preguntar al sistema si hay una app que abra `otpauth://`. */
    const val PROBE = "otpauth://totp/End%20Security"

    fun build(setup: TwoFactorSetup): String {
        val label = encode("${setup.issuer}:${setup.accountName}")
        val query = listOf(
            "secret" to setup.secret,
            "issuer" to setup.issuer,
            "algorithm" to setup.algorithm,
            "digits" to setup.digits.toString(),
            "period" to setup.periodSeconds.toString(),
        ).joinToString("&") { (key, value) -> "$key=${encode(value)}" }
        return "otpauth://totp/$label?$query"
    }

    // URLEncoder usa «+» para el espacio; en un URI va «%20».
    private fun encode(text: String): String = URLEncoder.encode(text, Charsets.UTF_8.name()).replace("+", "%20")
}
