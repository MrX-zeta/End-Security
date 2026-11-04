package com.luis.end_security.feature.account.data

import com.luis.end_security.core.common.ApiError
import com.luis.end_security.core.common.ApiResult
import com.luis.end_security.core.crypto.Base32
import com.luis.end_security.core.crypto.KdfParams
import com.luis.end_security.core.crypto.KeyDerivation
import com.luis.end_security.core.crypto.ProtectedVaultKey
import com.luis.end_security.core.crypto.SecureBytes
import com.luis.end_security.core.crypto.Totp
import com.luis.end_security.core.database.entity.AccountEntity
import com.luis.end_security.core.database.entity.SyncState
import com.luis.end_security.core.database.entity.VaultItemEntity
import com.luis.end_security.core.network.ApiClient
import com.luis.end_security.core.network.ApiTransport
import com.luis.end_security.core.network.Base64Url
import com.luis.end_security.core.network.HttpRequest
import com.luis.end_security.core.network.HttpResponse
import com.luis.end_security.core.network.RefreshOutcome
import com.luis.end_security.core.network.TokenProvider
import com.luis.end_security.core.network.api.AccountApiImpl
import com.luis.end_security.core.network.api.AuthApiImpl
import com.luis.end_security.core.network.api.SessionApiImpl
import com.luis.end_security.core.network.fake.FakeItem
import com.luis.end_security.core.network.fake.FakeServer
import com.luis.end_security.core.network.fake.FakeState
import com.luis.end_security.feature.account.domain.DeleteAccountResult
import com.luis.end_security.feature.account.domain.ServerKdf
import com.luis.end_security.feature.auth.domain.FakeAuthRepository
import com.luis.end_security.feature.auth.domain.LoginResult
import com.luis.end_security.feature.auth.domain.MasterPasswordNormalizer
import com.luis.end_security.feature.auth.domain.RegisterRequest
import com.luis.end_security.feature.vault.data.FakeAccountDao
import com.luis.end_security.feature.vault.data.FakeVaultItemDao
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.UUID

/**
 * OP-28 de punta a punta contra el backend falso (que cumple EP-18) con **Argon2 real**: reautenticación, baja lógica,
 * sesiones revocadas, `account_deleted` y un acceso posterior que se comporta como una cuenta inexistente.
 */
class DeleteAccountImplTest {

    private val password = "Tortuga-violeta-salta-29-nubes"
    private val email = "ana@correo.com"
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
    private val sessions = SessionApiImpl(client)
    private val otherTokens = MemoryTokens()
    private val otherSessions = SessionApiImpl(ApiClient(transport, otherTokens))

    private lateinit var accounts: FakeAccountDao
    private val items = FakeVaultItemDao()
    private val fakeAuth = FakeAuthRepository()
    private val gate = com.luis.end_security.core.session.AccountDeletionGate()
    private lateinit var useCase: DeleteAccountImpl
    private lateinit var authKey: SecureBytes

    private fun keysOf(password: String, salt: ByteArray): Pair<SecureBytes, SecureBytes> =
        KeyDerivation.deriveMasterKey(MasterPasswordNormalizer.normalize(password), salt, KdfParams.DEFAULT).use {
            KeyDerivation.encKey(it) to KeyDerivation.authKey(it)
        }

    /** Registra la cuenta en el servidor falso y en Room como lo deja un acceso real; abre además una segunda sesión «Otro». */
    private fun setUp() = runBlocking {
        val (enc, key) = keysOf(password, salt)
        authKey = key
        val vaultKey = SecureBytes.copyOf(ByteArray(32) { (it * 3 + 1).toByte() })
        val pvk = ProtectedVaultKey.wrap(enc, vaultKey, userId, 1)
        auth.register(RegisterRequest(userId, email, null, key, ServerKdf(65_536, 3, 1, salt), pvk))
        val verified = (auth.verifyEmail(email, codes.getValue(email), "Pixel 7 Pro") as ApiResult.Success).value
        tokens.access = verified.tokens.accessToken
        accounts = FakeAccountDao(AccountEntity(userId.toString(), email, null, salt, 65_536, 3, 1, pvk.nonce, pvk.ciphertext, 1, 0, null, null, null))
        val other = ((AuthApiImpl(ApiClient(transport, otherTokens)).login(email, key, "Galaxy A54") as ApiResult.Success).value as LoginResult.Success)
        otherTokens.access = other.authenticated.tokens.accessToken
        state.items["i1"] = FakeItem("i1", userId.toString(), 1, 1, 1, Base64Url.encode(ByteArray(12)), Base64Url.encode(ByteArray(48)), false, 0)
        vaultKey.close()
        enc.close()
        useCase = DeleteAccountImpl(account, accounts, items, ReauthRunnerImpl(accounts, Dispatchers.Default), fakeAuth, gate)
    }

    private fun delete(pass: String = password, totp: String? = null) = runBlocking { useCase.delete(pass, totp) }

    private fun serverUser() = state.users.getValue(userId.toString())

