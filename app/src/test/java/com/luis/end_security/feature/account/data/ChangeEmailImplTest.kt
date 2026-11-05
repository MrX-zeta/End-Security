package com.luis.end_security.feature.account.data

import com.luis.end_security.core.common.ApiError
import com.luis.end_security.core.common.ApiResult
import com.luis.end_security.core.crypto.KdfParams
import com.luis.end_security.core.crypto.KeyDerivation
import com.luis.end_security.core.crypto.ProtectedVaultKey
import com.luis.end_security.core.crypto.SecureBytes
import com.luis.end_security.core.database.entity.AccountEntity
import com.luis.end_security.core.network.ApiClient
import com.luis.end_security.core.network.ApiTransport
import com.luis.end_security.core.network.Base64Url
import com.luis.end_security.core.network.HttpRequest
import com.luis.end_security.core.network.HttpResponse
import com.luis.end_security.core.network.RefreshOutcome
import com.luis.end_security.core.network.TokenProvider
import com.luis.end_security.core.network.api.AccountApiImpl
import com.luis.end_security.core.network.api.AuthApiImpl
import com.luis.end_security.core.network.fake.FakeServer
import com.luis.end_security.core.network.fake.FakeState
import com.luis.end_security.feature.account.domain.AccountInfo
import com.luis.end_security.feature.account.domain.ConfirmEmailResult
import com.luis.end_security.feature.account.domain.RequestEmailResult
import com.luis.end_security.feature.account.domain.ServerKdf
import com.luis.end_security.feature.auth.domain.LoginResult
import com.luis.end_security.feature.auth.domain.MasterPasswordNormalizer
import com.luis.end_security.feature.auth.domain.RegisterRequest
import com.luis.end_security.feature.vault.data.FakeAccountDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.UUID

/**
 * OP-26 (DEC-63) contra el backend falso con **Argon2 real**: EP-15 y EP-16 con reautenticación, los límites (3 por hora, 5 por día y
 * 24 h tras un cambio) con un reloj inyectable, la normalización, la anti-enumeración y el acceso con el correo nuevo.
 */
class ChangeEmailImplTest {

    private val password = "Tortuga-violeta-salta-29-nubes"
    private val email = "ana@correo.com"
    private val newEmail = "ana.nueva@correo.com"
    private val userId = UUID.fromString("0b9f3c2e-5d1a-4f7e-9a2b-6c8d1e4f7a90")
    private val salt = ByteArray(16) { (it + 1).toByte() }

    private var clock = 1_800_000_000_000L
    private var offline = false
    private val codes = mutableMapOf<String, String>()
    private val state = FakeState(Base64Url.encode(ByteArray(32) { 1 }), Base64Url.encode(ByteArray(32) { 2 }))
    private val server = FakeServer(state, { clock }) { e, c -> codes[e] = c }
    private val transport = object : ApiTransport {
        override suspend fun execute(request: HttpRequest): HttpResponse {
            if (offline) throw IOException("sin red")
            return server.handle(request)
        }
    }

    private class MemoryTokens : TokenProvider {
        var access: String? = null
        override suspend fun accessToken() = access
        override suspend fun refreshAfterExpiry(expiredAccessToken: String): RefreshOutcome = RefreshOutcome.Revoked
        override suspend fun onSessionRevoked() {
            access = null
        }
    }

    private val tokens = MemoryTokens()
    private val client = ApiClient(transport, tokens)
    private val auth = AuthApiImpl(client)
    private val account = AccountApiImpl(client)
    private lateinit var accounts: FakeAccountDao
    private lateinit var useCase: ChangeEmailImpl
    private lateinit var authKey: SecureBytes

    private fun keysOf(password: String, salt: ByteArray): Pair<SecureBytes, SecureBytes> =
        KeyDerivation.deriveMasterKey(MasterPasswordNormalizer.normalize(password), salt, KdfParams.DEFAULT).use {
            KeyDerivation.encKey(it) to KeyDerivation.authKey(it)
        }

    private fun setUp() = runBlocking {
        val (enc, key) = keysOf(password, salt)
        authKey = key
        val vaultKey = SecureBytes.copyOf(ByteArray(32) { (it * 3 + 1).toByte() })
        val pvk = ProtectedVaultKey.wrap(enc, vaultKey, userId, 1)
        auth.register(RegisterRequest(userId, email, null, key, ServerKdf(65_536, 3, 1, salt), pvk))
        val verified = (auth.verifyEmail(email, codes.getValue(email), "Pixel 7 Pro") as ApiResult.Success).value
        tokens.access = verified.tokens.accessToken
        accounts = FakeAccountDao(AccountEntity(userId.toString(), email, null, salt, 65_536, 3, 1, pvk.nonce, pvk.ciphertext, 1, 0, null, null, null))
        vaultKey.close()
        enc.close()
        useCase = ChangeEmailImpl(account, accounts, ReauthRunnerImpl(accounts, Dispatchers.Default), AccountEmailSync(accounts))
    }

