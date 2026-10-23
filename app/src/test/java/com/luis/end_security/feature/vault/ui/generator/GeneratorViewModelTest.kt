package com.luis.end_security.feature.vault.ui.generator

import androidx.lifecycle.SavedStateHandle
import com.luis.end_security.core.crypto.GeneratorOptions
import com.luis.end_security.core.crypto.PasswordGenerator
import com.luis.end_security.feature.auth.domain.PasswordStrengthEstimator
import com.luis.end_security.feature.vault.domain.GeneratorRepository
import com.luis.end_security.feature.vault.ui.FakePasswordHandoff
import com.luis.end_security.feature.vault.ui.FakeSecretClipboard
import com.luis.end_security.feature.vault.ui.TestClock
import com.luis.end_security.feature.vault.ui.openedSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FakeGeneratorRepository(initial: GeneratorOptions = GeneratorOptions()) : GeneratorRepository {
    val stored = MutableStateFlow(initial)
    val saved = mutableListOf<GeneratorOptions>()
    private val generator = PasswordGenerator()

    override val options: Flow<GeneratorOptions> = stored

    override suspend fun saveOptions(options: GeneratorOptions) {
        saved += options
        stored.value = options
    }

    override fun generate(options: GeneratorOptions): CharArray = generator.generate(options)
}

