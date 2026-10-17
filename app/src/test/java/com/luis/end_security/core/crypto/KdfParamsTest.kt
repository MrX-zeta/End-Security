package com.luis.end_security.core.crypto

import org.junit.Assert.assertThrows
import org.junit.Test

class KdfParamsTest {

    @Test
    fun `las configuraciones de OWASP y las tres a medir se aceptan`() {
        KdfParams(memoryKiB = 47_104, iterations = 1, parallelism = 1)
        KdfParams(memoryKiB = 19_456, iterations = 2, parallelism = 1)
        KdfParams(memoryKiB = 12_288, iterations = 3, parallelism = 1)
        KdfParams(memoryKiB = 9_216, iterations = 4, parallelism = 1)
        KdfParams(memoryKiB = 7_168, iterations = 5, parallelism = 1)
        KdfParams(memoryKiB = 65_536, iterations = 3, parallelism = 1)
    }

    @Test
    fun `por debajo de los minimos se rechaza`() {
        assertThrows(IllegalArgumentException::class.java) { KdfParams(19_455, 2, 1) }
        assertThrows(IllegalArgumentException::class.java) { KdfParams(46_080, 1, 1) }
        assertThrows(IllegalArgumentException::class.java) { KdfParams(7_167, 5, 1) }
        assertThrows(IllegalArgumentException::class.java) { KdfParams(32, 3, 4) }
    }

    @Test
    fun `fuera de los topes se rechaza`() {
        assertThrows(IllegalArgumentException::class.java) { KdfParams(KdfParams.MAX_MEMORY_KIB + 1, 2, 1) }
        assertThrows(IllegalArgumentException::class.java) { KdfParams(19_456, KdfParams.MAX_ITERATIONS + 1, 1) }
        assertThrows(IllegalArgumentException::class.java) { KdfParams(19_456, 2, 0) }
        assertThrows(IllegalArgumentException::class.java) { KdfParams(19_456, 2, KdfParams.MAX_PARALLELISM + 1) }
    }

    @Test
    fun `memoria por pasadas tiene tope`() {
        KdfParams(memoryKiB = 131_072, iterations = 3, parallelism = 1)
        assertThrows(IllegalArgumentException::class.java) { KdfParams(131_072, 4, 1) }
        assertThrows(IllegalArgumentException::class.java) { KdfParams(65_536, KdfParams.MAX_ITERATIONS, 1) }
    }
}
