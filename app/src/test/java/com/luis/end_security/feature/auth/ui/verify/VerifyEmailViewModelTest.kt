package com.luis.end_security.feature.auth.ui.verify

import androidx.lifecycle.SavedStateHandle
import com.luis.end_security.feature.auth.domain.EmailVerificationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    private lateinit var viewModel: VerifyEmailViewModel

    private val state get() = viewModel.state.value

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        // El reloj del ViewModel es el reloj virtual del planificador: nada de esperas reales.
        viewModel = VerifyEmailViewModel(
            SavedStateHandle(mapOf(VerifyEmailViewModel.EMAIL_ARG to "ana.lopez@correo.com")),
            dispatcher.scheduler.timeSource,
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

    @Test
    fun `formato m ss`() {
        assertEquals("1:00", formatCountdown(60))
        assertEquals("0:09", formatCountdown(9))
        assertEquals("0:00", formatCountdown(0))
    }
}
