package com.luis.end_security.core.crypto

/**
 * Sobrescribe con ceros una llave o un búfer que ya no se usa (DEC-27, mejor esfuerzo: la JVM
 * puede haber copiado el arreglo antes).
 */
fun ByteArray.wipe() {
    fill(0)
}
