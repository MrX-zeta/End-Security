package com.luis.end_security.feature.vault.domain

/**
 * Tope de cada campo del formulario de la bóveda, en unidades UTF-16 (F-05). La suma, aun a 3 bytes
 * por carácter, cabe en el bloque cifrado máximo del servidor (32 KiB); la contraseña cubre el
 * máximo del generador (64) con holgura.
 */
object ItemLimits {
    const val NAME = 128
    const val USERNAME = 256
    const val PASSWORD = 256
    const val URL = 2048
    const val NOTES = 5000

    /** Peor caso de bytes del texto en claro de un elemento; debe quedar bajo el bloque de 32 KiB. */
    const val WORST_CASE_BYTES = 3 * (NAME + USERNAME + PASSWORD + URL + NOTES)
}
