package com.luis.end_security.core.crypto

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** HMAC y SHA-256 con JCA (DEC-28). Los usan el backend falso y, más adelante, otras partes de la app. */
object Hashes {

    fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray = hmac("HmacSHA256", key, data)

    fun hmacSha1(key: ByteArray, data: ByteArray): ByteArray = hmac("HmacSHA1", key, data)

    fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

    /** Comparación en tiempo constante (`.claude/rules/crypto.md`). */
    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean = MessageDigest.isEqual(a, b)

    private fun hmac(algorithm: String, key: ByteArray, data: ByteArray): ByteArray =
        Mac.getInstance(algorithm).run {
            init(SecretKeySpec(key, algorithm))
            doFinal(data)
        }
}

/** Bytes aleatorios de [SecureRandom] (INV-04), p. ej. sales, tokens y la `vault_key`. */
object RandomBytes {
    private val random = SecureRandom()

    fun next(size: Int): ByteArray = ByteArray(size).also(random::nextBytes)

    /** Entero en `[from, until)`; también para cosas no secretas (regla 4: solo `SecureRandom`). */
    fun nextInt(from: Int, until: Int): Int = from + random.nextInt(until - from)
}
