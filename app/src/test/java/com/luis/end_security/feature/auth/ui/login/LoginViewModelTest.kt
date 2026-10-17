package com.luis.end_security.feature.auth.ui.login

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

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)
    private lateinit var viewModel: LoginViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel = LoginViewModel(dispatcher.scheduler.timeSource)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val state get() = viewModel.state.value

    private fun fillValid() {
        viewModel.onAction(LoginAction.EmailChanged("ana.lopez@correo.com"))
        viewModel.onAction(LoginAction.PasswordEmptinessChanged(isEmpty = false))
    }

    @Test
    fun `entrar con datos validos navega a la verificacion en dos pasos`() = scope.runTest {
        fillValid()
        viewModel.onAction(LoginAction.Submit)
        assertEquals(LoginEffect.NavigateToTwoFactor, viewModel.effects.first())
    }

    @Test
    fun `entrar sin datos validos no navega`() = scope.runTest {
        viewModel.onAction(LoginAction.Submit)
        runCurrent()
        assertNull(withTimeoutOrNull(100) { viewModel.effects.first() })
    }

    @Test
    fun `el bloqueo por intentos deshabilita entrar y cuenta hacia atras`() = scope.runTest {
        fillValid()
        viewModel.onLockedOut(minutes = 14)
        runCurrent()
        assertTrue(state.isLockedOut)
        assertEquals(14 * 60, state.lockoutSecondsLeft)
        assertFalse(state.canSubmit)

        advanceTimeBy(14 * 60_000L)
        runCurrent()
        assertFalse(state.isLockedOut)
        assertTrue("al terminar se puede entrar otra vez", state.canSubmit)
    }

    @Test
    fun `los minutos del bloqueo se redondean hacia arriba`() {
        assertEquals(1, com.luis.end_security.feature.auth.ui.lockoutMinutesLeft(59))
        assertEquals(1, com.luis.end_security.feature.auth.ui.lockoutMinutesLeft(60))
        assertEquals(2, com.luis.end_security.feature.auth.ui.lockoutMinutesLeft(61))
        assertEquals(0, com.luis.end_security.feature.auth.ui.lockoutMinutesLeft(0))
    }

    @Test
    fun `empieza vacio y sin poder entrar`() {
        assertEquals("", state.email)
        assertFalse(state.isEmailValid)
        assertFalse(state.canSubmit)
        assertFalse(state.isLoading)
        assertNull(state.error)
    }

    @Test
    fun `un correo con forma valida se marca como valido`() {
        viewModel.onAction(LoginAction.EmailChanged("ana.lopez@correo.com"))
        assertEquals("ana.lopez@correo.com", state.email)
        assertTrue(state.isEmailValid)
    }

    @Test
    fun `un correo sin arroba o sin dominio no es valido`() {
        listOf("ana", "ana@", "ana@correo", "@correo.com", "ana lopez@correo.com").forEach { email ->
            viewModel.onAction(LoginAction.EmailChanged(email))
            assertFalse("«$email» no debería ser válido", state.isEmailValid)
        }
    }

    @Test
    fun `entrar se habilita solo con correo valido y contrasena no vacia`() {
        viewModel.onAction(LoginAction.EmailChanged("ana.lopez@correo.com"))
        assertFalse(state.canSubmit)

        viewModel.onAction(LoginAction.PasswordEmptinessChanged(isEmpty = false))
        assertTrue(state.canSubmit)
    }

    @Test
    fun `entrar se deshabilita si se borra la contrasena o el correo deja de ser valido`() {
        viewModel.onAction(LoginAction.EmailChanged("ana.lopez@correo.com"))
        viewModel.onAction(LoginAction.PasswordEmptinessChanged(isEmpty = false))

        viewModel.onAction(LoginAction.PasswordEmptinessChanged(isEmpty = true))
        assertFalse(state.canSubmit)

        viewModel.onAction(LoginAction.PasswordEmptinessChanged(isEmpty = false))
        viewModel.onAction(LoginAction.EmailChanged("ana.lopez@"))
        assertFalse(state.canSubmit)
    }

    @Test
    fun `con contrasena pero sin correo valido no se puede entrar`() {
        viewModel.onAction(LoginAction.PasswordEmptinessChanged(isEmpty = false))
        viewModel.onAction(LoginAction.EmailChanged("ana"))
        assertFalse(state.canSubmit)
    }

    @Test
    fun `entrar sin backend no cambia el estado`() {
        viewModel.onAction(LoginAction.EmailChanged("ana.lopez@correo.com"))
        viewModel.onAction(LoginAction.PasswordEmptinessChanged(isEmpty = false))
        val before = state

        viewModel.onAction(LoginAction.Submit)

        assertEquals(before, state)
    }
}
