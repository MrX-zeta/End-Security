package com.luis.end_security.feature.health

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.luis.end_security.feature.health.data.HibpClient
import com.luis.end_security.feature.health.domain.BreachLookup
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * HIBP de verdad, desde el emulador con red (permiso `INTERNET` y `network_security_config.xml` reales): «password123»
 * sale filtrada y una contraseña aleatoria no. Sin red la prueba se salta.
 */
@RunWith(AndroidJUnit4::class)
class HibpClientDeviceTest {

    private val client = HibpClient()

    @Test
    fun password123_esta_filtrada_y_una_aleatoria_no() = runBlocking {
        val known = client.lookup("password123")
        assumeFalse("sin red en el emulador", known == BreachLookup.Offline)

        assertTrue("password123 debe aparecer en filtraciones: $known", known is BreachLookup.Found && known.count > 1_000)
        val random = client.lookup(UUID.randomUUID().toString() + UUID.randomUUID().toString())
        assertTrue("una contraseña aleatoria no debe aparecer: $random", random == BreachLookup.Clean)
    }
}
