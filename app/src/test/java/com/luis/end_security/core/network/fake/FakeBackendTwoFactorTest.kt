package com.luis.end_security.core.network.fake

import com.luis.end_security.core.common.ApiError
import com.luis.end_security.core.common.ApiResult
import com.luis.end_security.core.crypto.Base32
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
import com.luis.end_security.core.network.api.AccountApiImpl
import com.luis.end_security.core.network.api.AuthApiImpl
import com.luis.end_security.feature.account.domain.Reauth
import com.luis.end_security.feature.account.domain.ServerKdf
import com.luis.end_security.feature.auth.domain.LoginResult
import com.luis.end_security.feature.auth.domain.RegisterRequest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * EP-19…EP-22 del backend falso a través de la capa de API real: clave pendiente reemplazable, confirmación con TOTP
 * real (RFC 6238) y anti-replay, `reauth_required`, desactivar borra los códigos y regenerar invalida los anteriores.
 */
class FakeBackendTwoFactorTest {

    private var clock = 1_800_000_000_000L
    private val codes = mutableMapOf<String, String>()
    private val state = FakeState(Base64Url.encode(ByteArray(32) { 1 }), Base64Url.encode(ByteArray(32) { 2 }))
    private val server = FakeServer(state, { clock }) { email, code -> codes[email] = code }
    private val transport = object : ApiTransport {
        override suspend fun execute(request: HttpRequest): HttpResponse = server.handle(request)
    }

    private inner class MemoryTokens : TokenProvider {
        var access: String? = null
        var refresh: String? = null
        override suspend fun accessToken() = access
        override suspend fun refreshAfterExpiry(expiredAccessToken: String): RefreshOutcome {
            val renewed = (auth.refresh(refresh ?: return RefreshOutcome.Revoked) as? ApiResult.Success)?.value
                ?: return RefreshOutcome.Revoked
            access = renewed.accessToken
            refresh = renewed.refreshToken
            return RefreshOutcome.Renewed(renewed.accessToken)
        }
        override suspend fun onSessionRevoked() {
            access = null
        }
    }

    private val tokens = MemoryTokens()
    private val client = ApiClient(transport, tokens)
    private val auth = AuthApiImpl(client)
    private val account = AccountApiImpl(client)

    private val email = "ana@correo.com"
    private val authKey = SecureBytes.copyOf(ByteArray(32) { 7 })
    private val wrongKey = SecureBytes.copyOf(ByteArray(32) { 9 })

    init {
        runBlocking {
            auth.register(RegisterRequest(UUID.randomUUID(), email, null, authKey, ServerKdf(65_536, 3, 1, ByteArray(16) { 3 }), Sealed(ByteArray(12) { 4 }, ByteArray(48) { 5 })))
            val verified = (auth.verifyEmail(email, codes.getValue(email), "Pixel 7 Pro") as ApiResult.Success).value
            tokens.access = verified.tokens.accessToken
            tokens.refresh = verified.tokens.refreshToken
        }
    }

    private fun <T> ApiResult<T>.value() = (this as ApiResult.Success).value
    private fun <T> ApiResult<T>.error() = (this as ApiResult.Failure).error

    private fun totp(secret: String) = Totp.code(Base32.decode(secret), Totp.step(clock / 1000))

    /** Activa el 2FA y devuelve la clave y los códigos de respaldo; deja el reloj en el paso siguiente. */
    private fun enable(): Pair<String, List<String>> = runBlocking {
        val setup = account.startTwoFactorSetup().value()
        val recovery = account.confirmTwoFactor(totp(setup.secret)).value()
        clock += 30_000
        setup.secret to recovery
    }

    private fun reauth(secret: String?, key: SecureBytes = authKey) = Reauth(key, secret?.let { totp(it) })

    private fun login() = runBlocking {
        (auth.login(email, authKey, "Pixel") as ApiResult.Success).value as LoginResult.TwoFactorRequired
    }

    @Test
    fun `EP-19 entrega la clave con los parametros del contrato`() = runBlocking {
        val setup = account.startTwoFactorSetup().value()
        assertEquals("End Security", setup.issuer)
        assertEquals(email, setup.accountName)
        assertEquals("SHA1", setup.algorithm)
        assertEquals(6, setup.digits)
        assertEquals(30, setup.periodSeconds)
        assertEquals(32, setup.secret.length)
        assertTrue(setup.secret.all { it in 'A'..'Z' || it in '2'..'7' })
    }

    @Test
    fun `la clave pendiente se reemplaza en cada EP-19 hasta confirmarla`() = runBlocking {
        val first = account.startTwoFactorSetup().value().secret
        val second = account.startTwoFactorSetup().value().secret
        assertNotEquals(first, second)
        // Con la clave anterior ya no se puede confirmar.
        assertTrue(account.confirmTwoFactor(totp(first)).error() is ApiError.InvalidCode)
        assertTrue(account.confirmTwoFactor(totp(second)) is ApiResult.Success)
    }

