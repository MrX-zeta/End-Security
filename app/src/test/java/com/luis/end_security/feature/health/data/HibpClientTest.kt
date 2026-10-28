package com.luis.end_security.feature.health.data

import com.luis.end_security.core.crypto.Hashes
import com.luis.end_security.feature.health.domain.BreachLookup
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList

/** El cliente de HIBP contra un servidor falso local: lo que sale a la red es solo el prefijo de 5 caracteres. */
class HibpClientTest {

    private class Seen(val path: String, val headers: Map<String, String>)

    private lateinit var server: HttpServer
    private val seen = CopyOnWriteArrayList<Seen>()
    private var status = 200
    private var body = ""
    private var delayMillis = 0L

    private val baseUrl get() = "http://127.0.0.1:${server.address.port}"

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            seen += Seen(
                exchange.requestURI.toString(),
                exchange.requestHeaders.entries.associate { it.key.lowercase() to it.value.joinToString() },
            )
            if (delayMillis > 0) Thread.sleep(delayMillis)
            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
            if (bytes.isNotEmpty()) exchange.responseBody.use { it.write(bytes) }
            exchange.close()
        }
        server.start()
    }

    @After
    fun tearDown() = server.stop(0)

    private fun client(timeoutMillis: Int = 2_000) = HibpClient(baseUrl, timeoutMillis)

    private fun sha1(text: String) = Hashes.sha1(text.toByteArray()).joinToString("") { "%02X".format(it) }

    @Test
    fun `vector conocido password tiene SHA-1 5BAA6 y el prefijo es 5BAA6`() {
        val digest = sha1("password")
        assertEquals("5BAA61E4C9B93F3F0682250B6CF8331B7EE68FD8", digest)
        runBlocking { client().lookup("password") }
        assertEquals("/range/5BAA6", seen.single().path)
    }

    @Test
    fun `manda los encabezados Add-Padding y el User-Agent propio`() {
        runBlocking { client().lookup("password") }
        val headers = seen.single().headers
        assertEquals("true", headers["add-padding"])
        assertEquals("EndSecurity-Android", headers["user-agent"])
    }

    @Test
    fun `nunca manda el sufijo ni la contrasena`() {
        val password = "Tortuga-Violeta-29"
        runBlocking { client().lookup(password) }
        val request = seen.single()
        val digest = sha1(password)
        assertEquals("/range/${digest.substring(0, 5)}", request.path)
        val everything = request.path + request.headers.toString()
        assertFalse(everything.contains(digest.substring(5), ignoreCase = true))
        assertFalse(everything.contains(password))
    }

    @Test
    fun `encuentra el sufijo y devuelve su cuenta`() {
        val suffix = sha1("password").substring(5)
        body = "0018A45C4D1DEF81644B54AB7F969B88D65:3\r\n$suffix:10434004\r\nFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF:2\r\n"
        val result = runBlocking { client().lookup("password") }
        assertEquals(10_434_004, (result as BreachLookup.Found).count)
    }

    @Test
    fun `el sufijo en minusculas tambien coincide`() {
        val suffix = sha1("password").substring(5).lowercase()
        body = "$suffix:7\n"
        assertEquals(7, (runBlocking { client().lookup("password") } as BreachLookup.Found).count)
    }

    @Test
    fun `las lineas con cuenta 0 son relleno y se ignoran`() {
        val suffix = sha1("password").substring(5)
        body = "$suffix:0\r\n0018A45C4D1DEF81644B54AB7F969B88D65:0\r\n"
        assertEquals(BreachLookup.Clean, runBlocking { client().lookup("password") })
    }

    @Test
    fun `si el sufijo no esta no esta filtrada`() {
        body = "0018A45C4D1DEF81644B54AB7F969B88D65:3\r\n"
        assertEquals(BreachLookup.Clean, runBlocking { client().lookup("una-contrasena-unica") })
    }

    @Test
    fun `un error del servicio es no disponible y no filtrada`() {
        status = 503
        assertEquals(BreachLookup.Unavailable, runBlocking { client().lookup("password") })
        status = 429
        assertEquals(BreachLookup.Unavailable, runBlocking { client().lookup("password") })
    }

    @Test
    fun `una respuesta lenta agota el tiempo de espera y cuenta como sin red`() {
        delayMillis = 1_500
        val started = System.currentTimeMillis()
        val result = runBlocking { client(timeoutMillis = 300).lookup("password") }
        assertEquals(BreachLookup.Offline, result)
        assertTrue("no espera a que responda", System.currentTimeMillis() - started < 1_400)
    }

    @Test
    fun `sin servidor es sin red`() {
        val closed = HibpClient("http://127.0.0.1:1", 500)
        assertEquals(BreachLookup.Offline, runBlocking { closed.lookup("password") })
    }

    @Test
    fun `lo encontrado no imprime la cuenta`() {
        assertFalse(BreachLookup.Found(12345).toString().contains("12345"))
    }

    @Test
    fun `una respuesta vacia o sin formato no cuenta como limpia`() {
        body = ""
        assertEquals(BreachLookup.Unavailable, runBlocking { client().lookup("password") })
        body = "<html>Bad gateway</html>"
        assertEquals(BreachLookup.Unavailable, runBlocking { client().lookup("password") })
    }

    @Test
    fun `una cuenta negativa es sospechosa y no es limpia`() {
        body = "0018A45C4D1DEF81644B54AB7F969B88D65:-5\r\n"
        assertEquals(BreachLookup.Unavailable, runBlocking { client().lookup("password") })
    }

    @Test
    fun `un cuerpo enorme se descarta sin agotar la memoria`() {
        body = "0018A45C4D1DEF81644B54AB7F969B88D65:3\r\n".repeat(80_000) // ~3 MB
        assertEquals(BreachLookup.Unavailable, runBlocking { client().lookup("password") })
    }
}
