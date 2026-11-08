package com.luis.end_security.core.sync

import com.luis.end_security.core.common.ApiResult
import com.luis.end_security.core.common.DeviceClock
import com.luis.end_security.core.crypto.RandomBytes
import com.luis.end_security.core.crypto.SecureBytes
import com.luis.end_security.core.crypto.Sealed
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
import com.luis.end_security.core.network.api.AuthApiImpl
import com.luis.end_security.core.network.api.VaultApiImpl
import com.luis.end_security.core.network.fake.FakeServer
import com.luis.end_security.core.network.fake.FakeState
import com.luis.end_security.feature.account.domain.ServerKdf
import com.luis.end_security.feature.auth.domain.LoginResult
import com.luis.end_security.feature.auth.domain.RegisterRequest
import com.luis.end_security.feature.vault.data.FakeAccountDao
import com.luis.end_security.feature.vault.data.FakeVaultItemDao
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.util.UUID

/**
 * Un servidor falso y varios «teléfonos» (cada uno con su Room en memoria, su sesión y su [SyncEngine]) que
 * hablan con él por la capa de API real. Lo de la red se puede cortar o interceptar petición por petición.
 */
class SyncWorld {
    var clock = 1_800_000_000_000L
    val state = FakeState(Base64Url.encode(ByteArray(32) { 1 }), Base64Url.encode(ByteArray(32) { 2 }))
    private val codes = mutableMapOf<String, String>()
    val server = FakeServer(state, { clock }) { email, code -> codes[email] = code }

    /** Si es `true`, ninguna petición llega (modo avión). */
    var networkDown = false

    /** Se llama antes de cada petición; si devuelve una respuesta, esa se usa en lugar del servidor. */
    var interceptor: ((HttpRequest) -> HttpResponse?)? = null

    /** Se llama justo después de que el servidor procesó una petición y antes de que el teléfono vea la respuesta. */
    var afterServer: ((HttpRequest) -> Unit)? = null

    val requests = mutableListOf<String>()

    private val deviceClock = object : DeviceClock {
        override fun wallMillis() = clock
        override fun elapsedRealtimeMillis() = clock
    }

    private val transport = object : ApiTransport {
        override suspend fun execute(request: HttpRequest): HttpResponse {
            if (networkDown) throw IOException("sin red")
            requests += "${request.method} ${request.path.substringBefore('?')}"
            interceptor?.invoke(request)?.let { return it }
            return server.handle(request).also { afterServer?.invoke(request) }
        }
    }

    private val authKey = SecureBytes.copyOf(ByteArray(32) { 7 })
    private val email = "ana@correo.com"
    val userId: UUID = UUID.randomUUID()

    /** Un teléfono con su base local, su sesión y su motor. */
    inner class Device(val name: String) {
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
        val accounts = FakeAccountDao(
            AccountEntity(
                userId.toString(), email, null, ByteArray(16), 65_536, 3, 1, ByteArray(12), ByteArray(48), 1, 0, null, null, null,
            ),
        )
        val dao = FakeVaultItemDao(accounts)
        val engine = SyncEngine(api, dao, accounts, deviceClock)

        /** Inicia sesión en el servidor con la cuenta de la prueba. */
        fun signIn() = runBlocking {
            val result = (auth.login(email, authKey, name) as ApiResult.Success).value as LoginResult.Success
            access = result.authenticated.tokens.accessToken
            refresh = result.authenticated.tokens.refreshToken
        }

        /** Crea localmente un elemento nuevo, como lo deja `VaultRepository.create`. */
        fun createLocal(id: UUID = UUID.randomUUID()): UUID = runBlocking {
            dao.insert(row(id, serverVersion = null, localSeq = 1, state = SyncState.Pending))
            id
        }

        /** Edita localmente, como `VaultRepository.update`: bloque nuevo, `local_seq + 1`, `pending`. */
        fun editLocal(id: UUID): Boolean = runBlocking {
            val row = dao.get(id.toString())!!
            dao.updateContent(id.toString(), block(12), block(40), row.localSeq, clock, row.formatVersion) == 1
        }

        fun deleteLocal(id: UUID, uploadInFlight: Boolean = engine.isUploading(id)): Boolean = runBlocking {
            val row = dao.get(id.toString())!!
            dao.delete(id.toString(), row.localSeq, clock, uploadInFlight)
        }

        fun row(id: UUID) = dao.all[id.toString()]

        fun sync(): SyncOutcome = runBlocking { engine.sync() }
    }

    /** El primer teléfono: registra la cuenta. */
    fun firstDevice(): Device {
        val device = Device("Pixel")
        runBlocking {
            device.auth.register(RegisterRequest(userId, email, null, authKey, ServerKdf(65_536, 3, 1, ByteArray(16) { 3 }), Sealed(ByteArray(12) { 4 }, ByteArray(48) { 5 })))
            device.auth.verifyEmail(email, codes.getValue(email), device.name)
        }
        device.signIn()
        return device
    }

    fun secondDevice(): Device = Device("Redmi").also { it.signIn() }

    fun block(size: Int): ByteArray = RandomBytes.next(size)

    fun row(id: UUID, serverVersion: Long?, localSeq: Long, state: SyncState, deleted: Boolean = false) = VaultItemEntity(
        id = id.toString(),
        formatVersion = 1,
        nonce = if (deleted) null else block(12),
        ciphertext = if (deleted) null else block(40),
        serverVersion = serverVersion,
        serverRevision = serverVersion,
        localSeq = localSeq,
        syncState = state,
        deletedAt = if (deleted) clock else null,
        updatedAt = clock,
    )

    /** Lo que el servidor guarda de un elemento. */
    fun serverItem(id: UUID) = state.items[id.toString()]
}
