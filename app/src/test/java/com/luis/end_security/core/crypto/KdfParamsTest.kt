package com.luis.end_security.core.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class KdfParamsTest {

    @Test
    fun `el piso de PEND-01 y lo que esta arriba se aceptan`() {
        KdfParams(memoryKiB = 65_536, iterations = 3, parallelism = 1)
        KdfParams(memoryKiB = 131_072, iterations = 3, parallelism = 1)
        KdfParams(memoryKiB = 65_536, iterations = 6, parallelism = 2)
        assertEquals(KdfParams(65_536, 3, 1), KdfParams.DEFAULT)
    }

    @Test
    fun `por debajo del piso se rechaza, aunque OWASP lo acepte`() {
        assertThrows(IllegalArgumentException::class.java) { KdfParams(47_104, 1, 1) }
        assertThrows(IllegalArgumentException::class.java) { KdfParams(19_456, 2, 1) }
        assertThrows(IllegalArgumentException::class.java) { KdfParams(65_535, 3, 1) }
        assertThrows(IllegalArgumentException::class.java) { KdfParams(65_536, 2, 1) }
        assertThrows(IllegalArgumentException::class.java) { KdfParams(32, 3, 4) }
    }

    @Test
    fun `fuera de los topes se rechaza`() {
        assertThrows(IllegalArgumentException::class.java) { KdfParams(KdfParams.MAX_MEMORY_KIB + 1, 3, 1) }
        assertThrows(IllegalArgumentException::class.java) { KdfParams(65_536, KdfParams.MAX_ITERATIONS + 1, 1) }
        assertThrows(IllegalArgumentException::class.java) { KdfParams(65_536, 3, 0) }
        assertThrows(IllegalArgumentException::class.java) { KdfParams(65_536, 3, KdfParams.MAX_PARALLELISM + 1) }
    }

    @Test
    fun `memoria por pasadas tiene tope`() {
        assertThrows(IllegalArgumentException::class.java) { KdfParams(131_072, 4, 1) }
        assertThrows(IllegalArgumentException::class.java) { KdfParams(65_536, KdfParams.MAX_ITERATIONS, 1) }
    }
}
