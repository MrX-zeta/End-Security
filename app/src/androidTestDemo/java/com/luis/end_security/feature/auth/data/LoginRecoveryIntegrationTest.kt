package com.luis.end_security.feature.auth.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.luis.end_security.core.common.AndroidDeviceClock
import com.luis.end_security.core.common.ApiError
import com.luis.end_security.core.common.ApiResult
import com.luis.end_security.core.crypto.KdfParams
import com.luis.end_security.core.crypto.KeyDerivation
import com.luis.end_security.core.crypto.ProtectedVaultKey
import com.luis.end_security.core.crypto.RandomBytes
import com.luis.end_security.core.crypto.SecureBytes
import com.luis.end_security.core.crypto.keystore.BiometricKeyStore
import com.luis.end_security.core.crypto.keystore.TokenKeyManager
import com.luis.end_security.core.database.AppDatabase
import com.luis.end_security.core.database.entity.AccountEntity
import com.luis.end_security.core.database.entity.SyncState
import com.luis.end_security.core.database.entity.VaultItemEntity
import com.luis.end_security.core.datastore.SettingsStore
import com.luis.end_security.core.datastore.TokenStore
import com.luis.end_security.core.datastore.UnlockAttemptStore
import com.luis.end_security.core.network.ApiClient
import com.luis.end_security.core.network.ApiTransport
import com.luis.end_security.core.network.Base64Url
import com.luis.end_security.core.network.HttpRequest
import com.luis.end_security.core.network.HttpResponse
import com.luis.end_security.core.network.TokenManager
import com.luis.end_security.core.network.api.AccountApiImpl
import com.luis.end_security.core.network.api.AuthApiImpl
import com.luis.end_security.core.network.fake.FakeServer
import com.luis.end_security.core.network.fake.FakeState
import com.luis.end_security.core.session.SessionLifecycleLock
import com.luis.end_security.core.session.VaultSession
import com.luis.end_security.core.session.VaultState
import com.luis.end_security.core.sync.SyncShutdown
import com.luis.end_security.feature.account.domain.MasterPasswordChange
import com.luis.end_security.feature.account.domain.Reauth
import com.luis.end_security.feature.account.domain.ServerKdf
import com.luis.end_security.feature.auth.domain.AuthStep
import com.luis.end_security.feature.auth.domain.MasterPasswordNormalizer
import com.luis.end_security.feature.vault.data.ItemCipher
import com.luis.end_security.feature.vault.domain.VaultItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.util.UUID
import javax.crypto.Cipher

/**
 * `login()` de punta a punta contra el backend falso (Argon2 real), Room en memoria y el Keystore del emulador: la recuperación
 * tras un cambio de contraseña que el servidor aplicó y el teléfono no llegó a guardar (tarea 19, Paso 0). Cada condición (a, b,
 * c) falla por separado y la combinación válida conserva los elementos, los pendientes y el cursor.
 */
