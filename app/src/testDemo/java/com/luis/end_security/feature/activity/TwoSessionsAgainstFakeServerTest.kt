package com.luis.end_security.feature.activity

import com.luis.end_security.core.common.ApiError
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
import com.luis.end_security.core.network.api.AuthApiImpl
import com.luis.end_security.core.network.api.SessionApiImpl
import com.luis.end_security.core.network.fake.FakeServer
import com.luis.end_security.core.network.fake.FakeState
import com.luis.end_security.feature.account.domain.Authenticated
import com.luis.end_security.feature.account.domain.ServerKdf
import com.luis.end_security.feature.activity.domain.ActivityKind
import com.luis.end_security.feature.activity.domain.RevokeSessions
import com.luis.end_security.feature.auth.domain.LoginResult
import com.luis.end_security.feature.auth.domain.RegisterRequest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * Dos sesiones de la misma cuenta contra el backend falso (que cumple EP-23…EP-25): la actual ve las dos, cierra la otra, y la
 * actividad registra cada evento con el nombre de su dispositivo. Es la prueba de «dos emuladores» sin abrir dos emuladores.
 */
class TwoSessionsAgainstFakeServerTest {

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
        var revoked = false
        override suspend fun accessToken() = access
        override suspend fun refreshAfterExpiry(expiredAccessToken: String): RefreshOutcome = RefreshOutcome.Revoked
        override suspend fun onSessionRevoked() {
            revoked = true
        }
    }

    private val tokensA = Tokens()
    private val tokensB = Tokens()
    private val clientA = ApiClient(transport, tokensA)
    private val clientB = ApiClient(transport, tokensB)
    private val authA = AuthApiImpl(clientA)
    private val authB = AuthApiImpl(clientB)
    private val sessionsA = SessionApiImpl(clientA)
    private val sessionsB = SessionApiImpl(clientB)

    private fun key() = SecureBytes.copyOf(ByteArray(32) { 7 })

    /** Sesión A («Pixel 7 Pro») por el registro y sesión B («Galaxy A54») por un acceso. */
    private fun openTwoSessions(): Pair<Authenticated, Authenticated> = runBlocking {
        authA.register(
            RegisterRequest(
                userId, email, null, key(), ServerKdf(65_536, 3, 1, ByteArray(16) { 3 }),
                Sealed(ByteArray(12) { 1 }, ByteArray(48) { 2 }),
            ),
        )
        val a = (authA.verifyEmail(email, codes.getValue(email), "Pixel 7 Pro") as ApiResult.Success).value
        tokensA.access = a.tokens.accessToken
        clock += 60_000
        val login = (authB.login(email, key(), "Galaxy A54") as ApiResult.Success).value as LoginResult.Success
        tokensB.access = login.authenticated.tokens.accessToken
        a to login.authenticated
    }

    @Test
    fun `la sesion actual ve las dos con su nombre y la propia primero`() = runBlocking {
        val (a, b) = openTwoSessions()
        val list = (sessionsA.listSessions() as ApiResult.Success).value
        assertEquals(listOf("Pixel 7 Pro", "Galaxy A54"), list.map { it.deviceName })
        assertTrue(list[0].current)
        assertFalse(list[1].current)
        assertEquals(a.tokens.sessionId, list[0].id)
        assertEquals(b.tokens.sessionId, list[1].id)
        // Desde la otra sesión, la «actual» es la suya.
        val fromB = (sessionsB.listSessions() as ApiResult.Success).value
        assertEquals(listOf("Galaxy A54", "Pixel 7 Pro"), fromB.map { it.deviceName })
    }

    @Test
    fun `cerrar la otra la quita de la lista y su sesion deja de valer`() = runBlocking {
        val (_, b) = openTwoSessions()
        val outcome = RevokeSessions(sessionsA)(listOf(b.tokens.sessionId))
        assertTrue(outcome.allClosed)
        assertEquals(1, (sessionsA.listSessions() as ApiResult.Success).value.size)
        // La sesión cerrada ya no puede pedir nada: el servidor dice `session_revoked`.
        val failure = sessionsB.listSessions() as ApiResult.Failure
        assertEquals(ApiError.SessionRevoked, failure.error)
        // Cerrar de nuevo la misma no es un error para la persona: el resultado deseado ya está.
        assertTrue(RevokeSessions(sessionsA)(listOf(b.tokens.sessionId)).allClosed)
    }

    @Test
    fun `la actividad registra cada evento con el dispositivo que lo hizo, del mas reciente al mas antiguo`() = runBlocking {
        val (_, b) = openTwoSessions()
        RevokeSessions(sessionsA)(listOf(b.tokens.sessionId))
        val page = (sessionsA.listActivity() as ApiResult.Success).value
        val byKind = page.events.map { it.kind to it.deviceName }
        assertEquals(ActivityKind.SessionRevoked to "Pixel 7 Pro", byKind[0])
        assertEquals(ActivityKind.Login to "Galaxy A54", byKind[1])
        assertEquals(ActivityKind.EmailVerified to "Pixel 7 Pro", byKind[2])
        assertTrue(page.events.zipWithNext().all { (newer, older) -> newer.id > older.id })
        assertTrue(page.events.all { it.success })
        assertFalse(page.hasMore)
    }

    @Test
    fun `un acceso fallido queda como fallido con su dispositivo`() = runBlocking {
        openTwoSessions()
        val wrong = SecureBytes.copyOf(ByteArray(32) { 9 })
        val result = authB.login(email, wrong, "Dispositivo ajeno")
        assertTrue(result is ApiResult.Failure)
        val failed = (sessionsA.listActivity() as ApiResult.Success).value.events.first()
        assertEquals(ActivityKind.Login, failed.kind)
        assertFalse(failed.success)
        assertEquals("Dispositivo ajeno", failed.deviceName)
    }

    @Test
    fun `la paginacion con before no repite ni salta eventos`() = runBlocking {
        val (_, b) = openTwoSessions()
        RevokeSessions(sessionsA)(listOf(b.tokens.sessionId))
        val all = (sessionsA.listActivity() as ApiResult.Success).value.events
        val first = (sessionsA.listActivity(limit = 2) as ApiResult.Success).value
        assertEquals(2, first.events.size)
        assertTrue(first.hasMore)
        val second = (sessionsA.listActivity(before = first.events.last().id, limit = 10) as ApiResult.Success).value
        assertEquals(all.map { it.id }, (first.events + second.events).map { it.id })
    }
}
