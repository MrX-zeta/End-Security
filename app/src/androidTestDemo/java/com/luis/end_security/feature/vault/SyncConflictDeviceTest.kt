package com.luis.end_security.feature.vault

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.luis.end_security.core.common.ApiResult
import com.luis.end_security.core.common.DeviceClock
import com.luis.end_security.core.crypto.Sealed
import com.luis.end_security.core.crypto.SecureBytes
import com.luis.end_security.core.database.AppDatabase
import com.luis.end_security.core.database.entity.AccountEntity
import com.luis.end_security.core.database.entity.SyncState
import com.luis.end_security.core.network.ApiClient
import com.luis.end_security.core.network.ApiTransport
import com.luis.end_security.core.network.Base64Url
import com.luis.end_security.core.network.HttpRequest
import com.luis.end_security.core.network.HttpResponse
import com.luis.end_security.core.network.RefreshOutcome
import com.luis.end_security.core.network.TokenProvider
import com.luis.end_security.core.network.api.AuthApiImpl
import com.luis.end_security.core.network.api.VaultApiImpl
import com.luis.end_security.core.network.fake.FakeServer
import com.luis.end_security.core.network.fake.FakeState
import com.luis.end_security.core.session.VaultSession
import com.luis.end_security.core.sync.SyncEngine
import com.luis.end_security.core.sync.SyncOutcome
import com.luis.end_security.core.sync.SyncTrigger
import com.luis.end_security.feature.account.domain.ServerKdf
import com.luis.end_security.feature.auth.domain.LoginResult
import com.luis.end_security.feature.auth.domain.RegisterRequest
import com.luis.end_security.feature.vault.data.ConflictResolver
import com.luis.end_security.feature.vault.data.VaultRepositoryImpl
import com.luis.end_security.feature.vault.domain.ConflictLoad
import com.luis.end_security.feature.vault.domain.ItemDraft
import com.luis.end_security.feature.vault.domain.KeepVersion
import com.luis.end_security.feature.vault.domain.ReadableEntry
import com.luis.end_security.feature.vault.domain.ResolveResult
import com.luis.end_security.feature.vault.domain.SaveResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.util.UUID

/**
 * Punta a punta en el emulador con **dos teléfonos** (cada uno con su Room y SQLite reales, su motor de
 * sincronización y su repositorio con cifrado real) contra el backend falso. Cubre lo que pide la tarea 12:
 * editar lo mismo en los dos lados → conflicto → resolver → una sola versión y sin aviso; y modo avión →
 * sin conexión → crear → volver la red → se sube.
 */
@RunWith(AndroidJUnit4::class)
class SyncConflictDeviceTest {

    private var now = System.currentTimeMillis()
    private val clock = object : DeviceClock {
        override fun wallMillis() = now
        override fun elapsedRealtimeMillis() = now
    }
    private val userId = UUID.fromString("0b9f3c2e-5d1a-4f7e-9a2b-6c8d1e4f7a90")
    private val session = VaultSession(clock).also { it.unlock(userId, SecureBytes.copyOf(ByteArray(32) { 9 })) }

    private val codes = mutableMapOf<String, String>()
    private val serverState = FakeState(Base64Url.encode(ByteArray(32) { 1 }), Base64Url.encode(ByteArray(32) { 2 }))
    private val server = FakeServer(serverState, { now }) { email, code -> codes[email] = code }
    private var networkDown = false
    private val transport = object : ApiTransport {
        override suspend fun execute(request: HttpRequest): HttpResponse {
            if (networkDown) throw IOException("sin red")
            return server.handle(request)
        }
    }
    private val authKey = SecureBytes.copyOf(ByteArray(32) { 7 })
    private val email = "ana@correo.com"
    private val accountId = UUID.randomUUID()
    private val triggers = object : SyncTrigger {
        override fun changed() = Unit
    }
    private val phones = mutableListOf<Phone>()

    private inner class Phone(deviceName: String) {
        private var access: String? = null
        private var refresh: String? = null
        private lateinit var authApi: AuthApiImpl
        private val tokens = object : TokenProvider {
            override suspend fun accessToken() = access
            override suspend fun refreshAfterExpiry(expiredAccessToken: String): RefreshOutcome {
                val renewed = (authApi.refresh(refresh ?: return RefreshOutcome.Revoked) as? ApiResult.Success)?.value
                    ?: return RefreshOutcome.Revoked
                access = renewed.accessToken
                refresh = renewed.refreshToken
                return RefreshOutcome.Renewed(renewed.accessToken)
            }

            override suspend fun onSessionRevoked() {
                access = null
            }
        }
        private val client = ApiClient(transport, tokens)
        val auth: AuthApiImpl = AuthApiImpl(client).also { authApi = it }
        val api = VaultApiImpl(client)
        val database: AppDatabase = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
        ).build()
        val engine: SyncEngine

        init {
            runBlocking {
                database.accountDao().replace(
                    AccountEntity(accountId.toString(), email, null, ByteArray(16), 65_536, 3, 1, ByteArray(12), ByteArray(48), 1, 0, null, null, null),
                )
            }
            engine = SyncEngine(api, database.vaultItemDao(), database.accountDao(), clock)
            phones += this
        }

        val repository = VaultRepositoryImpl(database.vaultItemDao(), session, clock, Dispatchers.Default, engine, triggers)
        val name = deviceName

        fun signIn() = runBlocking {
            val result = (auth.login(email, authKey, name) as ApiResult.Success).value as LoginResult.Success
            access = result.authenticated.tokens.accessToken
            refresh = result.authenticated.tokens.refreshToken
        }