    @Test
    fun `confirmar con un codigo incorrecto es invalid_code y no activa`() = runBlocking {
        account.startTwoFactorSetup()
        assertTrue(account.confirmTwoFactor("000000").error() is ApiError.InvalidCode)
        assertFalse(account.getAccount().value().twoFactorEnabled)
    }

    @Test
    fun `confirmar con el TOTP real activa y entrega 10 codigos XXXX-XXXX distintos`() = runBlocking {
        val (_, recovery) = enable()
        assertEquals(10, recovery.size)
        assertEquals(10, recovery.toSet().size)
        assertTrue(recovery.all { Regex("[A-Z0-9]{4}-[A-Z0-9]{4}").matches(it) })
        assertTrue(account.getAccount().value().twoFactorEnabled)
    }

    @Test
    fun `con el 2FA activo EP-19 es two_factor_already_enabled`() = runBlocking {
        enable()
        assertEquals(ApiError.TwoFactorAlreadyEnabled, account.startTwoFactorSetup().error())
    }

    @Test
    fun `el mismo codigo dos veces falla por anti-replay`() = runBlocking {
        val setup = account.startTwoFactorSetup().value()
        val code = totp(setup.secret)
        assertTrue(account.confirmTwoFactor(code) is ApiResult.Success)
        // Sin avanzar el reloj, el mismo código en la reautenticación ya se usó.
        assertEquals(ApiError.ReauthRequired, account.regenerateRecoveryCodes(reauth(setup.secret)).error())
        clock += 30_000
        assertTrue(account.regenerateRecoveryCodes(reauth(setup.secret)) is ApiResult.Success)
    }

    @Test
    fun `una authKey incorrecta o un TOTP faltante es reauth_required`() = runBlocking {
        val (secret, _) = enable()
        assertEquals(ApiError.ReauthRequired, account.regenerateRecoveryCodes(reauth(secret, wrongKey)).error())
        clock += 30_000
        assertEquals(ApiError.ReauthRequired, account.regenerateRecoveryCodes(reauth(null)).error())
        assertEquals(ApiError.ReauthRequired, account.disableTwoFactor(reauth(null)).error())
        assertEquals(ApiError.ReauthRequired, account.disableTwoFactor(Reauth(authKey, "000000")).error())
        assertTrue(account.getAccount().value().twoFactorEnabled)
    }

    @Test
    fun `regenerar invalida los codigos anteriores`() = runBlocking {
        val (secret, old) = enable()
        val fresh = account.regenerateRecoveryCodes(reauth(secret)).value()
        assertEquals(10, fresh.size)
        assertTrue(fresh.intersect(old.toSet()).isEmpty())
        val oldChallenge = login()
        assertTrue(auth.verifyRecoveryCode(oldChallenge.challengeToken, old.first().replace("-", "")).error() is ApiError.InvalidCode)
        val freshChallenge = login()
        assertTrue(auth.verifyRecoveryCode(freshChallenge.challengeToken, fresh.first().replace("-", "")) is ApiResult.Success)
    }

    @Test
    fun `un codigo de respaldo sirve una sola vez`() = runBlocking {
        val (_, recovery) = enable()
        val code = recovery.first().replace("-", "")
        assertTrue(auth.verifyRecoveryCode(login().challengeToken, code) is ApiResult.Success)
        assertTrue(auth.verifyRecoveryCode(login().challengeToken, code).error() is ApiError.InvalidCode)
    }

    @Test
    fun `desactivar borra la clave y los codigos`() = runBlocking {
        val (secret, old) = enable()
        assertTrue(account.disableTwoFactor(reauth(secret)) is ApiResult.Success)
        assertFalse(account.getAccount().value().twoFactorEnabled)
        // Sin 2FA el acceso ya no pide segundo factor.
        assertTrue((auth.login(email, authKey, "Pixel") as ApiResult.Success).value is LoginResult.Success)
        // Al volver a activarlo, los códigos de antes no existen.
        clock += 30_000
        val again = account.startTwoFactorSetup().value()
        account.confirmTwoFactor(totp(again.secret)).value()
        clock += 30_000
        assertTrue(auth.verifyRecoveryCode(login().challengeToken, old.first().replace("-", "")).error() is ApiError.InvalidCode)
    }

    @Test
    fun `regenerar sin 2FA activo es reauth_required`() = runBlocking {
        assertEquals(ApiError.ReauthRequired, account.regenerateRecoveryCodes(reauth(null)).error())
    }
}
