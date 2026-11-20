package com.luis.end_security.core.security

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** `SIN_FLAG_SECURE` (capturas de evidencias) nunca puede estar activo en release: el bloque `release` lo fija en `false`. */
class SinFlagSecureTest {

    private val gradle = listOf("build.gradle.kts", "app/build.gradle.kts").map { File(it) }.first { it.exists() }.readText(Charsets.UTF_8)

    @Test
    fun `release fija SIN_FLAG_SECURE en false`() {
        val release = gradle.substringAfter("        release {").substringBefore("        // Solo para medir Argon2")
        assertTrue(release.contains("""buildConfigField("boolean", "SIN_FLAG_SECURE", "false")"""))
    }

    @Test
    fun `solo debug lee la propiedad sinFlagSecure`() {
        val debug = gradle.substringAfter("        debug {").substringBefore("        release {")
        assertTrue(debug.contains("""findProperty("sinFlagSecure")"""))
    }
}