    private var sessionStartedAt = clock

    /** El `access_token` dura 15 min: tras mover mucho el reloj se abre otra sesión (como al volver a entrar). */
    private fun keepSessionAlive() = runBlocking {
        if (clock - sessionStartedAt < 10 * 60_000L && clock >= sessionStartedAt) return@runBlocking
        val result = auth.login(serverUser().email, authKey, "Pixel 7 Pro")
        tokens.access = ((result as ApiResult.Success).value as LoginResult.Success).authenticated.tokens.accessToken
        sessionStartedAt = clock
    }

    private fun request(to: String = newEmail, pass: String = password): RequestEmailResult {
        keepSessionAlive()
        return runBlocking { useCase.request(to, pass, null) }
    }

    private fun confirm(code: String): ConfirmEmailResult {
        keepSessionAlive()
        return runBlocking { useCase.confirm(code) }
    }

    private fun serverUser() = state.users.getValue(userId.toString())

    private fun loginAs(address: String) = runBlocking { auth.login(address, authKey, "Otro teléfono") }

    // --- EP-15 ---

    @Test
    fun `pedir el cambio manda un codigo al correo nuevo y no cambia nada todavia`() {
        setUp()
        assertEquals(RequestEmailResult.Accepted, request())
        assertTrue(codes.containsKey(newEmail))
        assertEquals(email, serverUser().email)
        assertEquals(email, accounts.current!!.email)
        assertEquals("email_change_requested", state.events.last().eventType)
        assertEquals("Pixel 7 Pro", state.events.last().deviceName)
    }

    @Test
    fun `el mismo correo tras normalizar es 400 y no cuenta para el limite`() {
        setUp()
        assertEquals(RequestEmailResult.SameAsCurrent, request(to = "  ANA@Correo.com "))
        assertTrue(serverUser().emailChangeRequests.isEmpty())
        assertNull(serverUser().pendingEmail)
    }

    @Test
    fun `un correo con la e compuesta o descompuesta es el mismo para el servidor`() {
        setUp()
        runBlocking {
            // El correo actual del servidor lleva la «é» compuesta; la solicitud la lleva descompuesta.
            serverUser().email = "josé@correo.com"
        }
        assertEquals(RequestEmailResult.SameAsCurrent, request(to = "josé@correo.com"))
    }

    @Test
    fun `un correo en uso responde 202 sin codigo efectivo`() {
        setUp()
        val (_, otherKey) = keysOf("Otra-contrasena-larga-77-nubes", ByteArray(16) { 9 })
        runBlocking {
            val otherId = UUID.randomUUID()
            val enc = keysOf("Otra-contrasena-larga-77-nubes", ByteArray(16) { 9 }).first
            val pvk = ProtectedVaultKey.wrap(enc, SecureBytes.copyOf(ByteArray(32) { 5 }), otherId, 1)
            AuthApiImpl(ApiClient(transport, MemoryTokens())).register(
                RegisterRequest(otherId, "ocupado@correo.com", null, otherKey, ServerKdf(65_536, 3, 1, ByteArray(16) { 9 }), pvk),
            )
            enc.close()
        }
        // El correo ocupado: 202 igual que uno libre (no se distingue), pero no hay código efectivo.
        assertEquals(RequestEmailResult.Accepted, request(to = "ocupado@correo.com"))
        // EP-16 responde igual que con un correo libre: código incorrecto con intentos, no «ya no sirve» (anti-enumeración).
        assertEquals(ConfirmEmailResult.WrongCode(4), confirm("123456"))
        assertEquals(email, serverUser().email)
    }

    @Test
    fun `un correo libre y uno ocupado son indistinguibles tambien en EP-16`() {
        setUp()
        request(to = "libre@correo.com")
        val free = confirm("000000")
        assertEquals(ConfirmEmailResult.WrongCode(4), free)
    }

    @Test
    fun `contrasena incorrecta`() {
        setUp()
        assertEquals(RequestEmailResult.WrongCredentials, request(pass = "otra-contrasena-Equivocada-1"))
        assertTrue(serverUser().emailChangeRequests.isEmpty())
    }

    @Test
    fun `una key_version mayor en el servidor se avisa como conflicto`() {
        setUp()
        runBlocking {
            accounts.replace(AccountEntity(userId.toString(), email, null, ByteArray(16) { 9 }, 65_536, 3, 1, ByteArray(12), ByteArray(48), 0, 0, null, null, null))
        }
        assertEquals(RequestEmailResult.KeyVersionConflict, request())
    }

    @Test
    fun `sin red es el generico`() {
        setUp()
        offline = true
        assertEquals(RequestEmailResult.Generic, request())
    }

