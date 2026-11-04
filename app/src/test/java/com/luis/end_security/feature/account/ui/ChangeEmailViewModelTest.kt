package com.luis.end_security.feature.account.ui

import com.luis.end_security.feature.account.domain.ChangeEmail
import com.luis.end_security.feature.account.domain.ConfirmEmailResult
import com.luis.end_security.feature.account.domain.RequestEmailResult
import com.luis.end_security.feature.auth.domain.DebugVerificationCodes
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

private const val CURRENT = "luis.ruiz@upchiapas.edu.mx"
private const val NEW = "luis.ruiz@gmail.com"
private const val PASSWORD = "Tortuga-violeta-salta-29-nubes"

private class FakeChangeEmail : ChangeEmail {
    var current: String? = CURRENT
    var twoFactor: Boolean? = false
    var requestResult: RequestEmailResult = RequestEmailResult.Accepted
    var confirmResult: ConfirmEmailResult = ConfirmEmailResult.Changed(NEW)
    var gate: CompletableDeferred<Unit>? = null
    val requests = mutableListOf<Triple<String, String, String?>>()
    val confirms = mutableListOf<String>()

    override suspend fun currentEmail(): String? = current

    override suspend fun twoFactorEnabled(): Boolean? = twoFactor

    override suspend fun request(newEmail: String, password: CharSequence, totpCode: String?): RequestEmailResult {
        requests += Triple(newEmail, password.toString(), totpCode)
        gate?.await()
        return requestResult
    }

    override suspend fun confirm(code: String): ConfirmEmailResult {
        confirms += code
        gate?.await()
        return confirmResult
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ChangeEmailViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)
    private val change = FakeChangeEmail()
    private val debug = object : DebugVerificationCodes {
        override fun latestCode(email: String): String? = "481920"
    }
    private lateinit var viewModel: ChangeEmailViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.create() {
        viewModel = ChangeEmailViewModel(change, debug, dispatcher.scheduler.timeSource)
        runCurrent()
    }

    private val state get() = viewModel.state.value

    private fun TestScope.type(email: String) {
        viewModel.onAction(ChangeEmailAction.NewEmailChanged(email))
        runCurrent()
    }

    private fun TestScope.cont(email: String = NEW) {
        type(email)
        viewModel.onAction(ChangeEmailAction.Continue)
        runCurrent()
    }

    private fun TestScope.send(password: String = PASSWORD, code: String? = null) {
        if (code != null) viewModel.onAction(ChangeEmailAction.SheetCodeChanged(code))
        viewModel.onAction(ChangeEmailAction.SendCode(password))
        runCurrent()
    }

    private fun TestScope.toCodeStep() {
        cont()
        send()
        assertEquals(EmailStep.Code, state.step)
    }

    // --- Carga ---

    @Test
    fun `carga el correo actual de Room y si hay 2FA`() = scope.runTest {
        change.twoFactor = true
        create()
        assertEquals(CURRENT, state.currentEmail)
        assertEquals(true, state.twoFactorRequired)
        assertEquals(EmailStep.Enter, state.step)
    }

    // --- Validaciones ---

    @Test
    fun `un formato invalido marca el error y no abre la hoja ni pide nada`() = scope.runTest {
        create()
        cont("esto-no-es-un-correo")
        assertTrue(state.showFormatError)
        assertFalse(state.sheetOpen)
        assertTrue(change.requests.isEmpty())
    }

    @Test
    fun `un campo vacio tambien marca el formato al tocar Continuar`() = scope.runTest {
        create()
        viewModel.onAction(ChangeEmailAction.Continue)
        runCurrent()
        assertTrue(state.showFormatError)
        assertFalse(state.sheetOpen)
    }

    @Test
    fun `al salir del campo con un formato malo se marca, y vacio no`() = scope.runTest {
        create()
        viewModel.onAction(ChangeEmailAction.NewEmailFocusLost)
        assertFalse(state.showFormatError)
        type("ana@")
        viewModel.onAction(ChangeEmailAction.NewEmailFocusLost)
        assertTrue(state.showFormatError)
    }

    @Test
    fun `escribir de nuevo quita los errores`() = scope.runTest {
        create()
        cont("malo")
        type("malo@")
        assertFalse(state.showFormatError)
    }

    @Test
    fun `igual al actual tras normalizar muestra el mensaje y no pide nada`() = scope.runTest {
        create()
        // Espacios y mayúsculas: el mismo correo.
        cont("  LUIS.Ruiz@UPChiapas.edu.MX ")
        assertTrue(state.showSameAsCurrent)
        assertFalse(state.showFormatError)
        assertFalse(state.sheetOpen)
        assertTrue(change.requests.isEmpty())
    }

