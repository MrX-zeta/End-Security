package com.luis.end_security.feature.auth.ui.verify

import androidx.lifecycle.SavedStateHandle
import com.luis.end_security.core.common.ApiError
import com.luis.end_security.core.common.ApiResult
import com.luis.end_security.feature.auth.domain.AuthStep
import com.luis.end_security.feature.auth.domain.DebugVerificationCodes
import com.luis.end_security.feature.auth.domain.EmailVerificationResult
import com.luis.end_security.feature.auth.domain.FakeAuthRepository
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VerifyEmailViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)
    private val auth = FakeAuthRepository()
    private val debugCodes = object : DebugVerificationCodes {
        var code: String? = "111111"
        override fun latestCode(email: String): String? = code
    }
    private lateinit var viewModel: VerifyEmailViewModel

    private val state get() = viewModel.state.value

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        // El reloj del ViewModel es el reloj virtual del planificador: nada de esperas reales.
        viewModel = VerifyEmailViewModel(
            SavedStateHandle(mapOf(VerifyEmailViewModel.EMAIL_ARG to "ana.lopez@correo.com")),
            dispatcher.scheduler.timeSource,
            auth,
            debugCodes,
        )
        scope.runCurrent()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `toma el correo del argumento de la ruta`() {
        assertEquals("ana.lopez@correo.com", state.email)
    }

    @Test
    fun `verificar se habilita solo con 6 digitos`() {
        viewModel.onAction(VerifyEmailAction.CodeChanged("12345"))
        assertFalse(state.canSubmit)
        viewModel.onAction(VerifyEmailAction.CodeChanged("123456"))
        assertTrue(state.canSubmit)
    }

    @Test
    fun `el codigo se filtra a digitos y maximo 6`() {
        viewModel.onAction(VerifyEmailAction.CodeChanged("12a3 45678"))
        assertEquals("123456", state.code)
    }

    @Test
    fun `empieza con 60 segundos de espera para reenviar`() {
        assertEquals(60, state.resendSecondsLeft)
        assertFalse(state.canResend)
    }

    @Test
    fun `la cuenta regresiva baja con el tiempo`() = scope.runTest {
        advanceTimeBy(15_000)
        runCurrent()
        assertEquals(45, state.resendSecondsLeft)
        assertEquals("0:45", formatCountdown(state.resendSecondsLeft))
    }

    @Test
    fun `al terminar la espera se puede reenviar`() = scope.runTest {
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(0, state.resendSecondsLeft)
        assertTrue(state.canResend)
    }

    @Test
    fun `reenviar no hace nada durante la espera`() = scope.runTest {
        advanceTimeBy(10_000)
        runCurrent()
        viewModel.onAction(VerifyEmailAction.Resend)
        runCurrent()
        assertEquals(50, state.resendSecondsLeft)
    }

    @Test
    fun `reenviar reinicia la espera y vacia el codigo`() = scope.runTest {
        viewModel.onAction(VerifyEmailAction.CodeChanged("123"))
        advanceTimeBy(60_000)
        runCurrent()

        viewModel.onAction(VerifyEmailAction.Resend)
        runCurrent()

        assertEquals(60, state.resendSecondsLeft)
        assertEquals("", state.code)
        assertFalse(state.canResend)
    }

    @Test
    fun `codigo incorrecto vacia las casillas y dice cuantos intentos quedan`() {
        viewModel.onAction(VerifyEmailAction.CodeChanged("123456"))
        viewModel.onVerificationResult(EmailVerificationResult.WrongCode(attemptsLeft = 3))

        assertEquals(VerifyEmailError.WrongCode(attemptsLeft = 3), state.error)
        assertEquals("", state.code)
        assertFalse(state.canSubmit)
        assertFalse("la espera sigue", state.canResend)
    }

    @Test
    fun `al escribir otra vez se quita codigo incorrecto`() {
        viewModel.onVerificationResult(EmailVerificationResult.WrongCode(attemptsLeft = 3))
        viewModel.onAction(VerifyEmailAction.CodeChanged("1"))
        assertNull(state.error)
    }

    @Test
    fun `codigo vencido muestra reenviar de inmediato y bloquea verificar`() {
        viewModel.onVerificationResult(EmailVerificationResult.Expired)

        assertEquals(VerifyEmailError.Expired, state.error)
        assertTrue("reenviar sin esperar", state.canResend)
        viewModel.onAction(VerifyEmailAction.CodeChanged("123456"))
        assertFalse(state.canSubmit)
        assertEquals(VerifyEmailError.Expired, state.error)
    }

    @Test
    fun `sin intentos cuenta como codigo vencido`() {
        viewModel.onVerificationResult(EmailVerificationResult.WrongCode(attemptsLeft = 0))
        assertEquals(VerifyEmailError.Expired, state.error)
        assertTrue(state.canResend)
    }

    @Test
    fun `reenviar tras un codigo vencido quita el error`() = scope.runTest {
        viewModel.onVerificationResult(EmailVerificationResult.Expired)
        viewModel.onAction(VerifyEmailAction.Resend)
        runCurrent()

        assertNull(state.error)
        assertEquals(60, state.resendSecondsLeft)
    }

    @Test
    fun `el estado no imprime el codigo ni el correo`() {
        viewModel.onAction(VerifyEmailAction.CodeChanged("481920"))
        val text = state.toString() + VerifyEmailAction.CodeChanged("481920").toString()
        assertFalse(text.contains("481920"))
        assertFalse(text.contains("ana.lopez"))
    }

    private fun TestScope.submit(code: String = "123456") {
        viewModel.onAction(VerifyEmailAction.CodeChanged(code))
        viewModel.onAction(VerifyEmailAction.Submit)
        runCurrent()
    }

    @Test
    fun `verificar manda el correo y el codigo y va a la boveda`() = scope.runTest {
        submit()
        assertEquals(listOf("verifyEmail:ana.lopez@correo.com:123456"), auth.calls)
        assertEquals(VerifyEmailEffect.NavigateToVault, viewModel.effects.first())
    }

    @Test
    fun `sin la llave en memoria va a iniciar sesion`() = scope.runTest {
        auth.verifyEmailResult = ApiResult.Success(AuthStep.SignInAgain)
        submit()
        assertEquals(VerifyEmailEffect.NavigateToLogin, viewModel.effects.first())
    }

    @Test
    fun `el servidor rechaza el codigo con los intentos que quedan`() = scope.runTest {
        auth.verifyEmailResult = ApiResult.Failure(ApiError.InvalidCode(attemptsRemaining = 2))
        submit()
        assertEquals(VerifyEmailError.WrongCode(attemptsLeft = 2), state.error)
        assertFalse(state.isLoading)
    }

    @Test
    fun `el codigo vencido en el servidor deja reenviar`() = scope.runTest {
        auth.verifyEmailResult = ApiResult.Failure(ApiError.ChallengeExpired)
        submit()
        assertEquals(VerifyEmailError.Expired, state.error)
        assertTrue(state.canResend)
    }

    @Test
    fun `sin red muestra el error generico`() = scope.runTest {
        auth.verifyEmailResult = ApiResult.Failure(ApiError.NetworkUnavailable)
        submit()
        assertEquals(VerifyEmailError.Generic, state.error)
    }

    @Test
    fun `reenviar pide otro codigo y muestra el de prueba nuevo`() = scope.runTest {
        assertEquals("111111", state.debugCode)
        advanceTimeBy(60_000)
        runCurrent()
        debugCodes.code = "222222"
        viewModel.onAction(VerifyEmailAction.Resend)
        runCurrent()
        assertTrue(auth.calls.contains("resend:ana.lopez@correo.com"))
        assertEquals("222222", state.debugCode)
    }

    @Test
    fun `demasiados reenvios muestran los minutos de espera`() = scope.runTest {
        auth.resendResult = ApiResult.Failure(ApiError.RateLimited(retryAfterSeconds = 300))
        advanceTimeBy(60_000)
        runCurrent()
        viewModel.onAction(VerifyEmailAction.Resend)
        runCurrent()
        assertEquals(VerifyEmailError.RateLimited(minutes = 5), state.error)
    }

    @Test
    fun `formato m ss`() {
        assertEquals("1:00", formatCountdown(60))
        assertEquals("0:09", formatCountdown(9))
        assertEquals("0:00", formatCountdown(0))
    }
}
