package com.luis.end_security.feature.auth.ui.login

import com.luis.end_security.core.common.ApiError
import com.luis.end_security.core.common.ApiResult
import com.luis.end_security.feature.auth.domain.AuthStep
import com.luis.end_security.feature.auth.domain.FakeAuthRepository
import kotlinx.coroutines.CompletableDeferred
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
    private val auth = FakeAuthRepository()
    private lateinit var viewModel: LoginViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel = LoginViewModel(dispatcher.scheduler.timeSource, auth)
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

    private fun TestScope.submit() {
        viewModel.onAction(LoginAction.Submit("contraseña"))
        runCurrent()
    }

    @Test
    fun `entrar manda el correo y la contrasena del campo`() = scope.runTest {
        fillValid()
        submit()
        assertEquals(listOf("login:ana.lopez@correo.com"), auth.calls)
        assertEquals(listOf("contraseña"), auth.passwords)
    }

    @Test
    fun `con la boveda abierta navega a la boveda`() = scope.runTest {
        fillValid()
        submit()
        assertEquals(LoginEffect.NavigateToVault, viewModel.effects.first())
    }

    @Test
    fun `con 2FA navega a la verificacion en dos pasos`() = scope.runTest {
        auth.loginResult = ApiResult.Success(AuthStep.TwoFactorRequired)
        fillValid()
        submit()
        assertEquals(LoginEffect.NavigateToTwoFactor, viewModel.effects.first())
    }

    @Test
    fun `con el correo sin verificar navega a verificarlo`() = scope.runTest {
        auth.loginResult = ApiResult.Success(AuthStep.EmailNotVerified("ana.lopez@correo.com"))
        fillValid()
        submit()
        assertEquals(LoginEffect.NavigateToVerifyEmail("ana.lopez@correo.com"), viewModel.effects.first())
    }

    @Test
    fun `credenciales incorrectas muestran el error y se quita al escribir`() = scope.runTest {
        auth.loginResult = ApiResult.Failure(ApiError.InvalidCredentials)
        fillValid()
        submit()
        assertEquals(LoginError.InvalidCredentials, state.error)
        assertFalse(state.isLoading)
        viewModel.onAction(LoginAction.PasswordEmptinessChanged(isEmpty = false))
        assertNull(state.error)
    }

    @Test
    fun `sin red muestra el error generico`() = scope.runTest {
        auth.loginResult = ApiResult.Failure(ApiError.NetworkUnavailable)
        fillValid()
        submit()
        assertEquals(LoginError.Generic, state.error)
    }

    @Test
    fun `demasiados intentos en el servidor bloquean con los minutos redondeados`() = scope.runTest {
        auth.loginResult = ApiResult.Failure(ApiError.RateLimited(retryAfterSeconds = 61))
        fillValid()
        submit()
        assertEquals(2 * 60, state.lockoutSecondsLeft)
        assertFalse(state.canSubmit)
    }

    @Test
    fun `mientras entra muestra carga y no acepta otro envio`() = scope.runTest {
        auth.gate = CompletableDeferred()
        fillValid()
        submit()
        assertTrue(state.isLoading)
        assertFalse(state.canSubmit)
        submit()
        assertEquals(1, auth.calls.size)
        auth.gate!!.complete(Unit)
        runCurrent()
        assertFalse(state.isLoading)
    }

    @Test
    fun `entrar sin datos validos no llama al servidor ni navega`() = scope.runTest {
        submit()
        assertTrue(auth.calls.isEmpty())
        assertNull(withTimeoutOrNull(100) { viewModel.effects.first() })
    }

    @Test
    fun `la accion de enviar no imprime la contrasena`() {
        assertFalse(LoginAction.Submit("secreta").toString().contains("secreta"))
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
}
