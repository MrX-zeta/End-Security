package com.luis.end_security.core.network.fake

import com.luis.end_security.core.common.ApiResult
import com.luis.end_security.core.crypto.Sealed
import com.luis.end_security.core.crypto.SecureBytes
import com.luis.end_security.core.network.ApiClient
import com.luis.end_security.core.network.ApiTransport
import com.luis.end_security.core.network.Base64Url
import com.luis.end_security.core.network.HttpRequest
import com.luis.end_security.core.network.HttpResponse
import com.luis.end_security.core.network.RefreshOutcome
import com.luis.end_security.core.network.TokenProvider
import com.luis.end_security.core.network.api.AccountApiImpl
import com.luis.end_security.core.network.api.AuthApiImpl
import com.luis.end_security.feature.account.domain.ServerKdf
import com.luis.end_security.feature.auth.domain.RegisterRequest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * Contrato 1.4.0 (DEC-64) contra el backend falso: `marketingOptIn` en el registro, en la cuenta y en `PATCH /account`, que sigue
 * aceptando el nombre visible por separado.
 */
class MarketingOptInAgainstFakeServerTest {

    private val email = "ana@correo.com"
    private val userId = UUID.fromString("0b9f3c2e-5d1a-4f7e-9a2b-6c8d1e4f7a90")
    private var clock = 1_800_000_000_000L
    private val codes = mutableMapOf<String, String>()
    private val state = FakeState(Base64Url.encode(ByteArray(32) { 1 }), Base64Url.encode(ByteArray(32) { 2 }))
    private val server = FakeServer(state, { clock }) { e, c -> codes[e] = c }
    private val transport = object : ApiTransport {
        override suspend fun execute(request: HttpRequest): HttpResponse = server.handle(request)
    }

    private class Tokens : TokenProvider {
        var access: String? = null
        override suspend fun accessToken() = access
        override suspend fun refreshAfterExpiry(expiredAccessToken: String): RefreshOutcome = RefreshOutcome.Revoked
        override suspend fun onSessionRevoked() = Unit
    }

    private val tokens = Tokens()
    private val client = ApiClient(transport, tokens)
    private val auth = AuthApiImpl(client)
    private val account = AccountApiImpl(client)

    /** Registra la cuenta y deja la sesión abierta; devuelve el estado del `marketingOptIn` que ve la cuenta. */
    private fun registerAndVerify(marketingOptIn: Boolean?) = runBlocking {
        val request = RegisterRequest(
            userId, email, null, SecureBytes.copyOf(ByteArray(32) { 7 }), ServerKdf(65_536, 3, 1, ByteArray(16) { 3 }),
            Sealed(ByteArray(12) { 1 }, ByteArray(48) { 2 }),
            marketingOptIn = marketingOptIn ?: false,
        )
        auth.register(request)
        val authenticated = (auth.verifyEmail(email, codes.getValue(email), "Pixel 7 Pro") as ApiResult.Success).value
        tokens.access = authenticated.tokens.accessToken
        authenticated.account.marketingOptIn
    }

    private fun raw(body: String): HttpResponse = runBlocking { server.handle(HttpRequest("PATCH", "/account", body, tokens.access)) }

    @Test
    fun `sin marcar la casilla el registro deja marketingOptIn en falso`() {
        assertFalse(registerAndVerify(marketingOptIn = null))
        assertFalse((runBlocking { account.getAccount() } as ApiResult.Success).value.marketingOptIn)
    }

    @Test
    fun `con la casilla marcada el registro guarda marketingOptIn en verdadero`() {
        assertTrue(registerAndVerify(marketingOptIn = true))
        assertTrue((runBlocking { account.getAccount() } as ApiResult.Success).value.marketingOptIn)
    }

    @Test
    fun `el PATCH activa y retira las finalidades secundarias y la cuenta lo conserva`() = runBlocking {
        registerAndVerify(marketingOptIn = null)
        assertTrue((account.updateMarketingOptIn(true) as ApiResult.Success).value.marketingOptIn)
        assertTrue((account.getAccount() as ApiResult.Success).value.marketingOptIn)
        assertFalse((account.updateMarketingOptIn(false) as ApiResult.Success).value.marketingOptIn)
        assertFalse((account.getAccount() as ApiResult.Success).value.marketingOptIn)
    }

    @Test
    fun `cambiar el nombre visible no toca marketingOptIn y al reves`() = runBlocking {
        registerAndVerify(marketingOptIn = true)
        val named = (account.updateDisplayName("Ana") as ApiResult.Success).value
        assertEquals("Ana", named.displayName)
        assertTrue(named.marketingOptIn)
        val off = (account.updateMarketingOptIn(false) as ApiResult.Success).value
        assertEquals("Ana", off.displayName)
        assertFalse(off.marketingOptIn)
        // `displayName: null` explícito sí borra el nombre y deja lo demás como está.
        val cleared = (account.updateDisplayName(null) as ApiResult.Success).value
        assertNull(cleared.displayName)
        assertFalse(cleared.marketingOptIn)
    }

    @Test
    fun `el PATCH acepta los dos campos juntos`() {
        registerAndVerify(marketingOptIn = null)
        val response = raw("""{"displayName":"Ana","marketingOptIn":true}""")
        assertEquals(200, response.status)
        val account = (runBlocking { account.getAccount() } as ApiResult.Success).value
        assertEquals("Ana", account.displayName)
        assertTrue(account.marketingOptIn)
    }

    @Test
    fun `un PATCH sin campos o con campos desconocidos es una solicitud invalida`() {
        registerAndVerify(marketingOptIn = null)
        assertEquals(400, raw("{}").status)
        assertEquals(400, raw("""{"otro":1}""").status)
        // Nada cambió.
        assertFalse((runBlocking { account.getAccount() } as ApiResult.Success).value.marketingOptIn)
    }
}
