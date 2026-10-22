package com.luis.end_security.feature.vault.ui.detail

import androidx.lifecycle.SavedStateHandle
import com.luis.end_security.feature.vault.domain.EditedAge
import com.luis.end_security.feature.vault.ui.FakeDeletionUndo
import com.luis.end_security.feature.vault.ui.FakeSecretClipboard
import com.luis.end_security.feature.vault.ui.FakeVaultRepository
import com.luis.end_security.feature.vault.ui.TestClock
import com.luis.end_security.feature.vault.ui.item
import com.luis.end_security.feature.vault.ui.openedSession
import com.luis.end_security.feature.vault.ui.readable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
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
import java.time.Instant
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class ItemDetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val clock = TestClock()
    private val session = openedSession(clock)
    private val clipboard = FakeSecretClipboard()
    private val deletion = FakeDeletionUndo()
    private val changedAt = Instant.ofEpochMilli(clock.now).minusSeconds(3 * 30 * 86_400L)
    private val gmail = readable(
        "Gmail",
        item = item("Gmail", "luis.ruiz@gmail.com", "Tortuga-Violeta-29", "accounts.google.com", "nota", changedAt),
    )
    private val repository = FakeVaultRepository(listOf(gmail))

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() {
        session.lock()
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(id: UUID = gmail.id): ItemDetailViewModel {
        val viewModel = ItemDetailViewModel(
            SavedStateHandle(mapOf(ItemDetailViewModel.ID_ARG to id.toString())),
            repository, clipboard, deletion, session, clock,
        )
        runCurrent()
        return viewModel
    }

    @Test
    fun `muestra el elemento con la contrasena oculta y editada hace 3 meses`() = runTest(dispatcher) {
        val state = viewModel().state.value
        assertEquals(DetailStatus.Found, state.status)
        assertEquals("Gmail", state.name)
        assertEquals("G", state.initial)
        assertEquals("luis.ruiz@gmail.com", state.username)
        assertEquals("accounts.google.com", state.site)
        assertTrue(state.siteOpenable)
        assertEquals(EditedAge.Months(3), state.edited)
        assertEquals(18, state.passwordLength)
        assertNull("oculta: el estado no lleva la contraseña", state.shownPassword)
        assertFalse(state.toString().contains("Tortuga"))
    }

    @Test
    fun `el ojo muestra y oculta la contrasena y al salir de primer plano se oculta`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.onTogglePassword()
        assertEquals("Tortuga-Violeta-29", viewModel.state.value.shownPassword)
        viewModel.onTogglePassword()
        assertNull(viewModel.state.value.shownPassword)

        viewModel.onTogglePassword()
        viewModel.onHidePassword()
        assertNull(viewModel.state.value.shownPassword)
    }

    @Test
    fun `copiar la contrasena la manda al portapapeles y el aviso dura 2,5 s`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.onCopyPassword()
        runCurrent()
        assertEquals(listOf("Tortuga-Violeta-29"), clipboard.copied)
        assertEquals(CopiedKind.Password, viewModel.state.value.copied?.kind)
        assertEquals(30, viewModel.state.value.copied?.seconds)

        advanceTimeBy(ItemDetailViewModel.COPIED_NOTICE_MILLIS - 1)
        runCurrent()
        assertTrue(viewModel.state.value.copied != null)
        advanceTimeBy(1)
        runCurrent()
        assertNull(viewModel.state.value.copied)
    }

    @Test
    fun `copiar el usuario avisa que se copio el usuario`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onCopyUsername()
        runCurrent()
        assertEquals(listOf("luis.ruiz@gmail.com"), clipboard.copied)
        assertEquals(CopiedKind.Username, viewModel.state.value.copied?.kind)
    }

    @Test
    fun `abrir el sitio manda una direccion https y un esquema peligroso no se abre`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onOpenSite()
        runCurrent()
        assertEquals(ItemDetailEffect.OpenSite("https://accounts.google.com"), viewModel.effects.first())

        val evil = readable("Malo", item = item("Malo", url = "javascript:alert(1)"))
        repository.entries.value = listOf(evil)
        val evilViewModel = viewModel(evil.id)
        assertFalse(evilViewModel.state.value.siteOpenable)
        evilViewModel.onOpenSite()
        runCurrent()
        assertNull(withTimeoutOrNull(50) { evilViewModel.effects.first() })
    }

    @Test
    fun `eliminar pide confirmacion, programa el borrado con Deshacer y vuelve a la lista`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.onDeleteClick()
        assertTrue(viewModel.state.value.showDeleteSheet)
        viewModel.onDeleteDismiss()
        assertFalse(viewModel.state.value.showDeleteSheet)
        assertTrue("cancelar no programa nada", deletion.scheduled.isEmpty())

        viewModel.onDeleteClick()
        viewModel.onDeleteConfirm()
        runCurrent()
        assertEquals(1, deletion.scheduled.size)
        assertEquals(gmail.id, deletion.scheduled[0].id)
        assertEquals("Gmail", deletion.scheduled[0].name)
        assertEquals(gmail.localSeq, deletion.scheduled[0].localSeq)
        assertEquals(ItemDetailEffect.Close, viewModel.effects.first())
    }

    @Test
    fun `confirmar dos veces programa un solo borrado y cierra una sola vez`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onDeleteClick()
        viewModel.onDeleteConfirm()
        viewModel.onDeleteConfirm()
        runCurrent()

        assertEquals(1, deletion.scheduled.size)
        assertEquals(ItemDetailEffect.Close, viewModel.effects.first())
        assertNull(withTimeoutOrNull(50) { viewModel.effects.first() })
    }

    @Test
    fun `el efecto de abrir el sitio no imprime la direccion`() {
        assertFalse(ItemDetailEffect.OpenSite("https://secreto.example").toString().contains("secreto"))
    }

    @Test
    fun `si el elemento desaparece sale a la lista`() = runTest(dispatcher) {
        val viewModel = viewModel()
        repository.entries.value = emptyList()
        runCurrent()
        assertEquals(DetailStatus.Gone, viewModel.state.value.status)
        assertEquals(ItemDetailEffect.Close, viewModel.effects.first())
    }

    @Test
    fun `al bloquearse se olvida lo descifrado y no navega por su cuenta`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onTogglePassword()

        session.lock()
        repository.entries.value = emptyList() // el repositorio real deja de entregar elementos
        runCurrent()

        val state = viewModel.state.value
        assertEquals(DetailStatus.Gone, state.status)
        assertEquals("", state.name)
        assertNull(state.shownPassword)
        assertNull("LockGuard es quien navega", withTimeoutOrNull(50) { viewModel.effects.first() })
        viewModel.onCopyPassword()
        runCurrent()
        assertTrue("ya no hay nada que copiar", clipboard.copied.isEmpty())
    }

    @Test
    fun `un cambio hecho en Editar se ve al volver`() = runTest(dispatcher) {
        val viewModel = viewModel()
        repository.entries.value = listOf(
            readable("Gmail personal", gmail.id, 2, item("Gmail personal", "otro@correo.com", "Nueva-Contrasena-44")),
        )
        runCurrent()
        assertEquals("Gmail personal", viewModel.state.value.name)
        assertEquals("otro@correo.com", viewModel.state.value.username)
        assertEquals(19, viewModel.state.value.passwordLength)
    }
}
