package com.luis.end_security.core.network.fake

import com.luis.end_security.core.common.ApiError
import com.luis.end_security.core.common.ApiResult
import com.luis.end_security.core.crypto.Base32
import com.luis.end_security.core.crypto.RandomBytes
import com.luis.end_security.core.crypto.SecureBytes
import com.luis.end_security.core.crypto.Sealed
import com.luis.end_security.core.crypto.Totp
import com.luis.end_security.core.network.ApiClient
import com.luis.end_security.core.network.ApiTransport
import com.luis.end_security.core.network.Base64Url
import com.luis.end_security.core.network.HttpRequest
import com.luis.end_security.core.network.HttpResponse
import com.luis.end_security.core.network.RefreshOutcome
import com.luis.end_security.core.network.TokenProvider
import com.luis.end_security.core.network.api.AuthApiImpl
import com.luis.end_security.core.network.api.VaultApiImpl
import com.luis.end_security.feature.account.domain.Authenticated
import com.luis.end_security.feature.account.domain.ServerKdf
import com.luis.end_security.feature.auth.domain.LoginResult
import com.luis.end_security.feature.auth.domain.RegisterRequest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * El backend falso a través de la capa de API real (DTO, JSON y errores): cada `code` del
 * contrato que usan el registro, el acceso y la bóveda.
 */
class FakeBackendTest {

    private var clock = 1_800_000_000_000L
    private val codes = mutableMapOf<String, String>()
    private val state = FakeState(Base64Url.encode(ByteArray(32) { 1 }), Base64Url.encode(ByteArray(32) { 2 }))
    private val server = FakeServer(state, { clock }) { email, code -> codes[email] = code }
    private val transport = object : ApiTransport {
        override suspend fun execute(request: HttpRequest): HttpResponse = server.handle(request)
    }

    /** Tokens en memoria con renovación real contra el backend falso. */
    private inner class MemoryTokens : TokenProvider {
        var access: String? = null
        var refresh: String? = null
        var revoked = false
        override suspend fun accessToken() = access
        override suspend fun refreshAfterExpiry(expiredAccessToken: String): RefreshOutcome {
            val result = auth.refresh(refresh ?: return RefreshOutcome.Revoked)
            val renewed = (result as? ApiResult.Success)?.value ?: return RefreshOutcome.Revoked
            access = renewed.accessToken
            refresh = renewed.refreshToken
            return RefreshOutcome.Renewed(renewed.accessToken)
        }
        override suspend fun onSessionRevoked() {
            revoked = true
            access = null
        }
    }

    private val tokens = MemoryTokens()
    private val client = ApiClient(transport, tokens)
    private val auth = AuthApiImpl(client)
    private val vault = VaultApiImpl(client)

    private val authKey = SecureBytes.copyOf(ByteArray(32) { 7 })
    private val kdf = ServerKdf(65_536, 3, 1, ByteArray(16) { 3 })
    private val pvk = Sealed(ByteArray(12) { 4 }, ByteArray(48) { 5 })

    private fun register(email: String, userId: UUID = UUID.randomUUID()) = runBlocking {
        auth.register(RegisterRequest(userId, email, null, authKey, kdf, pvk))
    }

    private fun registerAndVerify(email: String): Authenticated = runBlocking {
        register(email)
        val result = auth.verifyEmail(email, codes.getValue(email), "Pixel 7 Pro")
        (result as ApiResult.Success).value.also {
            tokens.access = it.tokens.accessToken
            tokens.refresh = it.tokens.refreshToken
        }
    }

    private fun <T> ApiResult<T>.error() = (this as ApiResult.Failure).error

    @Test
    fun `prelogin responde siempre, con sal falsa determinista para un correo inexistente`() = runBlocking {
        val first = (auth.prelogin("nadie@correo.com") as ApiResult.Success).value
        val second = (auth.prelogin("  NADIE@correo.com ") as ApiResult.Success).value
        assertEquals(65_536, first.memoryKiB)
        assertEquals(3, first.iterations)
        assertEquals(16, first.salt.size)
        assertArrayEquals(first.salt, second.salt)
        registerAndVerify("ana@correo.com")
        assertArrayEquals(kdf.salt, (auth.prelogin("ana@correo.com") as ApiResult.Success).value.salt)
    }