    @Test
    fun `igual al actual con la e compuesta o descompuesta es el mismo correo`() = scope.runTest {
        change.current = "josé@correo.com" // «é» compuesta
        create()
        cont("josé@correo.com") // «é» descompuesta
        assertTrue(state.showSameAsCurrent)
    }

    @Test
    fun `los puntos de Gmail no se quitan, son otro correo`() = scope.runTest {
        change.current = "luis.ruiz@gmail.com"
        create()
        cont("luisruiz@gmail.com")
        assertFalse(state.showSameAsCurrent)
        assertTrue(state.sheetOpen)
    }

    @Test
    fun `con un correo valido y distinto abre la hoja sin llamar al servidor`() = scope.runTest {
        create()
        cont()
        assertTrue(state.sheetOpen)
        assertTrue(change.requests.isEmpty())
    }

    @Test
    fun `sin red no se sabe si hay 2FA y no abre la hoja`() = scope.runTest {
        change.twoFactor = null
        create()
        cont()
        assertTrue(state.genericError)
        assertFalse(state.sheetOpen)
    }

    // --- Reautenticación y EP-15 ---

    @Test
    fun `contrasena vacia en la hoja marca el error y no llama`() = scope.runTest {
        create()
        cont()
        send(password = "")
        assertTrue(state.showPasswordRequired)
        assertEquals(ReauthField.Password, state.focusRequest?.field)
        assertTrue(change.requests.isEmpty())
    }

    @Test
    fun `con 2FA el codigo de la app es obligatorio y viaja`() = scope.runTest {
        change.twoFactor = true
        create()
        cont()
        send()
        assertTrue(state.showCodeIncomplete)
        assertTrue(change.requests.isEmpty())
        send(code = "123456")
        assertEquals(Triple(NEW, PASSWORD, "123456"), change.requests.single())
    }

    @Test
    fun `sin 2FA no se manda codigo`() = scope.runTest {
        create()
        cont()
        send()
        assertNull(change.requests.single().third)
    }

    @Test
    fun `manda el correo normalizado`() = scope.runTest {
        create()
        cont("  Nuevo.Correo@GMAIL.com ")
        send()
        assertEquals("nuevo.correo@gmail.com", change.requests.single().first)
    }

    @Test
    fun `202 cierra la hoja y pasa al codigo con el correo nuevo y el codigo de demostracion`() = scope.runTest {
        create()
        toCodeStep()
        assertFalse(state.sheetOpen)
        assertEquals(NEW, state.newEmail)
        assertEquals("481920", state.debugCode)
        // El correo no cambia hasta confirmar.
        assertTrue(change.confirms.isEmpty())
    }

    @Test
    fun `reauth_required marca la contrasena y borra el codigo de la hoja`() = scope.runTest {
        change.twoFactor = true
        change.requestResult = RequestEmailResult.WrongCredentials
        create()
        cont()
        send(code = "123456")
        assertTrue(state.credentialsWrong)
        assertEquals("", state.sheetCode)
        assertTrue(state.sheetOpen)
        assertEquals(EmailStep.Enter, state.step)
    }

    @Test
    fun `una key_version mayor se avisa como conflicto dentro de la hoja`() = scope.runTest {
        change.requestResult = RequestEmailResult.KeyVersionConflict
        create()
        cont()
        send()
        assertTrue(state.sheetConflict)
        assertFalse(state.credentialsWrong)
    }

    @Test
    fun `sin red o 5xx el generico queda en la hoja`() = scope.runTest {
        change.requestResult = RequestEmailResult.Generic
        create()
        cont()
        send()
        assertTrue(state.sheetGenericError)
        assertTrue(state.sheetOpen)
    }

    @Test
    fun `400 validation_failed muestra el mensaje de igual al actual`() = scope.runTest {
        change.requestResult = RequestEmailResult.SameAsCurrent
        create()
        cont()
        send()
        assertTrue(state.showSameAsCurrent)
        assertFalse(state.sheetOpen)
    }

    @Test
    fun `mientras pide el codigo la hoja no se cierra ni se envia dos veces`() = scope.runTest {
        create()
        cont()
        val hold = CompletableDeferred<Unit>()
        change.gate = hold
        send()
        assertTrue(state.isRequesting)
        viewModel.onAction(ChangeEmailAction.SheetDismissed)
        viewModel.onAction(ChangeEmailAction.SendCode(PASSWORD))
        runCurrent()
        assertTrue(state.sheetOpen)
        assertEquals(1, change.requests.size)
        hold.complete(Unit)
        runCurrent()
        assertEquals(EmailStep.Code, state.step)
    }

