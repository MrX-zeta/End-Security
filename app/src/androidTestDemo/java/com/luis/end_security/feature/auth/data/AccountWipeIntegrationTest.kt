package com.luis.end_security.feature.auth.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.luis.end_security.core.common.AndroidDeviceClock
import com.luis.end_security.core.common.ApiResult
import com.luis.end_security.core.crypto.GeneratorOptions
import com.luis.end_security.core.crypto.keystore.BiometricKeyStore
import com.luis.end_security.core.crypto.keystore.TokenKeyManager
import com.luis.end_security.core.database.AppDatabase
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
import com.luis.end_security.core.network.api.AuthApiImpl
import com.luis.end_security.core.network.fake.FakeServer
import com.luis.end_security.core.network.fake.FakeState
import com.luis.end_security.core.security.ClipboardPort
import com.luis.end_security.core.security.SecretClipboardController
import com.luis.end_security.core.session.SessionLifecycleLock
import com.luis.end_security.core.session.VaultSession
import com.luis.end_security.core.session.VaultState
import com.luis.end_security.core.sync.SyncShutdown
import com.luis.end_security.feature.auth.domain.AuthStep
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import javax.crypto.Cipher
import kotlin.time.Duration

/**
 * La limpieza local tras eliminar la cuenta (tarea 20, Paso 3) con Room, DataStore y Keystore reales: tras el 204 no queda
 * nada de lo de la cuenta y se conserva «onboarding visto»; si parte de la limpieza falla queda una marca y se reintenta al
 * arrancar.
 */