    @Test
    fun `registro responde 202 aunque el correo exista, y no lo sobrescribe`() = runBlocking {
        assertTrue(register("ana@correo.com") is ApiResult.Success)
        val firstCode = codes.getValue("ana@correo.com")
        codes.clear()
        assertTrue(register("ana@correo.com") is ApiResult.Success)
        assertTrue("no se manda otro código", codes.isEmpty())
        assertEquals(1, state.users.size)
        assertTrue(auth.verifyEmail("ana@correo.com", firstCode, "Pixel") is ApiResult.Success)
    }

    @Test
    fun `registro con parametros por debajo del piso es validation_failed`() = runBlocking {
        val weak = ServerKdf(19_456, 2, 1, ByteArray(16))
        val result = auth.register(RegisterRequest(UUID.randomUUID(), "ana@correo.com", null, authKey, weak, pvk))
        assertEquals(ApiError.ValidationFailed, result.error())
    }

    @Test
    fun `verificar correo cuenta los intentos y al quinto el codigo ya no sirve`() = runBlocking {
        register("ana@correo.com")
        val wrong = if (codes.getValue("ana@correo.com") == "000000") "111111" else "000000"
        assertEquals(ApiError.InvalidCode(4), auth.verifyEmail("ana@correo.com", wrong, "Pixel").error())
        repeat(3) { auth.verifyEmail("ana@correo.com", wrong, "Pixel") }
        assertEquals(ApiError.InvalidCode(0), auth.verifyEmail("ana@correo.com", wrong, "Pixel").error())
        assertEquals(ApiError.ChallengeExpired, auth.verifyEmail("ana@correo.com", codes.getValue("ana@correo.com"), "Pixel").error())
    }

    @Test
    fun `el codigo de correo vence a los 15 min`() = runBlocking {
        register("ana@correo.com")
        clock += 15 * 60_000L
        assertEquals(ApiError.ChallengeExpired, auth.verifyEmail("ana@correo.com", codes.getValue("ana@correo.com"), "Pixel").error())
    }

    @Test
    fun `reenviar respeta 60 s entre envios`() = runBlocking {
        register("ana@correo.com")
        assertTrue(auth.resendVerification("ana@correo.com").error() is ApiError.RateLimited)
        clock += 60_000L
        assertTrue(auth.resendVerification("ana@correo.com") is ApiResult.Success)
        assertTrue("correo inexistente: 202 igual", auth.resendVerification("nadie@correo.com") is ApiResult.Success)
    }

    @Test
    fun `login con correo inexistente y con authKey incorrecta da el mismo error`() = runBlocking {
        registerAndVerify("ana@correo.com")
        val wrongKey = SecureBytes.copyOf(ByteArray(32) { 9 })
        assertEquals(ApiError.InvalidCredentials, auth.login("nadie@correo.com", authKey, "Pixel").error())
        assertEquals(ApiError.InvalidCredentials, auth.login("ana@correo.com", wrongKey, "Pixel").error())
        assertTrue(auth.login("ana@correo.com", authKey, "Pixel") is ApiResult.Success)
    }

    @Test
    fun `cinco fallos de login bloquean 15 min`() = runBlocking {
        registerAndVerify("ana@correo.com")
        val wrongKey = SecureBytes.copyOf(ByteArray(32) { 9 })
        repeat(5) { assertEquals(ApiError.InvalidCredentials, auth.login("ana@correo.com", wrongKey, "Pixel").error()) }
        assertEquals(ApiError.RateLimited(900), auth.login("ana@correo.com", authKey, "Pixel").error())
        clock += 15 * 60_000L
        assertTrue(auth.login("ana@correo.com", authKey, "Pixel") is ApiResult.Success)
    }

    @Test
    fun `login sin verificar el correo es email_not_verified`() = runBlocking {
        register("ana@correo.com")
        assertEquals(ApiError.EmailNotVerified, auth.login("ana@correo.com", authKey, "Pixel").error())
    }

    @Test
    fun `2FA con TOTP, anti-replay y reto de un solo uso`() = runBlocking {
        registerAndVerify("ana+2fa@correo.com")
        val challenge = (auth.login("ana+2fa@correo.com", authKey, "Pixel") as ApiResult.Success).value as LoginResult.TwoFactorRequired
        val secret = Base32.decode(FakeBackendDebug.TOTP_SECRET)
        val code = Totp.code(secret, Totp.step(clock / 1000))
        assertTrue(auth.verifyTwoFactor(challenge.challengeToken, code) is ApiResult.Success)
        assertEquals("reto ya usado", ApiError.ChallengeExpired, auth.verifyTwoFactor(challenge.challengeToken, code).error())
        val again = (auth.login("ana+2fa@correo.com", authKey, "Pixel") as ApiResult.Success).value as LoginResult.TwoFactorRequired
        assertEquals("mismo paso: anti-replay", ApiError.InvalidCode(null), auth.verifyTwoFactor(again.challengeToken, code).error())
    }

