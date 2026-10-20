package com.luis.end_security.feature.auth.ui.signup

import com.luis.end_security.core.common.ApiError
import com.luis.end_security.core.common.ApiResult
import com.luis.end_security.feature.auth.domain.FakeAuthRepository
import com.luis.end_security.feature.auth.domain.FakeUnicodeCharacterData
import com.luis.end_security.feature.auth.domain.PasswordStrengthEstimator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SignUpViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val estimator = FakeEstimator()
    private val auth = FakeAuthRepository()
    private lateinit var viewModel: SignUpViewModel

    private val state get() = viewModel.state.value

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel = SignUpViewModel(estimator, FakeUnicodeCharacterData(), auth, dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun passwords(password: String, confirmation: String = "") =
        viewModel.onAction(SignUpAction.PasswordsChanged(StringBuilder(password), StringBuilder(confirmation)))

    /** Llena todo lo necesario para poder crear la cuenta. */
    private fun fillValidForm() {
        viewModel.onAction(SignUpAction.EmailChanged("ana.lopez@correo.com"))
        estimator.nextScore = 3
        passwords(STRONG, STRONG)
        viewModel.onAction(SignUpAction.TermsAcceptedChanged(true))
    }

    @Test
    fun `empieza vacio y sin poder crear la cuenta`() {
        assertNull(state.strengthScore)
        assertFalse(state.meetsLength)
        assertFalse(state.confirmationMatches)
        assertFalse(state.showMismatchError)
        assertFalse(state.termsAccepted)
        assertFalse(state.canSubmit)
    }

    @Test
    fun `con todo valido se puede crear la cuenta`() {
        fillValidForm()
        assertEquals(3, state.strengthScore)
        assertTrue(state.meetsLength)
        assertTrue(state.confirmationMatches)
        assertTrue(state.canSubmit)
    }

    @Test
    fun `sin la casilla no se puede crear la cuenta`() {
        fillValidForm()
        viewModel.onAction(SignUpAction.TermsAcceptedChanged(false))
        assertFalse(state.canSubmit)
    }

    @Test
    fun `con correo invalido no se puede crear la cuenta`() {
        fillValidForm()
        viewModel.onAction(SignUpAction.EmailChanged("ana@"))
        assertFalse(state.canSubmit)
    }

    @Test
    fun `con puntaje menor a 3 no se puede crear la cuenta aunque sea larga`() {
        fillValidForm()
        estimator.nextScore = 2
        passwords("contrase\u00F1a2026", "contrase\u00F1a2026")
        assertEquals(2, state.strengthScore)
        assertTrue(state.meetsLength)
        assertFalse(state.canSubmit)
    }

    @Test
    fun `con menos de 12 caracteres no se puede crear la cuenta aunque puntue alto`() {
        fillValidForm()
        estimator.nextScore = 4
        passwords("Tr0ub4dor&3", "Tr0ub4dor&3") // 11 caracteres
        assertFalse(state.meetsLength)
        assertFalse(state.canSubmit)
    }

    @Test
    fun `si la confirmacion no es igual no se puede crear la cuenta`() {
        fillValidForm()
        passwords(STRONG, "$STRONG!")
        assertFalse(state.confirmationMatches)
        assertFalse(state.canSubmit)
    }

    @Test
    fun `mientras se calcula el puntaje nuevo el anterior no habilita el boton`() {
        // El cálculo queda en cola hasta que el planificador avanza.
        val scoring = StandardTestDispatcher(dispatcher.scheduler)
        viewModel = SignUpViewModel(estimator, FakeUnicodeCharacterData(), auth, scoring)
        fillValidForm()
        scoring.scheduler.runCurrent()
        assertTrue(state.canSubmit)

        passwords("otra contrase\u00F1a cualquiera", "otra contrase\u00F1a cualquiera")
        assertFalse(state.canSubmit)

        scoring.scheduler.runCurrent()
        assertTrue(state.canSubmit)
    }

    @Test
    fun `al vaciar la contrasena desaparece el puntaje`() {
        fillValidForm()
        passwords("", "")
        assertNull(state.strengthScore)
        assertFalse(state.canSubmit)
    }

    @Test
    fun `no coinciden no se muestra mientras se escribe`() {
        estimator.nextScore = 3
        passwords(STRONG, "tortuga")
        assertFalse(state.confirmationMatches)
        assertFalse(state.showMismatchError)
    }

    @Test
    fun `no coinciden se muestra al perder el foco la confirmacion`() {
        estimator.nextScore = 3
        passwords(STRONG, "tortuga")
        viewModel.onAction(SignUpAction.ConfirmationFocusLost)
        assertTrue(state.showMismatchError)
    }

    @Test
    fun `no coinciden se muestra al intentar crear la cuenta`() {
        estimator.nextScore = 3
        passwords(STRONG, "tortuga")
        viewModel.onAction(SignUpAction.Submit(StringBuilder(STRONG), StringBuilder("tortuga")))
        assertTrue(state.showMismatchError)
    }

    @Test
    fun `crear cuenta verifica la politica aunque el medidor siga calculando`() {
        // Antes, Submit se rechazaba siempre porque el cálculo del medidor seguía pendiente.
        val scoring = StandardTestDispatcher(dispatcher.scheduler)
        viewModel = SignUpViewModel(estimator, FakeUnicodeCharacterData(), auth, scoring)
        fillValidForm()

        viewModel.onAction(SignUpAction.Submit(StringBuilder(STRONG), StringBuilder(STRONG)))
        assertTrue("se está verificando", state.isLoading)
        assertFalse(state.canSubmit)

        scoring.scheduler.runCurrent()
        assertFalse(state.isLoading)
        assertTrue(state.canSubmit)
    }

    @Test
    fun `la politica se revisa sobre el texto enviado y no sobre el ultimo puntaje`() = kotlinx.coroutines.test.runTest(dispatcher) {
        fillValidForm() // el estado dice «fuerte y válido»
        estimator.nextScore = 2 // pero el texto que se envía puntúa 2
        assertFalse(viewModel.passesPolicy(StringBuilder("contrase\u00F1a2026"), StringBuilder("contrase\u00F1a2026")))

        estimator.nextScore = 4
        assertFalse("menos de 12", viewModel.passesPolicy(StringBuilder("Tr0ub4dor&3"), StringBuilder("Tr0ub4dor&3")))
        assertFalse("no coinciden", viewModel.passesPolicy(StringBuilder(STRONG), StringBuilder("$STRONG!")))
        assertTrue(viewModel.passesPolicy(StringBuilder(STRONG), StringBuilder(STRONG)))
    }

    @Test
    fun `al vaciar los campos se olvida todo lo calculado`() {
        fillValidForm()
        viewModel.onAction(SignUpAction.ConfirmationFocusLost)

        viewModel.onAction(SignUpAction.PasswordsCleared)

        assertNull(state.strengthScore)
        assertFalse(state.meetsLength)
        assertFalse(state.confirmationMatches)
        assertFalse(state.showMismatchError)
        assertFalse(state.canSubmit)
        assertTrue("la casilla no cambia", state.termsAccepted)
    }

    @Test
    fun `si el estimador falla no se puede crear la cuenta y no se cierra la app`() {
        viewModel.onAction(SignUpAction.EmailChanged("ana.lopez@correo.com"))
        viewModel.onAction(SignUpAction.TermsAcceptedChanged(true))
        estimator.failNext = true
        passwords(STRONG, STRONG)

        assertNull(state.strengthScore)
        assertFalse(state.canSubmit)
    }

    @Test
    fun `crear cuenta con la politica cumplida navega a verificar correo`() = kotlinx.coroutines.test.runTest(dispatcher) {
        fillValidForm()
        viewModel.onAction(SignUpAction.Submit(StringBuilder(STRONG), StringBuilder(STRONG)))
        assertEquals(SignUpEffect.NavigateToVerifyEmail("ana.lopez@correo.com"), viewModel.effects.first())
    }

    @Test
    fun `crear cuenta registra con el correo y la contrasena del campo`() = kotlinx.coroutines.test.runTest(dispatcher) {
        fillValidForm()
        viewModel.onAction(SignUpAction.Submit(StringBuilder(STRONG), StringBuilder(STRONG)))
        assertEquals(listOf("register:ana.lopez@correo.com"), auth.calls)
        assertEquals(listOf(STRONG), auth.passwords)
    }

    @Test
    fun `si el registro falla muestra el error y no navega`() = kotlinx.coroutines.test.runTest(dispatcher) {
        auth.registerResult = ApiResult.Failure(ApiError.NetworkUnavailable)
        fillValidForm()
        viewModel.onAction(SignUpAction.Submit(StringBuilder(STRONG), StringBuilder(STRONG)))
        assertEquals(SignUpSubmitError.Generic, state.submitError)
        assertFalse(state.isLoading)
        assertNull(kotlinx.coroutines.withTimeoutOrNull(100) { viewModel.effects.first() })
    }

    @Test
    fun `demasiados registros muestran los minutos de espera`() = kotlinx.coroutines.test.runTest(dispatcher) {
        auth.registerResult = ApiResult.Failure(ApiError.RateLimited(retryAfterSeconds = 120))
        fillValidForm()
        viewModel.onAction(SignUpAction.Submit(StringBuilder(STRONG), StringBuilder(STRONG)))
        assertEquals(SignUpSubmitError.RateLimited(minutes = 2), state.submitError)
    }

    @Test
    fun `crear cuenta sin la politica no navega`() = kotlinx.coroutines.test.runTest(dispatcher) {
        fillValidForm()
        estimator.nextScore = 2
        viewModel.onAction(SignUpAction.Submit(StringBuilder("contrase\u00F1a2026"), StringBuilder("contrase\u00F1a2026")))
        assertNull(kotlinx.coroutines.withTimeoutOrNull(100) { viewModel.effects.first() })
        assertTrue("no se registra", auth.calls.isEmpty())
    }

    @Test
    fun `pasado el maximo tras NFKC no se mide ni se puede crear la cuenta`() = kotlinx.coroutines.test.runTest(dispatcher) {
        fillValidForm()
        estimator.lastLength = -1
        val expanding = "\uFDFA".repeat(20) // 20 unidades, 360 caracteres tras NFKC
        estimator.nextScore = 4
        passwords(expanding, expanding)

        assertTrue(state.exceedsMaxLength)
        assertNull(state.strengthScore)
        assertEquals("zxcvbn no se llamó", -1, estimator.lastLength)
        assertFalse(state.canSubmit)
        assertFalse(viewModel.passesPolicy(StringBuilder(expanding), StringBuilder(expanding)))
        assertEquals("tampoco al revisar la política", -1, estimator.lastLength)
    }

    @Test
    fun `con caracteres invisibles no se mide ni se puede crear la cuenta`() = kotlinx.coroutines.test.runTest(dispatcher) {
        fillValidForm()
        estimator.lastLength = -1
        val withZeroWidth = "tortuga\u200Bvioleta-salta-29-nubes"
        passwords(withZeroWidth, withZeroWidth)

        assertTrue(state.hasForbiddenCharacters)
        assertNull(state.strengthScore)
        assertEquals("zxcvbn no se llamó", -1, estimator.lastLength)
        assertFalse(state.canSubmit)
        assertFalse(viewModel.passesPolicy(StringBuilder(withZeroWidth), StringBuilder(withZeroWidth)))
    }

    @Test
    fun `un emoji compuesto no cuenta como caracter invisible`() {
        estimator.nextScore = 4
        val family = "familia 👨\u200D👩\u200D👧 en casa grande"
        passwords(family, family)
        assertFalse(state.hasForbiddenCharacters)
    }

    @Test
    fun `la confirmacion en NFD coincide con la contrasena en NFC`() {
        estimator.nextScore = 3
        passwords("contrase\u00F1a-de-tortugas", "contrasen\u0303a-de-tortugas")
        assertTrue(state.confirmationMatches)
    }

    @Test
    fun `la longitud se cuenta en caracteres normalizados`() {
        estimator.nextScore = 4
        passwords("contrasen\u0303a1", "contrasen\u0303a1") // 12 unidades UTF-16, 11 caracteres
        assertFalse(state.meetsLength)
    }

    @Test
    fun `el estimador recibe el texto normalizado`() {
        estimator.nextScore = 3
        passwords("contrasen\u0303a-de-tortugas", "")
        assertEquals("contrase\u00F1a-de-tortugas".length, estimator.lastLength)
    }

    @Test
    fun `el estado no imprime el correo ni el puntaje`() {
        fillValidForm()
        val text = state.toString() + SignUpAction.EmailChanged("ana.lopez@correo.com").toString()
        assertFalse(text.contains("ana.lopez"))
        assertFalse(text.contains("strengthScore"))
    }

    @Test
    fun `no se marca error si la confirmacion esta vacia al perder el foco`() {
        estimator.nextScore = 3
        passwords(STRONG, "")
        viewModel.onAction(SignUpAction.ConfirmationFocusLost)
        assertFalse(state.showMismatchError)
    }

    @Test
    fun `el error se quita en cuanto coinciden`() {
        estimator.nextScore = 3
        passwords(STRONG, "tortuga")
        viewModel.onAction(SignUpAction.ConfirmationFocusLost)
        passwords(STRONG, STRONG)
        assertFalse(state.showMismatchError)
    }

    @Test
    fun `la accion con contrasenas no las imprime`() {
        val action = SignUpAction.PasswordsChanged(StringBuilder(STRONG), StringBuilder(STRONG))
        assertFalse(action.toString().contains(STRONG))
    }

    @Test
    fun `el estimador recibe la contrasena y no la confirmacion`() {
        estimator.nextScore = 3
        passwords(STRONG, "otra")
        assertEquals(STRONG.length, estimator.lastLength)
    }

    private class FakeEstimator : PasswordStrengthEstimator {
        var nextScore = 0
        var failNext = false
        var lastLength = -1

        override fun score(password: CharSequence): Int {
            lastLength = password.length
            if (failNext) {
                failNext = false
                error("no se pudieron cargar los diccionarios")
            }
            return nextScore
        }
    }

    private companion object {
        const val STRONG = "tortuga-violeta-salta-29-nubes"
    }
}
