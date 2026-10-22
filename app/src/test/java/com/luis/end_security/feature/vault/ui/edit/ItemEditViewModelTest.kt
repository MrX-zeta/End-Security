package com.luis.end_security.feature.vault.ui.edit

import androidx.lifecycle.SavedStateHandle
import com.luis.end_security.feature.auth.domain.PasswordStrengthEstimator
import com.luis.end_security.feature.vault.domain.ItemDraft
import com.luis.end_security.feature.vault.domain.SaveResult
import com.luis.end_security.feature.vault.ui.FakeVaultRepository
import com.luis.end_security.feature.vault.ui.item
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
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class ItemEditViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val estimator = object : PasswordStrengthEstimator {
        var next = 3
        var calls = 0

        override fun score(password: CharSequence): Int {
            calls++
            return next
        }
    }
    private val gmail = readable("Gmail", localSeq = 4, item = item("Gmail", "ana@correo.com", "Tortuga-29", "google.com", "nota"))
    private val repository = FakeVaultRepository(listOf(gmail))

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.newForm(): ItemEditViewModel {
        val viewModel = ItemEditViewModel(SavedStateHandle(), repository, estimator, dispatcher)
        runCurrent()
        return viewModel
    }

    private fun TestScope.editForm(id: UUID = gmail.id): ItemEditViewModel {
        val viewModel = ItemEditViewModel(SavedStateHandle(mapOf(ItemEditViewModel.ID_ARG to id.toString())), repository, estimator, dispatcher)
        // La pantalla pide los valores al armar sus campos.
        viewModel.requestPrefill()
        runCurrent()
        return viewModel
    }

    private fun draft(
        name: String = "Netflix",
        username: String = "luis",
        password: String = "Contrasena-Larga-31",
        url: String? = null,
        notes: String? = null,
    ) = ItemDraft(name, username, password, url, notes)

    @Test
    fun `nueva contrasena guarda con el borrador completo y avisa que se guardo`() = runTest(dispatcher) {
        val viewModel = newForm()
        assertTrue(viewModel.state.value.isNew)
        assertEquals(EditStatus.Ready, viewModel.state.value.status)

        viewModel.onSave(draft(url = "netflix.com"))
        runCurrent()

        assertEquals(1, repository.created.size)
        assertEquals("Netflix", repository.created[0].name)
        assertEquals("Contrasena-Larga-31", repository.created[0].password)
        assertEquals(ItemEditEffect.Saved, viewModel.effects.first())
        assertTrue("tras guardar sigue «guardando» hasta salir", viewModel.state.value.isSaving)
    }

    @Test
    fun `sin nombre no guarda y marca el error hasta que se escribe`() = runTest(dispatcher) {
        val viewModel = newForm()
        viewModel.onSave(draft(name = "   "))
        runCurrent()

        assertTrue(viewModel.state.value.nameError)
        assertTrue(repository.created.isEmpty())

        viewModel.onNameChanged()
        assertFalse(viewModel.state.value.nameError)
    }

    @Test
    fun `editar carga los datos una vez y suelta la contrasena del estado`() = runTest(dispatcher) {
        val viewModel = editForm()
        val state = viewModel.state.value
        assertFalse(state.isNew)
        assertEquals(EditStatus.Ready, state.status)
        assertEquals("Gmail", state.prefill?.name)
        assertEquals("Tortuga-10".length, state.prefill?.password?.length)

        viewModel.onPrefillApplied()
        assertNull("la contraseña ya no está en el estado", viewModel.state.value.prefill)
        assertFalse(viewModel.state.value.toString().contains("Tortuga"))
    }

    @Test
    fun `editar guarda con el local_seq que se leyo`() = runTest(dispatcher) {
        val viewModel = editForm()
        viewModel.onSave(draft(name = "Gmail personal"))
        runCurrent()

        assertEquals(1, repository.updated.size)
        assertEquals(gmail.id, repository.updated[0].first)
        assertEquals(4L, repository.updated[0].second)
        assertEquals(ItemEditEffect.Saved, viewModel.effects.first())
    }

    @Test
    fun `si otra escritura se adelanto muestra el conflicto y no sale`() = runTest(dispatcher) {
        repository.saveResult = SaveResult.Conflict
        val viewModel = editForm()
        viewModel.onSave(draft())
        runCurrent()

        assertEquals(SaveError.Conflict, viewModel.state.value.error)
        assertNull(withTimeoutOrNull(50) { viewModel.effects.first() })
        assertFalse(viewModel.state.value.isSaving)
    }

    @Test
    fun `un elemento que ya no existe cierra la pantalla`() = runTest(dispatcher) {
        val viewModel = editForm(UUID.randomUUID())
        assertEquals(EditStatus.Gone, viewModel.state.value.status)
        assertEquals(ItemEditEffect.Close, viewModel.effects.first())
    }

    @Test
    fun `salir sin cambios cierra y con cambios pide confirmacion`() = runTest(dispatcher) {
        val viewModel = editForm()

        viewModel.onBack(hasChanges = true)
        assertTrue(viewModel.state.value.showDiscardSheet)

        viewModel.onDiscardDismiss()
        assertFalse(viewModel.state.value.showDiscardSheet)

        viewModel.onBack(hasChanges = false)
        runCurrent()
        assertEquals(ItemEditEffect.Close, viewModel.effects.first())
    }

    @Test
    fun `descartar cierra una sola vez aunque se toque dos veces`() = runTest(dispatcher) {
        val viewModel = editForm()
        viewModel.onBack(hasChanges = true)
        viewModel.onDiscardConfirm()
        viewModel.onDiscardConfirm()
        runCurrent()

        assertEquals(ItemEditEffect.Close, viewModel.effects.first())
        assertNull("un segundo Close sacaría también la pantalla de abajo", withTimeoutOrNull(50) { viewModel.effects.first() })
    }

    @Test
    fun `dos toques en Guardar no crean un duplicado`() = runTest(dispatcher) {
        val viewModel = newForm()
        viewModel.onSave(draft())
        viewModel.onSave(draft())
        runCurrent()

        assertEquals(1, repository.created.size)
        assertEquals(ItemEditEffect.Saved, viewModel.effects.first())
        assertTrue("sigue guardando: no se vuelve a habilitar", viewModel.state.value.isSaving)
    }

    @Test
    fun `al recrearse la actividad los valores se piden otra vez y el ViewModel no los retiene`() = runTest(dispatcher) {
        val viewModel = editForm()
        viewModel.onPrefillApplied()
        assertNull(viewModel.state.value.prefill)

        // Girar la pantalla: los campos nuevos nacen vacíos y piden los valores de nuevo.
        viewModel.requestPrefill()
        runCurrent()
        assertEquals("Gmail", viewModel.state.value.prefill?.name)
        assertEquals("Tortuga-10".length, viewModel.state.value.prefill?.password?.length)
    }

    @Test
    fun `los valores iniciales distinguen lo que cambio`() {
        val base = FormValues("Gmail", "ana", "clave", "google.com", "nota")
        assertFalse(base.differsFrom(ItemDraft("Gmail", "ana", "clave", "google.com", "nota")))
        assertFalse("url y notas vacías equivalen a null", FormValues("a", "b", "c", "", "").differsFrom(ItemDraft("a", "b", "c", null, null)))
        assertTrue(base.differsFrom(ItemDraft("Gmail 2", "ana", "clave", "google.com", "nota")))
        assertTrue(base.differsFrom(ItemDraft("Gmail", "ana", "otra", "google.com", "nota")))
        assertTrue(base.differsFrom(ItemDraft("Gmail", "ana", "clave", null, "nota")))
        assertFalse(FormValues.EMPTY.differsFrom(ItemDraft("", "", "", null, null)))
        assertTrue(FormValues.EMPTY.differsFrom(ItemDraft("Algo", "", "", null, null)))
    }

    @Test
    fun `el medidor espera a que se deje de escribir, calcula fuera del hilo y vacio lo quita`() = runTest(dispatcher) {
        val viewModel = newForm()
        estimator.next = 4

        viewModel.onPasswordChanged("abc")
        viewModel.onPasswordChanged("abcdef")
        advanceTimeBy(ItemEditViewModel.STRENGTH_DEBOUNCE_MILLIS - 1)
        runCurrent()
        assertNull(viewModel.state.value.strengthScore)
        assertEquals(0, estimator.calls)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(4, viewModel.state.value.strengthScore)
        assertEquals("solo calculó la última", 1, estimator.calls)

        viewModel.onPasswordChanged("")
        assertNull(viewModel.state.value.strengthScore)
    }
}