    @Test
    fun `el reto del 2FA vence a los 5 min`() = runBlocking {
        registerAndVerify("ana+2fa@correo.com")
        val challenge = (auth.login("ana+2fa@correo.com", authKey, "Pixel") as ApiResult.Success).value as LoginResult.TwoFactorRequired
        clock += 5 * 60_000L
        val code = Totp.code(Base32.decode(FakeBackendDebug.TOTP_SECRET), Totp.step(clock / 1000))
        assertEquals(ApiError.ChallengeExpired, auth.verifyTwoFactor(challenge.challengeToken, code).error())
    }

    @Test
    fun `codigo de respaldo de un solo uso y cuantos quedan`() = runBlocking {
        registerAndVerify("ana+2fa@correo.com")
        val first = (auth.login("ana+2fa@correo.com", authKey, "Pixel") as ApiResult.Success).value as LoginResult.TwoFactorRequired
        val recovery = (auth.verifyRecoveryCode(first.challengeToken, FakeBackendDebug.RECOVERY_CODES[0]) as ApiResult.Success).value
        assertEquals(9, recovery.recoveryCodesRemaining)
        val second = (auth.login("ana+2fa@correo.com", authKey, "Pixel") as ApiResult.Success).value as LoginResult.TwoFactorRequired
        assertEquals(ApiError.InvalidCode(null), auth.verifyRecoveryCode(second.challengeToken, FakeBackendDebug.RECOVERY_CODES[0]).error())
    }

    @Test
    fun `token vencido se renueva solo y reusar el refresh revoca la sesion`() = runBlocking {
        registerAndVerify("ana@correo.com")
        val oldRefresh = tokens.refresh!!
        clock += 16 * 60_000L
        assertTrue("renovó y reintentó", vault.listChanges(0) is ApiResult.Success)
        assertTrue(oldRefresh != tokens.refresh)
        assertEquals(ApiError.SessionRevoked, auth.refresh(oldRefresh).error())
        assertEquals(ApiError.SessionRevoked, vault.listChanges(0).error())
        assertTrue(tokens.revoked)
    }

    @Test
    fun `boveda con version esperada, conflicto, lapida y paginas`() = runBlocking {
        registerAndVerify("ana@correo.com")
        val id = UUID.randomUUID()
        val sealed = Sealed(RandomBytes.next(12), RandomBytes.next(40))
        val created = (vault.putItem(id, null, 1, sealed) as ApiResult.Success).value
        assertEquals(1L, created.version)
        assertTrue(vault.putItem(id, null, 1, sealed).error() is ApiError.VersionConflict)
        assertEquals(ApiError.VersionConflict(1, created.revision), vault.putItem(id, 7, 1, sealed).error())
        assertEquals(ApiError.NotFound, vault.putItem(UUID.randomUUID(), 1, 1, sealed).error())
        val updated = (vault.putItem(id, 1, 1, sealed) as ApiResult.Success).value
        val deleted = (vault.deleteItem(id, updated.version) as ApiResult.Success).value
        assertEquals(3L, deleted.version)
        repeat(3) { vault.putItem(UUID.randomUUID(), null, 1, sealed) }
        val page = (vault.listChanges(0, limit = 2) as ApiResult.Success).value
        assertEquals(2, page.items.size)
        assertTrue(page.hasMore)
        assertTrue("la lápida viene sin bloque", page.items.first().deleted && page.items.first().sealed == null)
    }

    @Test
    fun `cursor_expired si since es menor que lo purgado`() = runBlocking {
        val authenticated = registerAndVerify("ana@correo.com")
        state.users.getValue(authenticated.account.userId.toString()).purgedThroughRevision = 10
        assertEquals(ApiError.CursorExpired, vault.listChanges(5).error())
        assertTrue(vault.listChanges(0) is ApiResult.Success)
    }

    @Test
    fun `el estado no guarda la authKey, tokens ni codigos en claro`() = runBlocking {
        registerAndVerify("ana@correo.com")
        val json = com.luis.end_security.core.network.ApiJson.encodeToString(FakeState.serializer(), state)
        assertTrue(!json.contains(authKey.withBytes(Base64Url::encode)))
        assertTrue(!json.contains(tokens.access!!))
        assertTrue(!json.contains(tokens.refresh!!))
    }
}