    // --- 429: la espera de DEC-63 ---

    @Test
    fun `429 con horas cierra la hoja y muestra la espera en horas`() = scope.runTest {
        change.requestResult = RequestEmailResult.RateLimited(18 * 3_600 - 600)
        create()
        cont()
        send()
        assertFalse(state.sheetOpen)
        assertEquals(EmailWait.Hours(18), state.wait)
        assertEquals(EmailStep.Enter, state.step)
    }

    @Test
    fun `la espera pasa de horas a minutos y a segundos y desaparece`() = scope.runTest {
        change.requestResult = RequestEmailResult.RateLimited(3_700)
        create()
        cont()
        send()
        assertEquals(EmailWait.Hours(2), state.wait)
        // Pasados 101 s quedan 3 599 s: menos de una hora, en minutos (se redondea hacia arriba).
        advanceTimeBy(101_000)
        runCurrent()
        assertEquals(EmailWait.Minutes(60), state.wait)
        advanceTimeBy(3_539_000)
        runCurrent()
        // Quedan 60 s: todavía un minuto.
        assertEquals(EmailWait.Minutes(1), state.wait)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(EmailWait.Seconds(59), state.wait)
        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(EmailWait.Seconds(29), state.wait)
        advanceTimeBy(30_000)
        runCurrent()
        assertNull(state.wait)
    }

    @Test
    fun `durante la espera Continuar no llama al servidor y deja el mensaje`() = scope.runTest {
        change.requestResult = RequestEmailResult.RateLimited(3_600)
        create()
        cont()
        send()
        val before = change.requests.size
        viewModel.onAction(ChangeEmailAction.Continue)
        runCurrent()
        assertEquals(before, change.requests.size)
        assertFalse(state.sheetOpen)
        assertTrue(state.wait != null)
    }

    @Test
    fun `una espera de segundos se muestra en segundos`() = scope.runTest {
        change.requestResult = RequestEmailResult.RateLimited(45)
        create()
        cont()
        send()
        assertEquals(EmailWait.Seconds(45), state.wait)
    }

    @Test
    fun `los limites del texto de espera`() {
        assertNull(EmailWait.of(0))
        assertEquals(EmailWait.Seconds(1), EmailWait.of(1))
        assertEquals(EmailWait.Seconds(59), EmailWait.of(59))
        assertEquals(EmailWait.Minutes(1), EmailWait.of(60))
        assertEquals(EmailWait.Minutes(2), EmailWait.of(61))
        assertEquals(EmailWait.Minutes(60), EmailWait.of(3_599))
        assertEquals(EmailWait.Hours(1), EmailWait.of(3_600))
        assertEquals(EmailWait.Hours(2), EmailWait.of(3_601))
        assertEquals(EmailWait.Hours(24), EmailWait.of(86_400))
    }

    // --- Código (EP-16) ---

    @Test
    fun `un codigo incompleto no se manda`() = scope.runTest {
        create()
        toCodeStep()
        viewModel.onAction(ChangeEmailAction.CodeChanged("123"))
        viewModel.onAction(ChangeEmailAction.ConfirmCode)
        runCurrent()
        assertTrue(state.showCodeIncompleteInline)
        assertTrue(change.confirms.isEmpty())
    }

    @Test
    fun `el codigo correcto avisa del cambio con el correo del servidor`() = scope.runTest {
        create()
        toCodeStep()
        viewModel.onAction(ChangeEmailAction.CodeChanged("481920"))
        viewModel.onAction(ChangeEmailAction.ConfirmCode)
        runCurrent()
        assertEquals("481920", change.confirms.single())
        assertEquals(ChangeEmailEffect.Changed(NEW), viewModel.effects.first())
    }

    @Test
    fun `codigo incorrecto dice cuantos intentos quedan y vacia las casillas`() = scope.runTest {
        change.confirmResult = ConfirmEmailResult.WrongCode(3)
        create()
        toCodeStep()
        viewModel.onAction(ChangeEmailAction.CodeChanged("000000"))
        viewModel.onAction(ChangeEmailAction.ConfirmCode)
        runCurrent()
        assertEquals(EmailCodeError.WrongCode(3), state.codeError)
        assertEquals("", state.code)
        viewModel.onAction(ChangeEmailAction.CodeChanged("1"))
        assertNull(state.codeError)
    }

