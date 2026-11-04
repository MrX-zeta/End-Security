package com.luis.end_security.core.datastore

import com.luis.end_security.core.crypto.InMemoryPreferencesDataStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** OP-21: el bloqueo automático se guarda saneado (`AutoLocker` ya reacciona al `Flow`: ver `AutoLockerTest`). */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsStoreAutolockTest {

    @Test
    fun `por omision son 5 minutos`() = kotlinx.coroutines.test.runTest {
        assertEquals(5, SettingsStore(InMemoryPreferencesDataStore()).autolockMinutes.first())
    }

    @Test
    fun `cada opcion permitida se guarda y se lee igual`() = runTest {
        val store = SettingsStore(InMemoryPreferencesDataStore())
        for (minutes in listOf(0, 1, 5, 15, 30)) {
            store.setAutolockMinutes(minutes)
            assertEquals(minutes, store.autolockMinutes.first())
        }
    }

    @Test
    fun `un valor invalido se sanea al valor por omision`() = runTest {
        val store = SettingsStore(InMemoryPreferencesDataStore())
        for (bad in listOf(-1, 2, 7, 60, 9_999)) {
            store.setAutolockMinutes(15)
            store.setAutolockMinutes(bad)
            assertEquals("valor $bad", 5, store.autolockMinutes.first())
        }
    }
}
