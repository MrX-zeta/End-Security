package com.luis.end_security.feature.vault.ui.list

import com.luis.end_security.feature.auth.domain.FakeAuthRepository
import com.luis.end_security.feature.vault.domain.DamagedEntry
import com.luis.end_security.feature.vault.domain.PendingDeletion
import com.luis.end_security.feature.vault.domain.UnsupportedEntry
import com.luis.end_security.feature.vault.ui.FakeDeletionUndo
import com.luis.end_security.feature.vault.ui.FakeVaultRepository
import com.luis.end_security.feature.vault.ui.TestClock
import com.luis.end_security.feature.vault.ui.item
import com.luis.end_security.feature.vault.ui.openedSession
import com.luis.end_security.feature.vault.ui.readable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class VaultListViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val clock = TestClock()
    private val session = openedSession(clock)
    private val repository = FakeVaultRepository()
    private val deletion = FakeDeletionUndo()
    private val auth = FakeAuthRepository().apply { accountName = "luis@correo.com" }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() {
        session.lock()
        Dispatchers.resetMain()
    }

    private fun kotlinx.coroutines.test.TestScope.viewModel(): VaultListViewModel {
        val viewModel = VaultListViewModel(repository, session, deletion, auth)
        backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()
        return viewModel
    }

    private fun names(state: VaultListUiState) = state.rows.map { (it as? VaultRow.Item)?.name ?: it.javaClass.simpleName }

    @Test
    fun `lista alfabetica sin acentos ni mayusculas y cuenta todos`() = runTest(dispatcher) {
        repository.entries.value = listOf(readable("zoom"), readable("Éter"), readable("amazon"))
        val viewModel = viewModel()

        assertEquals(listOf("amazon", "Éter", "zoom"), names(viewModel.state.value))
        assertEquals(3, viewModel.state.value.totalCount)
        assertFalse(viewModel.state.value.isEmpty)
    }

    @Test
    fun `sin elementos es la pantalla vacia`() = runTest(dispatcher) {
        val viewModel = viewModel()
        assertTrue(viewModel.state.value.isEmpty)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `un elemento danado y uno de otra version aparecen al final y no tumban la lista`() = runTest(dispatcher) {
        repository.entries.value = listOf(
            DamagedEntry(UUID.randomUUID(), 1),
            readable("Gmail"),
            UnsupportedEntry(UUID.randomUUID(), 1, 2),
        )
        val state = viewModel().state.value

        assertEquals(3, state.totalCount)
        assertEquals("Gmail", (state.rows[0] as VaultRow.Item).name)
        assertTrue(state.rows[1] is VaultRow.Damaged || state.rows[1] is VaultRow.Newer)
        assertTrue(state.rows[2] is VaultRow.Damaged || state.rows[2] is VaultRow.Newer)
    }

    @Test
    fun `buscar filtra por nombre usuario y sitio sin acentos`() = runTest(dispatcher) {
        repository.entries.value = listOf(
            readable("Netflix", item = item("Netflix", username = "luis@correo.com", url = "netflix.com")),
            readable("Café", item = item("Café", username = "ana")),
            readable("Banco", item = item("Banco", username = "otro", url = "bbva.mx")),
        )
        val viewModel = viewModel()

        viewModel.onQueryChange("CAFE")
        runCurrent()
        assertEquals(listOf("Café"), names(viewModel.state.value))
        assertEquals("el total no depende de la búsqueda", 3, viewModel.state.value.totalCount)

        viewModel.onQueryChange("bbva")
        runCurrent()
        assertEquals(listOf("Banco"), names(viewModel.state.value))

        viewModel.onQueryChange("zzz")
        runCurrent()
        assertTrue(viewModel.state.value.noResults)
        assertFalse(viewModel.state.value.isEmpty)

        viewModel.onQueryChange("")
        runCurrent()
        assertEquals(3, viewModel.state.value.rows.size)
    }

    @Test
    fun `el elemento que espera su Deshacer se oculta y deja de contar`() = runTest(dispatcher) {
        val gmail = readable("Gmail")
        repository.entries.value = listOf(gmail, readable("Amazon"))
        val viewModel = viewModel()

        deletion.schedule(PendingDeletion(gmail.id, "Gmail", gmail.localSeq))
        runCurrent()

        assertEquals(listOf("Amazon"), names(viewModel.state.value))
        assertEquals(1, viewModel.state.value.totalCount)
        assertEquals("Gmail", viewModel.state.value.deletedName)

        viewModel.onUndoDelete()
        runCurrent()
        assertEquals(2, viewModel.state.value.totalCount)
        assertNull(viewModel.state.value.deletedName)
        assertEquals(1, deletion.undone)
    }

    @Test
    fun `la inicial sale de la cuenta y el aviso de codigos de respaldo se lee una vez`() = runTest(dispatcher) {
        auth.recoveryCodesNotice = 2
        val viewModel = viewModel()
        assertEquals("L", viewModel.state.value.accountInitial)
        assertEquals(2, viewModel.state.value.recoveryCodesLeft)
        assertNull("ya se consumió", auth.consumeRecoveryCodesNotice())
    }

    @Test
    fun `bloquear ahora cierra la boveda y borra la busqueda y el aviso`() = runTest(dispatcher) {
        repository.entries.value = listOf(readable("Gmail"))
        auth.recoveryCodesNotice = 1
        val viewModel = viewModel()
        viewModel.onQueryChange("gma")
        runCurrent()

        viewModel.onLockNow()
        runCurrent()

        assertEquals("", viewModel.state.value.query)
        assertNull(viewModel.state.value.recoveryCodesLeft)
    }

    @Test
    fun `tocar un elemento abre su detalle, uno danado no`() = runTest(dispatcher) {
        val gmail = readable("Gmail")
        val bad = DamagedEntry(UUID.randomUUID(), 1)
        repository.entries.value = listOf(gmail, bad)
        val viewModel = viewModel()
        val rows = viewModel.state.value.rows

        viewModel.onItemClick(rows.first { it is VaultRow.Damaged })
        runCurrent()
        assertNull(withTimeoutOrNull(50) { viewModel.effects.first() })

        viewModel.onItemClick(rows.first { it is VaultRow.Item })
        runCurrent()
        assertEquals(VaultListEffect.OpenItem(gmail.id), viewModel.effects.first())
    }

    @Test
    fun `el estado no imprime nombres`() = runTest(dispatcher) {
        repository.entries.value = listOf(readable("Secreto-Banco"))
        val state = viewModel().state.value
        assertFalse(state.toString().contains("Secreto"))
        assertFalse(state.rows.joinToString { it.toString() }.contains("Secreto"))
    }
}
