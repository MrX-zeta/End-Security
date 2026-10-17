package com.luis.end_security.feature.auth.ui.login

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LoginViewModelTest {

    private lateinit var viewModel: LoginViewModel

    @Before
    fun setUp() {
        viewModel = LoginViewModel()
    }

    private val state get() = viewModel.state.value

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
