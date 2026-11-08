package com.luis.end_security.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import com.luis.end_security.core.crypto.GeneratorOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Las opciones del Generador (DEC-24) en DataStore: se recuerdan y nunca se guarda la contraseña generada. */
class SettingsStoreGeneratorTest {

    private val dataStore = InMemoryDataStore()

    @Test
    fun `la primera vez salen los valores por omision`() = runBlocking {
        val options = SettingsStore(dataStore).generatorOptions.first()
        assertEquals(GeneratorOptions(16, uppercase = true, digits = true, symbols = true, avoidAmbiguous = false), options)
    }

    @Test
    fun `las opciones sobreviven a cerrar y abrir la app`() = runBlocking {
        SettingsStore(dataStore).setGeneratorOptions(GeneratorOptions(31, uppercase = false, digits = true, symbols = false, avoidAmbiguous = true))

        val reopened = SettingsStore(dataStore).generatorOptions.first()

        assertEquals(31, reopened.length)
        assertFalse(reopened.uppercase)
        assertTrue(reopened.digits)
        assertFalse(reopened.symbols)
        assertTrue(reopened.avoidAmbiguous)
    }

    @Test
    fun `una longitud fuera de rango en disco se recorta`() = runBlocking {
        dataStore.edit { it[intPreferencesKey("generator_length")] = 4_000 }
        assertEquals(GeneratorOptions.MAX_LENGTH, SettingsStore(dataStore).generatorOptions.first().length)
        dataStore.edit { it[intPreferencesKey("generator_length")] = 1 }
        assertEquals(GeneratorOptions.MIN_LENGTH, SettingsStore(dataStore).generatorOptions.first().length)
    }

    @Test
    fun `solo se guardan las cinco opciones, nada de contrasenas`() = runBlocking {
        SettingsStore(dataStore).setGeneratorOptions(GeneratorOptions())
        val keys = dataStore.data.first().asMap().keys.map { it.name }.toSet()
        assertEquals(
            setOf("generator_length", "generator_upper", "generator_digits", "generator_symbols", "generator_avoid_ambiguous"),
            keys,
        )
    }

    /** DataStore en memoria (el de archivo no sirve en la JVM de Windows: falla al renombrar). */
    private class InMemoryDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        private val mutex = Mutex()

        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            mutex.withLock { transform(state.value).also { state.value = it } }
    }
}
