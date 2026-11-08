package com.luis.end_security.core.crypto

/** Hexadecimal para los vectores de prueba. */
internal fun hex(value: String): ByteArray {
    val clean = value.filterNot(Char::isWhitespace)
    return ByteArray(clean.length / 2) { clean.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}

internal fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

/** Arreglo con los bytes `first..last`, como en los vectores de RFC 5869. */
internal fun bytesRange(first: Int, last: Int): ByteArray = ByteArray(last - first + 1) { (first + it).toByte() }
