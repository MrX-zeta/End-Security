package com.luis.end_security.feature.auth.ui.onboarding

import com.luis.end_security.feature.auth.domain.OnboardingRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
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

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: FakeOnboardingRepository
    private lateinit var viewModel: OnboardingViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeOnboardingRepository()
        viewModel = OnboardingViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `empieza en la primera de tres paginas`() {
        val state = viewModel.state.value
        assertEquals(0, state.currentPage)
        assertEquals(3, state.pageCount)
        assertFalse(state.isLastPage)
    }

    @Test
    fun `siguiente avanza hasta la ultima pagina y no la rebasa`() {
        viewModel.onAction(OnboardingAction.Next)
        assertEquals(1, viewModel.state.value.currentPage)

        viewModel.onAction(OnboardingAction.Next)
        assertEquals(2, viewModel.state.value.currentPage)
        assertTrue(viewModel.state.value.isLastPage)

        viewModel.onAction(OnboardingAction.Next)
        assertEquals(2, viewModel.state.value.currentPage)
    }

    @Test
    fun `atras regresa una pagina y no baja de la primera`() {
        viewModel.onAction(OnboardingAction.Next)
        viewModel.onAction(OnboardingAction.Previous)
        assertEquals(0, viewModel.state.value.currentPage)

        viewModel.onAction(OnboardingAction.Previous)
        assertEquals(0, viewModel.state.value.currentPage)
    }

    @Test
    fun `deslizar actualiza la pagina dentro del rango`() {
        viewModel.onAction(OnboardingAction.PageSettled(2))
        assertEquals(2, viewModel.state.value.currentPage)

        viewModel.onAction(OnboardingAction.PageSettled(7))
        assertEquals(2, viewModel.state.value.currentPage)
    }

    @Test
    fun `no se guarda nada mientras solo se navega entre paginas`() {
        viewModel.onAction(OnboardingAction.Next)
        viewModel.onAction(OnboardingAction.Next)
        assertEquals(0, repository.markCompletedCalls)
    }

    @Test
    fun `comenzar guarda que ya se vio y navega a crear cuenta`() = runTest(dispatcher) {
        viewModel.onAction(OnboardingAction.PageSettled(2))
        viewModel.onAction(OnboardingAction.Start)

        assertEquals(1, repository.markCompletedCalls)
        assertTrue(repository.isCompleted.first())
        assertEquals(OnboardingEffect.NavigateToSignUp, viewModel.effects.first())
    }

    @Test
    fun `ya tengo una cuenta guarda y navega a iniciar sesion`() = runTest(dispatcher) {
        viewModel.onAction(OnboardingAction.PageSettled(2))
        viewModel.onAction(OnboardingAction.AlreadyHaveAccount)

        assertEquals(1, repository.markCompletedCalls)
        assertEquals(OnboardingEffect.NavigateToLogin, viewModel.effects.first())
    }

    @Test
    fun `omitir guarda y navega a iniciar sesion`() = runTest(dispatcher) {
        viewModel.onAction(OnboardingAction.Next) // página 2
        viewModel.onAction(OnboardingAction.Skip)

        assertEquals(1, repository.markCompletedCalls)
        assertTrue(repository.isCompleted.first())
        assertEquals(OnboardingEffect.NavigateToLogin, viewModel.effects.first())
    }

    @Test
    fun `un doble toque mientras se guarda solo guarda y navega una vez`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        repository.gate = gate // la escritura queda en curso hasta abrir la compuerta

        viewModel.onAction(OnboardingAction.Start)
        viewModel.onAction(OnboardingAction.Skip)
        gate.complete(Unit)

        assertEquals(1, repository.markCompletedCalls)
        assertEquals(OnboardingEffect.NavigateToSignUp, viewModel.effects.first())
        assertNull(withTimeoutOrNull(100) { viewModel.effects.first() })
    }

    @Test
    fun `al volver desde crear cuenta se puede terminar otra vez`() = runTest(dispatcher) {
        viewModel.onAction(OnboardingAction.Start)
        assertEquals(OnboardingEffect.NavigateToSignUp, viewModel.effects.first())
        assertFalse(viewModel.state.value.isFinishing)

        viewModel.onAction(OnboardingAction.AlreadyHaveAccount)
        assertEquals(OnboardingEffect.NavigateToLogin, viewModel.effects.first())
    }

    private class FakeOnboardingRepository : OnboardingRepository {
        private val completed = MutableStateFlow(false)
        var markCompletedCalls = 0
            private set

        override val isCompleted: Flow<Boolean> = completed

        /** Si no es null, la escritura espera a que se complete (simula DataStore). */
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun markCompleted() {
            markCompletedCalls++
            gate?.await()
            completed.value = true
        }
    }
}