        fun sync(): SyncOutcome = runBlocking { engine.sync() }
    }

    @After
    fun tearDown() {
        session.lock()
        phones.forEach { it.database.close() }
    }

    private fun firstPhone(): Phone {
        val phone = Phone("Pixel")
        runBlocking {
            phone.auth.register(
                RegisterRequest(accountId, email, null, authKey, ServerKdf(65_536, 3, 1, ByteArray(16) { 3 }), Sealed(ByteArray(12) { 4 }, ByteArray(48) { 5 })),
            )
            phone.auth.verifyEmail(email, codes.getValue(email), phone.name)
        }
        phone.signIn()
        return phone
    }

    private fun secondPhone() = Phone("Redmi").also { it.signIn() }

    private fun draft(name: String, password: String) = ItemDraft(name, "ana@correo.com", password, "netflix.com", null)

    private suspend fun entry(phone: Phone, id: UUID) = phone.repository.entry(id) as ReadableEntry

    @Test
    fun editar_lo_mismo_en_dos_telefonos_da_un_conflicto_que_se_resuelve_a_una_sola_version() = runBlocking {
        val a = firstPhone()
        val b = secondPhone()
        val resolver = ConflictResolver(a.database.vaultItemDao(), a.api, session, a.repository, clock, triggers, Dispatchers.Default)

        val id = (a.repository.create(draft("Netflix", "clave-inicial-0")) as SaveResult.Saved).id
        assertEquals(SyncOutcome.Synced, a.sync())
        assertEquals(SyncOutcome.Synced, b.sync())

        now += 1_000
        b.repository.update(id, entry(b, id).localSeq, draft("Netflix (otro)", "clave-del-otro-2"))
        assertEquals(SyncOutcome.Synced, b.sync())
        now += 1_000
        a.repository.update(id, entry(a, id).localSeq, draft("Netflix (aqui)", "clave-de-aqui-1"))
        a.sync()

        // El aviso «1 contraseña cambió en dos dispositivos» sale de aquí.
        assertEquals(listOf(id), a.repository.observeConflictIds().first())
        assertEquals("Netflix (aqui)", resolver.observeConflicts().first().single().name)

        val detail = (resolver.load(id) as ConflictLoad.Ready).detail
        assertTrue(resolver.resolve(detail, KeepVersion.Local) is ResolveResult.Resolved)
        assertTrue("sin aviso al resolver", a.repository.observeConflictIds().first().isEmpty())

        assertEquals(SyncOutcome.Synced, a.sync())
        assertEquals(SyncOutcome.Synced, b.sync())
        val seenByA = entry(a, id)
        val seenByB = entry(b, id)
        assertEquals("una sola versión en los dos teléfonos", "Netflix (aqui)", seenByB.item.name)
        assertEquals(seenByA.item.name, seenByB.item.name)
        assertEquals("clave-de-aqui-1", seenByB.item.password)
        assertTrue(b.repository.observeConflictIds().first().isEmpty())
    }

    @Test
    fun elegir_la_del_otro_dispositivo_deja_esa_version_aqui_sin_subir_nada() = runBlocking {
        val a = firstPhone()
        val b = secondPhone()
        val resolver = ConflictResolver(a.database.vaultItemDao(), a.api, session, a.repository, clock, triggers, Dispatchers.Default)
        val id = (a.repository.create(draft("Netflix", "clave-inicial-0")) as SaveResult.Saved).id
        a.sync()
        b.sync()
        now += 1_000
        b.repository.update(id, entry(b, id).localSeq, draft("Netflix (otro)", "clave-del-otro-2"))
        b.sync()
        now += 1_000
        a.repository.update(id, entry(a, id).localSeq, draft("Netflix (aqui)", "clave-de-aqui-1"))
        a.sync()

        val detail = (resolver.load(id) as ConflictLoad.Ready).detail
        assertTrue(resolver.resolve(detail, KeepVersion.Remote) is ResolveResult.Resolved)

        val row = a.database.vaultItemDao().get(id.toString())!!
        assertEquals(SyncState.Synced, row.syncState)
        val kept = entry(a, id)
        assertEquals("Netflix (otro)", kept.item.name)
        assertEquals("clave-del-otro-2", kept.item.password)
        assertTrue(a.repository.observeConflictIds().first().isEmpty())
    }

    @Test
    fun modo_avion_avisa_sin_conexion_guarda_lo_creado_y_lo_sube_al_volver_la_red() = runBlocking {
        val a = firstPhone()
        assertFalse(a.engine.offline.value)

        networkDown = true
        val id = (a.repository.create(draft("Spotify", "clave-spotify-3")) as SaveResult.Saved).id
        assertEquals(SyncOutcome.Offline, a.sync())
        assertTrue("SCR-44: aviso «Sin conexión»", a.engine.offline.value)
        assertEquals("lo creado se guarda aunque no haya red", "Spotify", entry(a, id).item.name)
        assertEquals(SyncState.Pending, a.database.vaultItemDao().get(id.toString())!!.syncState)

        networkDown = false
        assertEquals(SyncOutcome.Synced, a.sync())
        assertFalse("el aviso se quita al sincronizar bien", a.engine.offline.value)
        assertEquals(SyncState.Synced, a.database.vaultItemDao().get(id.toString())!!.syncState)

        val b = secondPhone()
        assertEquals(SyncOutcome.Synced, b.sync())
        assertEquals("Spotify", entry(b, id).item.name)
    }
}
