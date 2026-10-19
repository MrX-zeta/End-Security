package com.luis.end_security.core.crypto

/**
 * Parámetros de Argon2id (DEC-05, PEND-01). Llegan del servidor en el prelogin, así que se validan:
 * - piso: los parámetros elegidos en PEND-01 (64 MiB, t = 3, p = 1). Un servidor malicioso no
 *   puede pedir menos para abaratar un ataque sin conexión contra `auth_key`;
 * - topes, para que tampoco pueda pedir tanta memoria o tiempo que la app se cierre o se congele.
 */
data class KdfParams(
    val memoryKiB: Int,
    val iterations: Int,
    val parallelism: Int,
) {
    init {
        require(parallelism in 1..MAX_PARALLELISM) { "Paralelismo fuera de rango: $parallelism" }
        require(iterations in 1..MAX_ITERATIONS) { "Pasadas fuera de rango: $iterations" }
        require(memoryKiB <= MAX_MEMORY_KIB) { "Memoria por encima del tope: $memoryKiB KiB" }
        require(memoryKiB.toLong() * iterations <= MAX_COST) { "Costo por encima del tope: $memoryKiB KiB × $iterations" }
        require(memoryKiB >= MIN_MEMORY_KIB && iterations >= MIN_ITERATIONS) {
            "Parámetros por debajo del piso: $memoryKiB KiB, t=$iterations"
        }
    }

    companion object {
        /** Piso de PEND-01: supera todas las configuraciones de OWASP. */
        const val MIN_MEMORY_KIB = 65_536
        const val MIN_ITERATIONS = 3

        /** Los parámetros con los que se crean las cuentas nuevas (PEND-01). */
        val DEFAULT = KdfParams(memoryKiB = 65_536, iterations = 3, parallelism = 1)

        /**
         * 128 MiB: el doble de lo más alto que se mide (64 MiB). Bouncy Castle usa algo más que eso
         * de heap, y muchos teléfonos dan a cada app entre 192 y 256 MiB.
         */
        const val MAX_MEMORY_KIB = 131_072
        const val MAX_ITERATIONS = 10
        const val MAX_PARALLELISM = 4

        /** Memoria × pasadas: el doble de 64 MiB con t = 3, para acotar el tiempo de espera. */
        const val MAX_COST = 2L * 65_536 * 3
    }
}