@RunWith(AndroidJUnit4::class)
class LoginRecoveryIntegrationTest {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val email = "ana@correo.com"
    private val oldPassword = "Tortuga-violeta-salta-29-nubes"
    private val newPassword = "Nube-gris-camina-41-rapido"
    private val codes = mutableMapOf<String, String>()
    private val state = FakeState(Base64Url.encode(ByteArray(32) { 1 }), Base64Url.encode(ByteArray(32) { 2 }))
    private val server = FakeServer(state, { System.currentTimeMillis() }) { e, c -> codes[e] = c }
    private val transport = object : ApiTransport {
        override suspend fun execute(request: HttpRequest): HttpResponse = server.handle(request)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var file: File
    private lateinit var db: AppDatabase
    private lateinit var tokens: TokenManager
    private lateinit var client: ApiClient
    private lateinit var authApi: AuthApiImpl
    private lateinit var accountApi: AccountApiImpl
    private lateinit var session: VaultSession
    private lateinit var repo: AuthRepositoryImpl

    private class NoBiometricKeys : BiometricKeyStore {
        override fun newEncryptCipher(userId: UUID): Cipher = error("no se usa")

        override fun decryptCipher(userId: UUID, iv: ByteArray): Cipher = error("no se usa")

        override fun delete(userId: UUID) = Unit

        override fun deleteAll() = Unit
    }

    @Before
    fun setUp() {
        file = File(context.cacheDir, "recovery-${System.nanoTime()}.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) { file }
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        tokens = TokenManager(TokenStore(dataStore, TokenKeyManager())) { authApi }
        client = ApiClient(transport, tokens)
        authApi = AuthApiImpl(client)
        accountApi = AccountApiImpl(client)
        session = VaultSession(AndroidDeviceClock)
        repo = AuthRepositoryImpl(
            api = authApi,
            tokens = tokens,
            accountDao = db.accountDao(),
            database = db,
            session = session,
            attempts = UnlockAttemptStore(dataStore, AndroidDeviceClock),
            pending = PendingAuth(AndroidDeviceClock, scope),
            sync = object : SyncShutdown {
                override suspend fun stop(whileIdle: suspend () -> Unit) = whileIdle()
            },
            biometricKeys = NoBiometricKeys(),
            settings = SettingsStore(dataStore),
            sessionLock = SessionLifecycleLock(),
            serverCheck = ServerAccountCheck(db.vaultItemDao()),
            clipboard = com.luis.end_security.core.security.SecretClipboardController(
                object : com.luis.end_security.core.security.ClipboardPort {
                    override fun set(label: String, text: String) = Unit

                    override fun currentLabel(): String? = null

                    override fun clear() = Unit
                },
                SettingsStore(dataStore),
                scope,
            ),
            defaultDispatcher = Dispatchers.Default,
            scope = scope,
        )
    }

    @After
    fun tearDown() {
        session.lock()
        db.close()
        file.delete()
    }

    // ------------------------------------------------------------------ Preparación

    private lateinit var oldRow: AccountEntity
    private lateinit var userId: UUID
    private val pendingItemId = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val syncedItemId = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private var vaultKeyBytes = ByteArray(0)

    private fun item(name: String) = Instant.parse("2026-11-01T10:00:00Z").let { VaultItem(name, "ana", "secreto-$name", null, null, it, it) }

    private fun seal(id: UUID, name: String, viaSession: Boolean = true, otherKey: ByteArray? = null) =
        if (viaSession) {
            session.withVaultKey { ItemCipher.encrypt(it, id, userId, item(name)) }
        } else {
            SecureBytes.copyOf(checkNotNull(otherKey)).use { ItemCipher.encrypt(it, id, userId, item(name)) }
        }

    /** Registra y entra con [oldPassword], guarda un elemento sincronizado y uno pendiente y deja un cursor de sincronización. */
    private fun givenAccountWithItems(): Unit = runBlocking {
        assertTrue(repo.register(email, oldPassword) is ApiResult.Success)
        assertEquals(ApiResult.Success(AuthStep.VaultUnlocked), repo.verifyEmail(email, codes.getValue(email)))
        oldRow = checkNotNull(db.accountDao().get())
        userId = UUID.fromString(oldRow.userId)
        session.withVaultKey { it.withBytes { bytes -> vaultKeyBytes = bytes.copyOf() } }
        val synced = seal(syncedItemId, "Correo")
        val pending = seal(pendingItemId, "Banco")
        db.vaultItemDao().insert(
            VaultItemEntity(syncedItemId.toString(), 1, synced.nonce, synced.ciphertext, 3L, 3L, 1L, SyncState.Synced, null, 10L),
        )
        db.vaultItemDao().insert(
            VaultItemEntity(pendingItemId.toString(), 1, pending.nonce, pending.ciphertext, null, null, 2L, SyncState.Pending, null, 20L),
        )
        db.accountDao().updateSyncCursor(7L, 1_000L)
        oldRow = checkNotNull(db.accountDao().get())
    }

    /**
     * El servidor aplica el cambio a [newPassword] (misma `vault_key`, sal nueva) y el teléfono no llega a guardarlo: la fila de
     * Room sigue como antes, la sesión se bloquea y los tokens locales se pierden (proceso nuevo). Devuelve la `key_version` nueva.
     */
    private fun serverChangesPasswordButPhoneDoesNot(): Int = runBlocking {
        val newSalt = RandomBytes.next(16)
        val oldKeys = KeyDerivation.deriveMasterKey(MasterPasswordNormalizer.normalize(oldPassword), oldRow.kdfSalt, KdfParams.DEFAULT)
            .use { KeyDerivation.encKey(it) to KeyDerivation.authKey(it) }
        val newKeys = KeyDerivation.deriveMasterKey(MasterPasswordNormalizer.normalize(newPassword), newSalt, KdfParams.DEFAULT)
            .use { KeyDerivation.encKey(it) to KeyDerivation.authKey(it) }
        val pvk = session.withVaultKey { ProtectedVaultKey.wrap(newKeys.first, it, userId, 2) }
        val result = accountApi.changeMasterPassword(
            MasterPasswordChange(
                Reauth(oldKeys.second, null), 1, newKeys.second, ServerKdf(KdfParams.DEFAULT.memoryKiB, 3, 1, newSalt), pvk,
            ),
        )
        listOf(oldKeys.first, oldKeys.second, newKeys.first, newKeys.second).forEach { it.close() }
        val version = (result as ApiResult.Success).value
        // Proceso nuevo: sin tokens ni llave en memoria; Room conserva lo de antes.
        tokens.clear()
        session.lock()
        db.accountDao().replace(oldRow)
        version
    }

    private fun login(password: String) = runBlocking { repo.login(email, password) }

    private fun row() = runBlocking { db.accountDao().get() }

    private fun loginEvents() = state.events.count { it.eventType == "login" }

    private fun assertItemsAndPendingKept() = runBlocking {
        val synced = checkNotNull(db.vaultItemDao().get(syncedItemId.toString()))
        val pending = checkNotNull(db.vaultItemDao().get(pendingItemId.toString()))
        assertEquals(SyncState.Synced, synced.syncState)
        assertEquals(SyncState.Pending, pending.syncState)
        assertEquals(1, db.vaultItemDao().unsyncedCount())
    }

    // ------------------------------------------------------------------ Combinación válida

    @Test
    fun recuperacionValida_reemplazaLaCuentaYConservaElementosPendientesYCursor() {
        givenAccountWithItems()
        val newVersion = serverChangesPasswordButPhoneDoesNot()
        assertEquals(VaultState.Locked, session.state.value)

        assertEquals(ApiResult.Success(AuthStep.VaultUnlocked), login(newPassword))

        val row = checkNotNull(row())
        assertEquals(newVersion, row.keyVersion)
        assertFalse("sal nueva", row.kdfSalt.contentEquals(oldRow.kdfSalt))
        assertEquals("el cursor sigue valiendo", 7L, row.syncCursor)
        assertItemsAndPendingKept()
        // La misma vault_key: la sesión abre y descifra lo que ya había.
        assertTrue(session.state.value is VaultState.Unlocked)
        session.withVaultKey { it.withBytes { bytes -> assertArrayEquals(vaultKeyBytes, bytes) } }
        val stored = checkNotNull(runBlocking { db.vaultItemDao().get(pendingItemId.toString()) })
        val opened = session.withVaultKey {
            ItemCipher.decrypt(it, pendingItemId, userId, stored.formatVersion, com.luis.end_security.core.crypto.Sealed(stored.nonce!!, stored.ciphertext!!))
        }
        assertEquals("Banco", opened.name)
    }

    // ------------------------------------------------------------------ Cada condición falla por separado

    @Test
    fun contrasenaVigenteAquiConSalAjena_seRechazaSinMandarNada() {
        givenAccountWithItems()
        serverChangesPasswordButPhoneDoesNot()
        val before = loginEvents()
        val failedBefore = state.events.size
        // La contraseña VIEJA abre la pvk local: la sal distinta del servidor no es legítima (M2). No se manda nada.
        val result = login(oldPassword)
        assertEquals(ApiResult.Failure(ApiError.ServerError), result)
        assertEquals("el servidor no recibió ningún acceso", before, loginEvents())
        assertEquals(failedBefore, state.events.size)
        assertEquals(oldRow.keyVersion, row()!!.keyVersion)
        assertEquals(VaultState.Locked, session.state.value)
        assertItemsAndPendingKept()
    }

    @Test
    fun condicionB_keyVersionNoMayorQueLaLocal_seRechazaYSeCierraLaSesionDelServidor() {
        givenAccountWithItems()
        serverChangesPasswordButPhoneDoesNot()
        // El teléfono cree estar en la 2 o más: el servidor (en la 2) no es «mayor».
        val ahead = AccountEntity(
            oldRow.userId, oldRow.email, null, oldRow.kdfSalt, oldRow.kdfMemoryKiB, oldRow.kdfIterations, oldRow.kdfParallelism,
            oldRow.pvkNonce, oldRow.pvkCiphertext, 2, 7L, 1_000L, null, null,
        )
        runBlocking { db.accountDao().replace(ahead) }
        assertEquals(ApiResult.Failure(ApiError.ServerError), login(newPassword))
        assertEquals(2, row()!!.keyVersion)
        assertArrayEquals(oldRow.kdfSalt, row()!!.kdfSalt)
        assertEquals(VaultState.Locked, session.state.value)
        assertFalse("sin tokens locales", runBlocking { tokens.hasSession() })
        assertItemsAndPendingKept()
    }

    @Test
    fun condicionC_laVaultKeyNoAbreLoQueHayEnElTelefono_seRechaza() {
        givenAccountWithItems()
        serverChangesPasswordButPhoneDoesNot()
        // Un elemento local sellado con otra llave: la pvk del servidor abre, pero no es la `vault_key` de lo local.
        val foreign = seal(syncedItemId, "Correo", viaSession = false, otherKey = ByteArray(32) { 9 })
        runBlocking {
            db.openHelper.writableDatabase.execSQL("DELETE FROM vault_items")
            db.vaultItemDao().insert(
                VaultItemEntity(syncedItemId.toString(), 1, foreign.nonce, foreign.ciphertext, 3L, 3L, 1L, SyncState.Synced, null, 10L),
            )
        }
        assertEquals(ApiResult.Failure(ApiError.ServerError), login(newPassword))
        assertEquals(1, row()!!.keyVersion)
        assertEquals(VaultState.Locked, session.state.value)
        assertFalse(runBlocking { tokens.hasSession() })
    }

    @Test
    fun otraCuentaConElMismoCorreo_noEsUnaRecuperacion() {
        givenAccountWithItems()
        serverChangesPasswordButPhoneDoesNot()
        val other = AccountEntity(
            UUID.randomUUID().toString(), oldRow.email, null, oldRow.kdfSalt, oldRow.kdfMemoryKiB, oldRow.kdfIterations,
            oldRow.kdfParallelism, oldRow.pvkNonce, oldRow.pvkCiphertext, 1, 7L, 1_000L, null, null,
        )
        runBlocking { db.accountDao().replace(other) }
        assertEquals(ApiResult.Failure(ApiError.ServerError), login(newPassword))
        assertEquals(other.userId, row()!!.userId)
        assertEquals(VaultState.Locked, session.state.value)
    }

    @Test
    fun parametrosMasBajosQueLosLocales_seRechazanAntesDeDerivar() {
        givenAccountWithItems()
        serverChangesPasswordButPhoneDoesNot()
        // La cuenta local exige más costo que el que ahora manda el servidor (condición a).
        val costlier = AccountEntity(
            oldRow.userId, oldRow.email, null, oldRow.kdfSalt, oldRow.kdfMemoryKiB, 4, oldRow.kdfParallelism,
            oldRow.pvkNonce, oldRow.pvkCiphertext, 1, 7L, 1_000L, null, null,
        )
        runBlocking { db.accountDao().replace(costlier) }
        val before = loginEvents()
        assertEquals(ApiResult.Failure(ApiError.ServerError), login(newPassword))
        assertEquals("no se mandó nada", before, loginEvents())
        assertEquals(1, row()!!.keyVersion)
    }
}