    @Test
    fun `sin 2FA elimina la cuenta, limpia el telefono y deja el servidor dado de baja`() {
        setUp()
        assertEquals(DeleteAccountResult.Deleted(cleanupComplete = true), delete())
        assertEquals(1, fakeAuth.wipeCalls)
        assertTrue(serverUser().deleted)
        assertTrue("se borran sus bloques", state.items.values.none { it.userId == userId.toString() })
        // Los bloques y la 2FA ya no están; el servidor registra el evento con el dispositivo que lo pidió.
        val event = state.events.last { it.eventType == "account_deleted" }
        assertEquals("Pixel 7 Pro", event.deviceName)
        assertTrue(event.success)
    }

    @Test
    fun `revoca todas las sesiones, la actual y las demas`() = runBlocking {
        setUp()
        delete()
        assertEquals(ApiError.SessionRevoked, (sessions.listSessions() as ApiResult.Failure).error)
        assertEquals(ApiError.SessionRevoked, (otherSessions.listSessions() as ApiResult.Failure).error)
    }

    @Test
    fun `despues de eliminar el acceso se comporta como con una cuenta inexistente`() = runBlocking {
        setUp()
        delete()
        val deleted = auth.login(email, authKey, "Otro teléfono")
        val unknown = auth.login("nadie@correo.com", authKey, "Otro teléfono")
        assertEquals(ApiResult.Failure(ApiError.InvalidCredentials), deleted)
        assertEquals("misma respuesta que con un correo que nunca existió", unknown, deleted)
        // El prelogin da la sal falsa de siempre (DEC-07), no la real, y es estable.
        val prelogin = (auth.prelogin(email) as ApiResult.Success).value
        assertFalse(prelogin.salt.contentEquals(salt))
        assertEquals(KdfParams.DEFAULT.memoryKiB, prelogin.memoryKiB)
        assertTrue(prelogin.salt.contentEquals((auth.prelogin(email) as ApiResult.Success).value.salt))
        // Y el correo puede registrarse de nuevo como cualquier otro libre.
        val (enc, key) = keysOf(password, salt)
        val newUser = UUID.randomUUID()
        val again = auth.register(
            RegisterRequest(newUser, email, null, key, ServerKdf(65_536, 3, 1, salt), ProtectedVaultKey.wrap(enc, SecureBytes.copyOf(ByteArray(32) { 5 }), newUser, 1)),
        )
        assertTrue(again is ApiResult.Success)
        assertTrue(state.users.containsKey(newUser.toString()))
        enc.close()
    }

    @Test
    fun `contrasena incorrecta no elimina nada ni limpia el telefono`() {
        setUp()
        assertEquals(DeleteAccountResult.WrongCredentials, delete(pass = "otra-contrasena-Equivocada-1"))
        assertEquals(0, fakeAuth.wipeCalls)
        assertFalse(serverUser().deleted)
        assertTrue(state.items.values.any { it.userId == userId.toString() })
    }

    @Test
    fun `con 2FA pide el codigo, sin el o con uno malo no elimina nada`() {
        setUp()
        val secret = runBlocking {
            val setup = (account.startTwoFactorSetup() as ApiResult.Success).value
            account.confirmTwoFactor(Totp.code(Base32.decode(setup.secret), Totp.step(clock / 1000)))
            setup.secret
        }
        clock += 30_000
        assertEquals(DeleteAccountResult.WrongCredentials, delete(totp = null))
        assertEquals(DeleteAccountResult.WrongCredentials, delete(totp = "000000"))
        assertEquals(0, fakeAuth.wipeCalls)
        assertFalse(serverUser().deleted)
        val good = Totp.code(Base32.decode(secret), Totp.step(clock / 1000))
        assertEquals(DeleteAccountResult.Deleted(true), delete(totp = good))
        assertTrue(serverUser().deleted)
        assertNull2faGone()
    }

    private fun assertNull2faGone() = assertEquals(null, serverUser().twoFactor)

    @Test
    fun `una key_version mayor en el servidor se avisa como conflicto y no como incorrectos`() {
        setUp()
        // El teléfono cree estar en la 0 y con otra sal (la contraseña cambió en otro dispositivo): la auth_key no coincide.
        runBlocking {
            accounts.replace(AccountEntity(userId.toString(), email, null, ByteArray(16) { 9 }, 65_536, 3, 1, ByteArray(12), ByteArray(48), 0, 0, null, null, null))
        }
        assertEquals(DeleteAccountResult.KeyVersionConflict, delete())
        assertEquals(0, fakeAuth.wipeCalls)
        assertFalse(serverUser().deleted)
    }

    @Test
    fun `con la misma key_version una contrasena distinta sigue siendo incorrecta`() {
        setUp()
        runBlocking {
            accounts.replace(AccountEntity(userId.toString(), email, null, ByteArray(16) { 9 }, 65_536, 3, 1, ByteArray(12), ByteArray(48), 1, 0, null, null, null))
        }
        assertEquals(DeleteAccountResult.WrongCredentials, delete())
    }