@RunWith(AndroidJUnit4::class)
class AccountWipeIntegrationTest {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val email = "ana@correo.com"
    private val password = "Tortuga-violeta-salta-29-nubes"
    private val codes = mutableMapOf<String, String>()
    private val state = FakeState(Base64Url.encode(ByteArray(32) { 1 }), Base64Url.encode(ByteArray(32) { 2 }))
    private val server = FakeServer(state, { System.currentTimeMillis() }) { e, c -> codes[e] = c }
    private val transport = object : ApiTransport {
        override suspend fun execute(request: HttpRequest): HttpResponse = server.handle(request)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var file: File
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var db: AppDatabase
    private lateinit var tokens: TokenManager
    private lateinit var authApi: AuthApiImpl
    private lateinit var session: VaultSession
    private lateinit var settings: SettingsStore
    private lateinit var attempts: UnlockAttemptStore
    private lateinit var repo: AuthRepositoryImpl
    private lateinit var clipboard: SecretClipboardController

    private class Port : ClipboardPort {
        var label: String? = null
        var text: String? = null
        var cleared = 0

        override fun set(label: String, text: String) {
            this.label = label
            this.text = text
        }

        override fun currentLabel(): String? = label

        override fun clear() {
            label = null
            text = null
            cleared++
        }
    }

    private class Keys : BiometricKeyStore {
        var deleteAllCalls = 0
        var failDeleteAll = false

        override fun newEncryptCipher(userId: UUID): Cipher = error("no se usa")

        override fun decryptCipher(userId: UUID, iv: ByteArray): Cipher = error("no se usa")

        override fun delete(userId: UUID) = Unit

        override fun deleteAll() {
            deleteAllCalls++
            if (failDeleteAll) throw IllegalStateException("Keystore no disponible")
        }
    }

    private val port = Port()
    private val keys = Keys()

    @Before
    fun setUp() {
        file = File(context.cacheDir, "wipe-${System.nanoTime()}.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(scope = scope) { file }
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        tokens = TokenManager(TokenStore(dataStore, TokenKeyManager())) { authApi }
        authApi = AuthApiImpl(ApiClient(transport, tokens))
        session = VaultSession(AndroidDeviceClock)
        settings = SettingsStore(dataStore)
        attempts = UnlockAttemptStore(dataStore, AndroidDeviceClock)
        clipboard = SecretClipboardController(port, settings, scope)
        repo = AuthRepositoryImpl(
            api = authApi,
            tokens = tokens,
            accountDao = db.accountDao(),
            database = db,
            session = session,
            attempts = attempts,
            pending = PendingAuth(AndroidDeviceClock, scope),
            sync = object : SyncShutdown {
                override suspend fun stop(whileIdle: suspend () -> Unit) = whileIdle()
            },
            biometricKeys = keys,
            settings = settings,
            sessionLock = SessionLifecycleLock(),
            serverCheck = ServerAccountCheck(db.vaultItemDao()),
            clipboard = clipboard,
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

    /** Una cuenta abierta con elementos, ajustes cambiados, la huella activa, intentos fallidos y algo copiado. */
    private fun givenFullAccount(): Unit = runBlocking {
        assertTrue(repo.register(email, password) is ApiResult.Success)
        assertEquals(ApiResult.Success(AuthStep.VaultUnlocked), repo.verifyEmail(email, codes.getValue(email)))
        db.vaultItemDao().insert(VaultItemEntity("11111111-1111-1111-1111-111111111111", 1, ByteArray(12), ByteArray(40), 3L, 3L, 1L, SyncState.Synced, null, 10L))
        db.vaultItemDao().insert(VaultItemEntity("22222222-2222-2222-2222-222222222222", 1, ByteArray(12), ByteArray(40), null, null, 2L, SyncState.Pending, null, 20L))
        settings.setOnboardingCompleted()
        settings.setAutolockMinutes(15)
        settings.setGeneratorOptions(GeneratorOptions(length = 24, uppercase = false, digits = false, symbols = false, avoidAmbiguous = true))
        settings.setBiometricEnabled(true)
        repeat(6) { attempts.recordFailure() }
        assertTrue("hay un bloqueo vigente", attempts.remainingLockout() > Duration.ZERO)
        clipboard.copy("secreto-copiado")
        assertEquals("secreto-copiado", port.text)
        assertTrue(session.state.value is VaultState.Unlocked)
        assertTrue(tokens.hasSession())
    }

    @Test
    fun trasElWipeNoQuedaNadaDeLaCuentaYSeConservaElOnboarding() = runBlocking {
        givenFullAccount()
        assertTrue(repo.wipeAfterAccountDeletion())

        assertFalse("sin tokens", tokens.hasSession())
        assertNull("sin cuenta en Room", db.accountDao().get())
        assertEquals("sin elementos en Room", 0, db.vaultItemDao().visibleCount())
        assertEquals(0, db.vaultItemDao().unsyncedCount())
        assertEquals("la vault_key se borró de la sesión", VaultState.Locked, session.state.value)
        assertTrue("llaves biométricas borradas", keys.deleteAllCalls >= 1)
        assertFalse("bandera de la huella apagada", settings.biometricEnabled.first())
        assertNull("el portapapeles ya no tiene lo copiado", port.text)
        assertEquals("intentos de desbloqueo a cero", Duration.ZERO, attempts.remainingLockout())
        // Ajustes ligados a la cuenta: valores por defecto.
        assertEquals(5, settings.autolockMinutes.first())
        assertEquals(30, settings.clipboardClearSeconds.first())
        val generator = settings.generatorOptions.first()
        assertEquals(GeneratorOptions().length, generator.length)
        assertTrue(generator.uppercase && generator.digits && generator.symbols)
        assertFalse(generator.avoidAmbiguous)
        // Se conserva «onboarding visto»; y ninguna marca pendiente.
        assertTrue(settings.onboardingCompleted.first())
        assertFalse(settings.isWipePending())
    }

    @Test
    fun siFallaParteDeLaLimpiezaDejaUnaMarcaYSeReintentaAlArrancar() = runBlocking {
        givenFullAccount()
        keys.failDeleteAll = true
        assertFalse("parte de la limpieza falló", repo.wipeAfterAccountDeletion())
        assertTrue("queda la marca", settings.isWipePending())
        // Lo demás sí se borró: la cuenta ya no existe.
        assertNull(db.accountDao().get())
        assertFalse(tokens.hasSession())
        assertTrue(settings.onboardingCompleted.first())

        // Al siguiente arranque se reintenta; ahora el Keystore responde.
        keys.failDeleteAll = false
        val callsBefore = keys.deleteAllCalls
        assertTrue(repo.completePendingWipe())
        assertTrue("se volvió a intentar borrar las llaves", keys.deleteAllCalls > callsBefore)
        assertFalse("la marca se quitó", settings.isWipePending())
        assertTrue(settings.onboardingCompleted.first())
    }

    @Test
    fun siElReintentoVuelveAFallarLaMarcaSigue() = runBlocking {
        givenFullAccount()
        keys.failDeleteAll = true
        repo.wipeAfterAccountDeletion()
        assertFalse(repo.completePendingWipe())
        assertTrue(settings.isWipePending())
    }

    @Test
    fun sinMarcaPendienteElArranqueNoBorraNada() = runBlocking {
        givenFullAccount()
        val callsBefore = keys.deleteAllCalls
        assertTrue(repo.completePendingWipe())
        assertEquals("no se tocó nada", callsBefore, keys.deleteAllCalls)
        assertTrue(tokens.hasSession())
        assertTrue(db.accountDao().get() != null)
    }

    @Test
    fun unaMarcaDeOtraCuentaSeQuitaSinBorrarLaCuentaNueva() = runBlocking {
        givenFullAccount()
        keys.failDeleteAll = true
        assertFalse(repo.wipeAfterAccountDeletion())
        assertTrue("queda la marca de la cuenta eliminada", settings.isWipePending())
        // Después entra una cuenta NUEVA (el backend permite registrar de nuevo el mismo correo).
        assertTrue(repo.register("otra@correo.com", password) is ApiResult.Success)
        assertEquals(ApiResult.Success(AuthStep.VaultUnlocked), repo.verifyEmail("otra@correo.com", codes.getValue("otra@correo.com")))
        val newAccount = checkNotNull(db.accountDao().get())
        keys.failDeleteAll = false
        // Al siguiente arranque la marca es de otra cuenta: se quita y NO se borra la cuenta nueva.
        assertTrue(repo.completePendingWipe())
        assertFalse(settings.isWipePending())
        assertEquals(newAccount.userId, db.accountDao().get()?.userId)
        assertTrue(tokens.hasSession())
    }

    @Test
    fun unaMarcaSinUserIdSiguePorLaCuentaLocal() = runBlocking {
        givenFullAccount()
        settings.setWipePending("")
        assertTrue(repo.completePendingWipe())
        assertNull(db.accountDao().get())
        assertFalse(settings.isWipePending())
    }

    @Test
    fun laMarcaDeLaMismaCuentaSiReintentaLaLimpieza() = runBlocking {
        givenFullAccount()
        val userId = checkNotNull(db.accountDao().get()).userId
        settings.setWipePending(userId)
        assertTrue(repo.completePendingWipe())
        assertNull(db.accountDao().get())
        assertFalse(tokens.hasSession())
    }
}
