package com.luis.end_security.feature.auth.ui.twofactor

import com.luis.end_security.core.common.ApiError
import com.luis.end_security.core.common.ApiResult
import com.luis.end_security.feature.auth.domain.FakeAuthRepository
import com.luis.end_security.feature.auth.domain.SecondFactorResult
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TwoFactorViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)
    private val auth = FakeAuthRepository()
    private lateinit var viewModel: TwoFactorViewModel

    private val state get() = viewModel.state.value

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel = TwoFactorViewModel(dispatcher.scheduler.timeSource, auth)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `continuar se habilita solo con 6 digitos`() {
        viewModel.onAction(TwoFactorAction.CodeChanged("27091"))
        assertFalse(state.canSubmit)
        viewModel.onAction(TwoFactorAction.CodeChanged("270915"))
        assertTrue(state.canSubmit)
    }

    @Test
    fun `codigo incorrecto vacia las casillas y muestra el error`() {
        viewModel.onAction(TwoFactorAction.CodeChanged("270915"))
        viewModel.onResult(SecondFactorResult.Rejected)
        assertTrue(state.wrongCode)
        assertEquals("", state.code)
        assertFalse(state.canSubmit)
    }

    @Test
    fun `al escribir otra vez se quita el error`() {
        viewModel.onResult(SecondFactorResult.Rejected)
        viewModel.onAction(TwoFactorAction.CodeChanged("2"))
        assertFalse(state.wrongCode)
    }

    @Test
    fun `el bloqueo deshabilita continuar y cuenta hacia atras`() = scope.runTest {
        viewModel.onResult(SecondFactorResult.LockedOut(minutes = 15))
        runCurrent()
        assertTrue(state.isLockedOut)
        assertEquals(15 * 60, state.lockoutSecondsLeft)

        viewModel.onAction(TwoFactorAction.CodeChanged("270915"))
        assertFalse("bloqueado", state.canSubmit)

        advanceTimeBy(5 * 60_000L)
        runCurrent()
        assertEquals(10 * 60, state.lockoutSecondsLeft)
    }

    @Test
    fun `al terminar el bloqueo se puede continuar`() = scope.runTest {
        viewModel.onResult(SecondFactorResult.LockedOut(minutes = 1))
        viewModel.onAction(TwoFactorAction.CodeChanged("270915"))
        advanceTimeBy(60_000L)
        runCurrent()
        assertFalse(state.isLockedOut)
        assertTrue(state.canSubmit)
    }

    @Test
    fun `el estado no imprime el codigo`() {
        viewModel.onAction(TwoFactorAction.CodeChanged("270915"))
        assertFalse(state.toString().contains("270915"))
        assertFalse(TwoFactorAction.CodeChanged("270915").toString().contains("270915"))
    }

    private fun TestScope.submit(code: String = "270915") {
        viewModel.onAction(TwoFactorAction.CodeChanged(code))
        viewModel.onAction(TwoFactorAction.Submit)
        runCurrent()
    }

    @Test
    fun `continuar con 6 digitos manda el codigo y va a la boveda`() = scope.runTest {
        submit()
        assertEquals(listOf("twoFactor:270915"), auth.calls)
        assertEquals(TwoFactorEffect.NavigateToVault, viewModel.effects.first())
    }

    @Test
    fun `el servidor rechaza el codigo`() = scope.runTest {
        auth.twoFactorResult = ApiResult.Failure(ApiError.InvalidCode(attemptsRemaining = 3))
        submit()
        assertTrue(state.wrongCode)
        assertEquals("", state.code)
    }

    @Test
    fun `demasiados intentos en el servidor bloquean`() = scope.runTest {
        auth.twoFactorResult = ApiResult.Failure(ApiError.RateLimited(retryAfterSeconds = 900))
        submit()
        assertEquals(15 * 60, state.lockoutSecondsLeft)
    }

    @Test
    fun `el reto vencido regresa a iniciar sesion`() = scope.runTest {
        auth.twoFactorResult = ApiResult.Failure(ApiError.ChallengeExpired)
        submit()
        assertEquals(TwoFactorEffect.NavigateToLogin, viewModel.effects.first())
    }

    @Test
    fun `sin red muestra el error generico y vacia las casillas`() = scope.runTest {
        auth.twoFactorResult = ApiResult.Failure(ApiError.NetworkUnavailable)
        submit()
        assertTrue(state.genericError)
        assertEquals("", state.code)
        assertFalse(state.isLoading)
    }
}