    // --- Límites de DEC-63 ---

    @Test
    fun `la cuarta solicitud en una hora es 429 con los segundos que faltan`() {
        setUp()
        repeat(3) {
            assertEquals(RequestEmailResult.Accepted, request())
            clock += 1_000
        }
        val result = request()
        // La primera fue hace 3 s: faltan 3 597 s para que salga de la ventana de la hora.
        assertEquals(RequestEmailResult.RateLimited(3_597), result)
    }

    @Test
    fun `pasada la hora se puede otra, y la sexta del dia es 429`() {
        setUp()
        repeat(3) {
            assertEquals(RequestEmailResult.Accepted, request())
            clock += 1_000
        }
        // 3 s después de la primera + 3 600 s: sale de la ventana de la hora.
        clock += 3_600_000
        assertEquals(RequestEmailResult.Accepted, request())
        clock += 1_000
        assertEquals(RequestEmailResult.Accepted, request())
        clock += 1_000
        // Ya van 5 en el día (las del principio siguen dentro de las 24 h): la sexta espera a que salga la primera.
        val sixth = request()
        assertTrue(sixth is RequestEmailResult.RateLimited)
        val first = serverUser().emailChangeRequests.min()
        val expected = ((first + 24 * 3_600_000L - clock + 999) / 1000).toInt()
        assertEquals(RequestEmailResult.RateLimited(expected), sixth)
    }

    @Test
    fun `un correo igual o una contrasena mala no gastan solicitudes`() {
        setUp()
        repeat(4) { request(to = email) }
        repeat(4) { request(pass = "otra-contrasena-Equivocada-1") }
        assertEquals(RequestEmailResult.Accepted, request())
    }

    @Test
    fun `despues de un cambio completado hay que esperar 24 horas, con el tiempo exacto`() {
        setUp()
        assertEquals(RequestEmailResult.Accepted, request())
        val code = codes.getValue(newEmail)
        clock += 60_000
        assertEquals(ConfirmEmailResult.Changed(newEmail), confirm(code))
        val changedAt = clock
        clock += 10_000
        // 10 s después del cambio: faltan 86 390 s.
        assertEquals(RequestEmailResult.RateLimited(86_390), request(to = "otro.mas@correo.com"))
        clock = changedAt + 24 * 3_600_000L - 1_000
        assertEquals(RequestEmailResult.RateLimited(1), request(to = "otro.mas@correo.com"))
        // A las 24 h ya se puede (el correo actual es ahora el nuevo, así que se pide otro distinto).
        clock = changedAt + 24 * 3_600_000L
        assertEquals(RequestEmailResult.Accepted, request(to = "otro.mas@correo.com"))
    }

    // --- EP-16 ---

    @Test
    fun `confirmar con el codigo cambia el correo del servidor y el de Room`() {
        setUp()
        request()
        val result = confirm(codes.getValue(newEmail))
        assertEquals(ConfirmEmailResult.Changed(newEmail), result)
        assertEquals(newEmail, serverUser().email)
        assertEquals(newEmail, accounts.current!!.email)
        assertEquals("email_changed", state.events.last().eventType)
        assertEquals("Pixel 7 Pro", state.events.last().deviceName)
        assertNull(serverUser().pendingEmail)
    }

    @Test
    fun `con el correo nuevo se entra y con el viejo responde como una cuenta inexistente`() = runBlocking {
        setUp()
        request()
        confirm(codes.getValue(newEmail))
        val fresh = loginAs(newEmail)
        assertTrue(fresh is ApiResult.Success && fresh.value is LoginResult.Success)
        val old = loginAs(email)
        val unknown = loginAs("nadie@correo.com")
        assertEquals(ApiResult.Failure(ApiError.InvalidCredentials), old)
        assertEquals(unknown, old)
    }

    @Test
    fun `un codigo incorrecto dice cuantos intentos quedan y a los cinco ya no sirve`() {
        setUp()
        request()
        assertEquals(ConfirmEmailResult.WrongCode(4), confirm("000000"))
        assertEquals(ConfirmEmailResult.WrongCode(3), confirm("000001"))
        assertEquals(ConfirmEmailResult.WrongCode(2), confirm("000002"))
        assertEquals(ConfirmEmailResult.WrongCode(1), confirm("000003"))
        assertEquals(ConfirmEmailResult.WrongCode(0), confirm("000004"))
        // Sin intentos: ni el código bueno sirve.
        assertEquals(ConfirmEmailResult.Expired, confirm(codes.getValue(newEmail)))
        assertEquals(email, serverUser().email)
    }

    @Test
    fun `el codigo vence a los 15 minutos`() {
        setUp()
        request()
        clock += 15 * 60_000L + 1
        assertEquals(ConfirmEmailResult.Expired, confirm(codes.getValue(newEmail)))
    }

