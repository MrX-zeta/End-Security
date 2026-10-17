package com.luis.end_security.core.crypto

/**
 * Parámetros de Argon2id (DEC-05). Llegan del servidor en el prelogin, así que se validan:
 * - mínimos de OWASP (Password Storage Cheat Sheet): la memoria y las pasadas deben alcanzar al
 *   menos una de sus configuraciones (46 MiB/t=1, 19 MiB/t=2, 12 MiB/t=3, 9 MiB/t=4, 7 MiB/t=5);
 * - topes, para que un servidor malicioso no pueda pedir tanta memoria o tiempo que la app se
 *   cierre o se congele.
 *
 * Los valores concretos salen de la medición de PEND-01.
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
        require(OWASP_MINIMUMS.any { (memory, passes) -> memoryKiB >= memory && iterations >= passes }) {
            "Parámetros por debajo de los mínimos de OWASP: $memoryKiB KiB, t=$iterations"
        }
    }

    companion object {
        /** (memoria en KiB, pasadas), con p = 1. */
        private val OWASP_MINIMUMS = listOf(
            47_104 to 1,
            19_456 to 2,
            12_288 to 3,
            9_216 to 4,
            7_168 to 5,
        )

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
