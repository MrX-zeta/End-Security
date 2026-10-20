package com.luis.end_security.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.luis.end_security.core.crypto.keystore.TokenKeyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** [TokenStore] con el Android Keystore real (DEC-16). */
@RunWith(AndroidJUnit4::class)
class TokenStoreDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val file = File(context.cacheDir, "token_store_test.preferences_pb")
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val dataStore = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
    private val store = TokenStore(dataStore, TokenKeyManager())

    @After
    fun tearDown() {
        runBlocking { store.clear() }
        scope.cancel()
        file.delete()
    }

    @Test
    fun guarda_y_lee_los_tokens_cifrados() = runBlocking {
        store.save(StoredTokens("access-de-prueba-123", "refresh-de-prueba-456"))
        val read = store.read()!!
        assertEquals("access-de-prueba-123", read.accessToken)
        assertEquals("refresh-de-prueba-456", read.refreshToken)
        // En DataStore solo hay bloques cifrados.
        val raw = dataStore.data.first().asMap().values.joinToString()
        assertFalse(raw.contains("access-de-prueba"))
        assertFalse(raw.contains("refresh-de-prueba"))
    }

    @Test
    fun dos_cifrados_del_mismo_token_son_distintos() = runBlocking {
        store.save(StoredTokens("mismo", "mismo"))
        val values = dataStore.data.first().asMap().values.map { it.toString() }
        assertEquals(2, values.toSet().size)
    }

    @Test
    fun un_bloque_alterado_o_borrado_da_null() = runBlocking {
        store.save(StoredTokens("a", "b"))
        val key = stringPreferencesKey("access_token")
        val stored = dataStore.data.first()[key]!!
        val tampered = stored.dropLast(2) + if (stored.endsWith("AA")) "BA" else "AA"
        dataStore.edit { it[key] = tampered }
        assertNull(store.read())
        store.clear()
        assertNull(store.read())
    }
}