    @Test
    fun `un codigo nuevo invalida el anterior`() {
        setUp()
        request()
        val first = codes.getValue(newEmail)
        clock += 1_000
        request()
        val second = codes.getValue(newEmail)
        // Con la misma probabilidad de colisión de siempre (1 en un millón) los códigos difieren; si coincidieran, no hay nada que probar.
        if (first != second) {
            assertTrue(confirm(first) is ConfirmEmailResult.WrongCode)
            assertEquals(ConfirmEmailResult.Changed(newEmail), confirm(second))
        }
    }

    @Test
    fun `si otra cuenta ocupo el correo mientras tanto se responde como codigo invalido`() {
        setUp()
        request()
        runBlocking {
            val otherId = UUID.randomUUID()
            val (enc, otherKey) = keysOf("Otra-contrasena-larga-77-nubes", ByteArray(16) { 9 })
            val pvk = ProtectedVaultKey.wrap(enc, SecureBytes.copyOf(ByteArray(32) { 5 }), otherId, 1)
            AuthApiImpl(ApiClient(transport, MemoryTokens())).register(
                RegisterRequest(otherId, newEmail, null, otherKey, ServerKdf(65_536, 3, 1, ByteArray(16) { 9 }), pvk),
            )
            enc.close()
        }
        assertEquals(ConfirmEmailResult.WrongCode(4), confirm(codes.getValue(newEmail)))
        assertEquals(email, serverUser().email)
    }

    @Test
    fun `sin red al confirmar es el generico`() {
        setUp()
        request()
        offline = true
        assertEquals(ConfirmEmailResult.Generic, confirm("123456"))
    }

    @Test
    fun `el correo actual y 2FA salen de Room y del servidor`() = runBlocking {
        setUp()
        assertEquals(email, useCase.currentEmail())
        assertEquals(false, useCase.twoFactorEnabled())
        offline = true
        assertNull(useCase.twoFactorEnabled())
    }

    // --- Paso 3: el correo en el resto de la app ---

    private fun info(user: UUID, mail: String) = AccountInfo(
        user, mail, true, null, 1, ServerKdf(65_536, 3, 1, salt), com.luis.end_security.core.crypto.Sealed(ByteArray(12), ByteArray(48)), false, false,
    )

    @Test
    fun `con el mismo userId el correo distinto se actualiza en Room`() = runBlocking {
        setUp()
        AccountEmailSync(accounts).apply(info(userId, "cambiado@correo.com"))
        assertEquals("cambiado@correo.com", accounts.current!!.email)
    }

    @Test
    fun `con otro userId se ignora`() = runBlocking {
        setUp()
        AccountEmailSync(accounts).apply(info(UUID.randomUUID(), "ajeno@correo.com"))
        assertEquals(email, accounts.current!!.email)
    }

    @Test
    fun `sin cuenta local no hace nada y con el mismo correo no escribe`() = runBlocking {
        setUp()
        AccountEmailSync(accounts).apply(info(userId, email))
        assertEquals(email, accounts.current!!.email)
        accounts.deleteAll()
        AccountEmailSync(accounts).apply(info(userId, "cambiado@correo.com"))
        assertNull(accounts.current)
    }

    @Test
    fun `getAccount de la app guarda el correo nuevo que cambio otro telefono`() = runBlocking {
        setUp()
        request()
        // Otro teléfono confirma el cambio en el servidor (este no lo sabe todavía).
        val other = AccountApiImpl(client)
        other.confirmEmailChange(codes.getValue(newEmail))
        assertEquals(email, accounts.current!!.email)
        val syncing = EmailSyncingAccountApi(AccountApiImpl(client), AccountEmailSync(accounts))
        val result = syncing.getAccount()
        assertEquals(newEmail, (result as ApiResult.Success).value.email)
        assertEquals(newEmail, accounts.current!!.email)
    }

    @Test
    fun `la sincronizacion ignora un correo mal formado o sin normalizar`() = runBlocking {
        setUp()
        for (bad in listOf("x", "Foo@X.com", "foo@x.com ", "", "a".repeat(260) + "@x.com", "josé@correo.com")) {
            AccountEmailSync(accounts).apply(info(userId, bad))
            assertEquals("se aceptó «$bad»", email, accounts.current!!.email)
        }
    }

    @Test
    fun `la sincronizacion ignora una respuesta con otra sal`() = runBlocking {
        setUp()
        val other = AccountInfo(
            userId, "valido@correo.com", true, null, 2, ServerKdf(65_536, 3, 1, ByteArray(16) { 9 }),
            com.luis.end_security.core.crypto.Sealed(ByteArray(12), ByteArray(48)), false, false,
        )
        AccountEmailSync(accounts).apply(other)
        assertEquals(email, accounts.current!!.email)
    }
}