    @Test
    fun `sin intentos o vencido el codigo ya no sirve y no se puede confirmar`() = scope.runTest {
        change.confirmResult = ConfirmEmailResult.WrongCode(0)
        create()
        toCodeStep()
        viewModel.onAction(ChangeEmailAction.CodeChanged("000000"))
        viewModel.onAction(ChangeEmailAction.ConfirmCode)
        runCurrent()
        assertEquals(EmailCodeError.Expired, state.codeError)
        assertFalse(state.canSubmitCode)
        // Escribir no lo quita: hay que pedir otro.
        viewModel.onAction(ChangeEmailAction.CodeChanged("123456"))
        assertEquals(EmailCodeError.Expired, state.codeError)
        viewModel.onAction(ChangeEmailAction.ConfirmCode)
        runCurrent()
        assertEquals(1, change.confirms.size)
    }

    @Test
    fun `codigo vencido`() = scope.runTest {
        change.confirmResult = ConfirmEmailResult.Expired
        create()
        toCodeStep()
        viewModel.onAction(ChangeEmailAction.CodeChanged("123456"))
        viewModel.onAction(ChangeEmailAction.ConfirmCode)
        runCurrent()
        assertEquals(EmailCodeError.Expired, state.codeError)
    }

    @Test
    fun `bloqueado por intentos muestra los minutos`() = scope.runTest {
        change.confirmResult = ConfirmEmailResult.RateLimited(125)
        create()
        toCodeStep()
        viewModel.onAction(ChangeEmailAction.CodeChanged("123456"))
        viewModel.onAction(ChangeEmailAction.ConfirmCode)
        runCurrent()
        assertEquals(EmailCodeError.RateLimited(3), state.codeError)
    }

    @Test
    fun `sin red al confirmar es el generico y conserva el codigo`() = scope.runTest {
        change.confirmResult = ConfirmEmailResult.Generic
        create()
        toCodeStep()
        viewModel.onAction(ChangeEmailAction.CodeChanged("123456"))
        viewModel.onAction(ChangeEmailAction.ConfirmCode)
        runCurrent()
        assertEquals(EmailCodeError.Generic, state.codeError)
        assertEquals("123456", state.code)
    }

    // --- Pedir otro código y Atrás ---

    @Test
    fun `Pide otro codigo vuelve al correo nuevo con el correo ya escrito y es una solicitud nueva`() = scope.runTest {
        create()
        toCodeStep()
        viewModel.onAction(ChangeEmailAction.RequestAnotherCode)
        assertEquals(EmailStep.Enter, state.step)
        assertEquals(NEW, state.newEmail)
        assertEquals("", state.code)
        // Otra solicitud: de nuevo la hoja y otro EP-15.
        viewModel.onAction(ChangeEmailAction.Continue)
        runCurrent()
        assertTrue(state.sheetOpen)
        send()
        assertEquals(2, change.requests.size)
        assertEquals(EmailStep.Code, state.step)
    }

    @Test
    fun `Atras desde el codigo vuelve al correo nuevo y no cambia nada`() = scope.runTest {
        create()
        toCodeStep()
        viewModel.onAction(ChangeEmailAction.Back)
        assertEquals(EmailStep.Enter, state.step)
        assertEquals(NEW, state.newEmail)
        assertTrue(change.confirms.isEmpty())
        assertNull(withTimeoutOrNull(1) { viewModel.effects.first() })
    }

    @Test
    fun `Atras desde el correo nuevo sale`() = scope.runTest {
        create()
        viewModel.onAction(ChangeEmailAction.Back)
        assertEquals(ChangeEmailEffect.Leave, viewModel.effects.first())
    }

    @Test
    fun `Atras con la hoja abierta la cierra y no sale`() = scope.runTest {
        create()
        cont()
        viewModel.onAction(ChangeEmailAction.Back)
        assertFalse(state.sheetOpen)
        assertNull(withTimeoutOrNull(1) { viewModel.effects.first() })
    }

    @Test
    fun `Atras no hace nada mientras se pide o se confirma`() = scope.runTest {
        create()
        cont()
        val hold = CompletableDeferred<Unit>()
        change.gate = hold
        send()
        viewModel.onAction(ChangeEmailAction.Back)
        assertTrue(state.sheetOpen)
        assertNull(withTimeoutOrNull(1) { viewModel.effects.first() })
        hold.complete(Unit)
        runCurrent()
    }

    @Test
    fun `el estado y las acciones no imprimen secretos`() = scope.runTest {
        create()
        toCodeStep()
        viewModel.onAction(ChangeEmailAction.CodeChanged("481920"))
        assertFalse(state.toString().contains("481920"))
        assertFalse(state.toString().contains(NEW))
        assertFalse(ChangeEmailAction.SendCode(PASSWORD).toString().contains(PASSWORD))
        assertFalse(ChangeEmailAction.CodeChanged("481920").toString().contains("481920"))
    }
}