    @Test
    fun `sin red no toca nada local ni del servidor`() {
        setUp()
        offline = true
        assertEquals(DeleteAccountResult.Generic, delete())
        offline = false
        assertEquals(0, fakeAuth.wipeCalls)
        assertFalse(serverUser().deleted)
    }

    @Test
    fun `sin cuenta local es un fallo generico`() {
        setUp()
        runBlocking { accounts.deleteAll() }
        assertEquals(DeleteAccountResult.Generic, delete())
        assertEquals(0, fakeAuth.wipeCalls)
    }

    @Test
    fun `si parte de la limpieza falla igual queda eliminada y lo avisa`() {
        setUp()
        fakeAuth.wipeResult = false
        assertEquals(DeleteAccountResult.Deleted(cleanupComplete = false), delete())
        assertTrue(serverUser().deleted)
    }

    @Test
    fun `cancelar a mitad no deja el servidor eliminado con el telefono intacto`() = runBlocking {
        setUp()
        val gate = CompletableDeferred<Unit>()
        fakeAuth.gate = gate
        val job = launch(Dispatchers.Default) { useCase.delete(password, null) }
        // Se espera a que EP-18 ya haya respondido y la limpieza haya empezado (queda detenida en la compuerta).
        repeat(200) { if (fakeAuth.wipeCalls == 0) delay(50) }
        assertEquals("la limpieza empezó", 1, fakeAuth.wipeCalls)
        assertTrue("el servidor ya eliminó la cuenta", serverUser().deleted)
        job.cancel()
        gate.complete(Unit)
        job.join()
        // La limpieza no se cortó por la cancelación: una sola llamada y terminada.
        assertEquals(1, fakeAuth.wipeCalls)
        assertEquals("wipeAfterAccountDeletion", fakeAuth.calls.last { it.startsWith("wipe") })
    }

    @Test
    fun `cancelar durante Argon2 no llama al servidor`() = runBlocking {
        setUp()
        val job = launch(Dispatchers.Default) { useCase.delete(password, null) }
        delay(30)
        job.cancel()
        job.join()
        // O se cortó antes de EP-18 (nada pasó) o ya había llegado: nunca el servidor eliminado con el teléfono sin limpiar.
        assertEquals(serverUser().deleted, fakeAuth.wipeCalls == 1)
    }

    @Test
    fun `cuenta los elementos visibles y no las lapidas`() = runBlocking {
        setUp()
        fun row(id: String, deleted: Long?) = VaultItemEntity(id, 1, ByteArray(12), ByteArray(40), 1L, 1L, 1L, SyncState.Synced, deleted, 1L)
        items.put(row("a", null))
        items.put(row("b", null))
        items.put(row("c", 100L))
        assertEquals(2, useCase.itemCount())
    }

    // --- Revisión de la tarea 20: la navegación a «Cuenta eliminada» no depende de la pantalla de Eliminar ---

    @Test
    fun `con el 204 la puerta se abre antes de limpiar y avisa al terminar`() = runBlocking {
        setUp()
        var activeDuringWipe = false
        fakeAuth.onWipe = { activeDuringWipe = gate.active }
        assertEquals(DeleteAccountResult.Deleted(true), useCase.delete(password, null))
        assertTrue("el bloqueo de la limpieza no debe mandar a Desbloquear", activeDuringWipe)
        // Avisa a la navegación una vez y la puerta sigue abierta hasta que se navegó.
        kotlinx.coroutines.withTimeout(1_000) { gate.deleted.first() }
        assertTrue(gate.active)
        gate.end()
        assertFalse(gate.active)
    }

    @Test
    fun `aunque la limpieza falle avisa a la navegacion`() = runBlocking {
        setUp()
        fakeAuth.wipeResult = false
        useCase.delete(password, null)
        kotlinx.coroutines.withTimeout(1_000) { gate.deleted.first() }
    }

    @Test
    fun `si no se elimino no se abre la puerta ni se avisa`() = runBlocking {
        setUp()
        assertEquals(DeleteAccountResult.WrongCredentials, useCase.delete("otra-contrasena-Equivocada-1", null))
        offline = true
        assertEquals(DeleteAccountResult.Generic, useCase.delete(password, null))
        assertFalse(gate.active)
        assertEquals(null, kotlinx.coroutines.withTimeoutOrNull(100) { gate.deleted.first() })
    }

    @Test
    fun `cancelar durante la limpieza no deja la puerta abierta sin aviso`() = runBlocking {
        setUp()
        val hold = CompletableDeferred<Unit>()
        fakeAuth.gate = hold
        val job = launch(Dispatchers.Default) { useCase.delete(password, null) }
        repeat(200) { if (fakeAuth.wipeCalls == 0) delay(50) }
        job.cancel()
        hold.complete(Unit)
        job.join()
        // La limpieza terminó dentro de NonCancellable y la navegación recibió el aviso.
        kotlinx.coroutines.withTimeout(1_000) { gate.deleted.first() }
    }
}