@OptIn(ExperimentalCoroutinesApi::class)
class GeneratorViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val session = openedSession(TestClock())
    private val clipboard = FakeSecretClipboard()
    private val handoff = FakePasswordHandoff()
    private val estimator = object : PasswordStrengthEstimator {
        var calls = 0

        override fun score(password: CharSequence): Int {
            calls++
            return 4
        }
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() {
        session.lock()
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(repository: GeneratorRepository = FakeGeneratorRepository()): GeneratorViewModel {
        val viewModel = GeneratorViewModel(repository, estimator, clipboard, handoff, session, dispatcher)
        runCurrent()
        return viewModel
    }

    @Test
    fun `al abrir genera una contrasena con las opciones recordadas`() = runTest(dispatcher) {
        val repository = FakeGeneratorRepository(GeneratorOptions(24, uppercase = false, digits = true, symbols = false, avoidAmbiguous = true))
        val state = viewModel(repository).state.value

        assertEquals(GeneratorOptions(24, uppercase = false, digits = true, symbols = false, avoidAmbiguous = true), state.options)
        assertEquals(24, state.password.length)
        assertTrue(state.password.none { it in "ABCDEFGHIJKLMNOPQRSTUVWXYZ" })
        assertTrue("nada generado en un ajuste apagado", state.password.none { it in PasswordGenerator.SYMBOLS })
        assertEquals(4, state.strengthScore)
    }

    @Test
    fun `con los valores por omision son 16, mayusculas, numeros y simbolos`() = runTest(dispatcher) {
        val state = viewModel().state.value
        assertEquals(16, state.options.length)
        assertTrue(state.options.uppercase && state.options.digits && state.options.symbols)
        assertFalse(state.options.avoidAmbiguous)
    }

    @Test
    fun `los interruptores y la longitud se recuerdan y regeneran`() = runTest(dispatcher) {
        val repository = FakeGeneratorRepository()
        val viewModel = viewModel(repository)
        val first = viewModel.state.value.password

        viewModel.onToggle(GeneratorToggle.Symbols)
        runCurrent()
        assertFalse(repository.saved.last().symbols)
        assertTrue(viewModel.state.value.password.none { it in PasswordGenerator.SYMBOLS })
        assertNotEquals(first, viewModel.state.value.password)

        viewModel.onLengthChange(40)
        assertEquals(40, viewModel.state.value.password.length)
        viewModel.onLengthChangeFinished()
        runCurrent()
        assertEquals(40, repository.saved.last().length)

        viewModel.onToggle(GeneratorToggle.AvoidAmbiguous)
        runCurrent()
        assertTrue(repository.saved.last().avoidAmbiguous)

        // Otro ViewModel (otra apertura) arranca con lo recordado.
        val reopened = viewModel(repository).state.value
        assertEquals(40, reopened.options.length)
        assertFalse(reopened.options.symbols)
        assertTrue(reopened.options.avoidAmbiguous)
    }

    @Test
    fun `la longitud se mantiene entre 8 y 64`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onLengthChange(3)
        assertEquals(8, viewModel.state.value.options.length)
        viewModel.onLengthChange(900)
        assertEquals(64, viewModel.state.value.options.length)
    }

    @Test
    fun `el dado genera otra y sube la generacion`() = runTest(dispatcher) {
        val viewModel = viewModel()
        val before = viewModel.state.value

        viewModel.onRegenerate()
        runCurrent()

        assertNotEquals(before.password, viewModel.state.value.password)
        assertEquals(before.generation + 1, viewModel.state.value.generation)
    }

    @Test
    fun `copiar usa el portapapeles seguro y el aviso dura 2,5 segundos`() = runTest(dispatcher) {
        val viewModel = viewModel()
        val password = viewModel.state.value.password

        viewModel.onCopy()
        runCurrent()
        assertEquals(listOf(password), clipboard.copied)
        assertEquals(30, viewModel.state.value.copiedSeconds)

        advanceTimeBy(GeneratorViewModel.COPIED_NOTICE_MILLIS + 1)
        runCurrent()
        assertNull(viewModel.state.value.copiedSeconds)
    }

    @Test
    fun `Guardar en la boveda entrega la contrasena una sola vez y abre Nueva`() = runTest(dispatcher) {
        val viewModel = viewModel()
        val password = viewModel.state.value.password

        viewModel.onSave()
        runCurrent()

        assertEquals(GeneratorEffect.OpenNewWithPassword, viewModel.effects.first())
        assertEquals(password, String(handoff.take()!!))
        assertNull("una sola vez", handoff.take())
    }

    @Test
    fun `Usar esta contrasena devuelve la generada y no la imprime`() = runTest(dispatcher) {
        val viewModel = viewModel()
        val password = viewModel.state.value.password

        viewModel.onUse()
        runCurrent()

        val effect = viewModel.effects.first() as GeneratorEffect.Use
        assertEquals(password, effect.password)
        assertFalse(effect.toString().contains(password))
    }

    @Test
    fun `salir de primer plano borra la contrasena y al volver se genera otra`() = runTest(dispatcher) {
        val viewModel = viewModel()
        val password = viewModel.state.value.password

        viewModel.onHide()
        assertEquals("", viewModel.state.value.password)
        assertNull(viewModel.state.value.strengthScore)
        viewModel.onCopy()
        runCurrent()
        assertTrue("sin contraseña no se copia nada", clipboard.copied.isEmpty())

        viewModel.ensureGenerated()
        runCurrent()
        assertEquals(16, viewModel.state.value.password.length)
        assertNotEquals(password, viewModel.state.value.password)
    }

    @Test
    fun `al bloquear se borra y no se genera con la boveda bloqueada`() = runTest(dispatcher) {
        val viewModel = viewModel()

        session.lock()
        runCurrent()

        assertEquals("", viewModel.state.value.password)
        viewModel.onRegenerate()
        runCurrent()
        assertEquals("con la bóveda bloqueada no se genera", "", viewModel.state.value.password)
        viewModel.onSave()
        runCurrent()
        assertNull(withTimeoutOrNull(50) { viewModel.effects.first() })
        assertFalse(handoff.hasPending)
    }

    @Test
    fun `la contrasena no va al estado guardable ni se imprime`() = runTest(dispatcher) {
        val viewModel = viewModel()
        val state = viewModel.state.value

        assertFalse(state.toString().contains(state.password))
        // El ViewModel no recibe SavedStateHandle: nada de lo generado se puede guardar con el estado de la pantalla.
        val parameters = GeneratorViewModel::class.java.constructors.single().parameterTypes
        assertTrue(parameters.none { it == SavedStateHandle::class.java })
    }
}
