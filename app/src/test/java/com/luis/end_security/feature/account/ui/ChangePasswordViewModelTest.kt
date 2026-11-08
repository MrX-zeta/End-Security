package com.luis.end_security.feature.account.ui

import com.luis.end_security.core.common.ApiResult
import com.luis.end_security.core.session.AuthNotices
import com.luis.end_security.feature.account.domain.ChangeMasterPassword
import com.luis.end_security.feature.account.domain.ChangePasswordResult
import com.luis.end_security.feature.account.domain.TwoFactorRepository
import com.luis.end_security.feature.account.domain.TwoFactorSetup
import com.luis.end_security.feature.auth.domain.FakeUnicodeCharacterData
import com.luis.end_security.feature.auth.domain.PasswordStrengthEstimator
import com.luis.end_security.feature.health.domain.BreachApi
import com.luis.end_security.feature.health.domain.BreachLookup
import com.luis.end_security.feature.unlock.FakeBiometricUnlock
import com.luis.end_security.feature.unlock.domain.BiometricPreparation
import com.luis.end_security.feature.unlock.domain.BiometricResult
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import javax.crypto.Cipher

private const val CURRENT = "Tortuga-violeta-salta-29-nubes"
private const val NEW = "Nube-gris-camina-41-rapido"

private class FakeChange : ChangeMasterPassword {
    var result: ChangePasswordResult = ChangePasswordResult.Success(biometricWasEnabled = false)
    var gate: CompletableDeferred<Unit>? = null
    var calls = 0
    var lastCurrent: String? = null
    var lastTotp: String? = null
    var lastNew: String? = null

    override suspend fun change(current: CharSequence, totpCode: String?, newPassword: CharSequence): ChangePasswordResult {
        calls++
        lastCurrent = current.toString()
        lastTotp = totpCode
        lastNew = newPassword.toString()
        gate?.await()
        return result
    }
}

private class FakeTwoFactor : TwoFactorRepository {
    var enabled: Boolean? = false
    var calls = 0

    override suspend fun isEnabled(): Boolean? {
        calls++
        return enabled
    }

    override suspend fun startSetup(): ApiResult<TwoFactorSetup> = error("no se usa")
    override suspend fun confirm(code: String): ApiResult<List<String>> = error("no se usa")
    override suspend fun disable(password: CharSequence, totpCode: String): ApiResult<Unit> = error("no se usa")
    override suspend fun regenerateRecoveryCodes(password: CharSequence, totpCode: String): ApiResult<List<String>> = error("no se usa")
}

private class FakeBreaches : BreachApi {
    var result: BreachLookup = BreachLookup.Clean
    var calls = 0
    var lastLookup: String? = null

    override suspend fun lookup(password: String): BreachLookup {
        calls++
        lastLookup = password
        return result
    }
}

private class FakeEstimator : PasswordStrengthEstimator {
    var next = 3
    override fun score(password: CharSequence): Int = next
}

@OptIn(ExperimentalCoroutinesApi::class)
class ChangePasswordViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)
    private val change = FakeChange()
    private val twoFactor = FakeTwoFactor()
    private val breaches = FakeBreaches()
    private val estimator = FakeEstimator()
    private val biometric = FakeBiometricUnlock()
    private val notices = AuthNotices()
    private lateinit var viewModel: ChangePasswordViewModel

    private val state get() = viewModel.state.value

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.create() {
        viewModel = ChangePasswordViewModel(
            change, twoFactor, breaches, estimator, FakeUnicodeCharacterData(), biometric, notices,
            dispatcher, dispatcher.scheduler.timeSource,
        )
        runCurrent()
    }

    private fun TestScope.typeNew(password: String = NEW, confirmation: String = password) {
        viewModel.onAction(ChangePasswordAction.NewPasswordsChanged(password, confirmation))
        runCurrent()
    }

    private fun TestScope.submit(current: String = CURRENT, password: String = NEW, confirmation: String = password) {
        viewModel.onAction(ChangePasswordAction.CurrentEmptinessChanged(current.isEmpty()))
        viewModel.onAction(ChangePasswordAction.Submit(current, password, confirmation))
        runCurrent()
    }

    private fun cipher(): Cipher = Cipher.getInstance("AES/GCM/NoPadding")

    // ---- Requisitos y medidor

    @Test
    fun `cada requisito se marca por separado y no se revisa HIBP al escribir`() = scope.runTest {
        create()
        typeNew("abcdefghijkl")
        assertTrue(state.meetsLength)
        assertFalse(state.hasUppercase)
        assertTrue(state.hasLowercase)
        assertFalse(state.hasDigit)
        typeNew("Abcdefghijk1")
        assertTrue(state.hasUppercase && state.hasLowercase && state.hasDigit)
        // «No aparece en filtraciones» solo se revisa al tocar el botón.
        assertEquals(0, breaches.calls)
        assertEquals(BreachCheck.Pending, state.breach)
    }

    @Test
    fun `la coincidencia se evalua y el error de no coincide solo al salir del campo`() = scope.runTest {
        create()
        typeNew(NEW, "$NEW!")
        assertFalse(state.confirmationMatches)
        assertFalse(state.showMismatch)
        viewModel.onAction(ChangePasswordAction.ConfirmationFocusLost)
        assertTrue(state.showMismatch)
        typeNew(NEW, NEW)
        assertTrue(state.confirmationMatches)
        assertFalse(state.showMismatch)
    }

    @Test
    fun `el puntaje se calcula con el estimador`() = scope.runTest {
        estimator.next = 4
        create()
        typeNew()
        assertEquals(4, state.strengthScore)
    }

    @Test
    fun `al vaciar los campos se olvida todo lo calculado`() = scope.runTest {
        create()
        typeNew()
        viewModel.onAction(ChangePasswordAction.NewPasswordsCleared)
        assertFalse(state.newTyped)
        assertNull(state.strengthScore)
        assertFalse(state.meetsLength)
    }

    // ---- El código de la app solo con 2FA

    @Test
    fun `el campo de codigo se pide solo con la 2FA activa`() = scope.runTest {
        twoFactor.enabled = true
        create()
        assertEquals(true, state.twoFactorRequired)
        twoFactor.enabled = false
        create()
        assertEquals(false, state.twoFactorRequired)
    }

    @Test
    fun `con 2FA y el codigo incompleto marca el codigo y no llama a nada`() = scope.runTest {
        twoFactor.enabled = true
        create()
        viewModel.onAction(ChangePasswordAction.CodeChanged("48"))
        typeNew()
        submit()
        assertTrue(state.showCodeIncomplete)
        assertEquals(ChangeField.Code, state.focusRequest?.field)
        assertEquals(0, change.calls)
        assertEquals(0, breaches.calls)
        viewModel.onAction(ChangePasswordAction.CodeChanged("482916"))
        assertFalse(state.showCodeIncomplete)
    }

    @Test
    fun `con 2FA manda el codigo, sin 2FA no manda codigo`() = scope.runTest {
        twoFactor.enabled = true
        create()
        viewModel.onAction(ChangePasswordAction.CodeChanged("482916"))
        typeNew()
        submit()
        assertEquals("482916", change.lastTotp)
        twoFactor.enabled = false
        create()
        typeNew()
        submit()
        assertNull(change.lastTotp)
    }

    @Test
    fun `sin saber si hay 2FA se vuelve a preguntar al enviar, y sin red no se puede`() = scope.runTest {
        twoFactor.enabled = null
        create()
        assertNull(state.twoFactorRequired)
        typeNew()
        submit()
        assertTrue(state.genericError)
        assertEquals(0, change.calls)
        assertFalse(state.isLoading)
    }

    @Test
    fun `si al volver a preguntar resulta que hay 2FA pide el codigo`() = scope.runTest {
        twoFactor.enabled = null
        create()
        typeNew()
        twoFactor.enabled = true
        submit()
        assertEquals(true, state.twoFactorRequired)
        assertTrue(state.showCodeIncomplete)
        assertEquals(ChangeField.Code, state.focusRequest?.field)
        assertEquals(0, change.calls)
    }

    // ---- Errores al tocar el botón (tarea 15)

    @Test
    fun `con todo vacio marca los campos, enfoca la actual y no llama a nada`() = scope.runTest {
        create()
        submit(current = "", password = "", confirmation = "")
        assertTrue(state.showCurrentRequired)
        assertTrue(state.showNewRequired)
        assertEquals(ChangeField.Current, state.focusRequest?.field)
        assertEquals(0, breaches.calls)
        assertEquals(0, change.calls)
        viewModel.onAction(ChangePasswordAction.CurrentEmptinessChanged(false))
        assertFalse(state.showCurrentRequired)
    }

    @Test
    fun `una nueva que no cumple la politica enfoca la nueva`() = scope.runTest {
        create()
        typeNew("corta1A")
        submit(password = "corta1A")
        assertTrue(!state.meetsLength)
        assertEquals(ChangeField.New, state.focusRequest?.field)
        assertEquals(0, change.calls)
    }

    @Test
    fun `igual a la actual dice Debe ser distinta y no manda nada`() = scope.runTest {
        create()
        typeNew(CURRENT)
        submit(password = CURRENT)
        assertTrue(state.showSameAsCurrent)
        assertEquals(ChangeField.New, state.focusRequest?.field)
        assertEquals(0, breaches.calls)
        assertEquals(0, change.calls)
    }

    @Test
    fun `la comparacion con la actual es sobre el texto normalizado`() = scope.runTest {
        create()
        val composed = "Contraseña-de-tortugas-29"
        val decomposed = "Contraseña-de-tortugas-29"
        typeNew(decomposed)
        submit(current = composed, password = decomposed)
        assertTrue(state.showSameAsCurrent)
    }

    @Test
    fun `una confirmacion distinta enfoca la confirmacion`() = scope.runTest {
        create()
        typeNew(NEW, "$NEW!")
        submit(confirmation = "$NEW!")
        assertTrue(state.showMismatch)
        assertEquals(ChangeField.Confirmation, state.focusRequest?.field)
        assertEquals(0, change.calls)
    }

    @Test
    fun `dos toques seguidos con el mismo error cambian el id del foco`() = scope.runTest {
        create()
        submit(current = "")
        val first = state.focusRequest
        submit(current = "")
        assertTrue(first != state.focusRequest)
    }

    @Test
    fun `una contrasena debil se marca y no se revisa en HIBP`() = scope.runTest {
        estimator.next = 2
        create()
        typeNew()
        submit()
        assertTrue(state.showWeak)
        assertEquals(ChangeField.New, state.focusRequest?.field)
        assertEquals(0, breaches.calls)
        assertEquals(0, change.calls)
    }

    // ---- HIBP al tocar el botón

    @Test
    fun `con filtracion bloquea el cambio y marca la nueva`() = scope.runTest {
        breaches.result = BreachLookup.Found(12_345)
        create()
        typeNew()
        submit()
        assertEquals(1, breaches.calls)
        assertEquals(BreachCheck.Found, state.breach)
        assertEquals(ChangeField.New, state.focusRequest?.field)
        assertEquals(0, change.calls)
        assertFalse(state.isLoading)
    }

    @Test
    fun `sin filtracion se marca limpia y sigue con el cambio`() = scope.runTest {
        create()
        typeNew()
        submit()
        assertEquals(BreachCheck.Clean, state.breach)
        assertEquals(1, change.calls)
        // Solo el texto normalizado se consulta, una vez.
        assertEquals(NEW, breaches.lastLookup)
    }

    @Test
    fun `sin red la linea queda sin revisar y no bloquea`() = scope.runTest {
        breaches.result = BreachLookup.Offline
        create()
        typeNew()
        submit()
        assertEquals(BreachCheck.Offline, state.breach)
        assertEquals(1, change.calls)
    }

    @Test
    fun `si HIBP no responde tampoco bloquea`() = scope.runTest {
        breaches.result = BreachLookup.Unavailable
        create()
        typeNew()
        submit()
        assertEquals(BreachCheck.Offline, state.breach)
        assertEquals(1, change.calls)
    }

    @Test
    fun `cambiar el texto invalida la revision de filtraciones`() = scope.runTest {
        breaches.result = BreachLookup.Found(3)
        create()
        typeNew()
        submit()
        assertEquals(BreachCheck.Found, state.breach)
        typeNew("Otra-nube-gris-camina-77")
        assertEquals(BreachCheck.Pending, state.breach)
    }

    // ---- El cambio

    @Test
    fun `manda las contrasenas al caso de uso y el exito vuelve a Ajustes`() = scope.runTest {
        create()
        typeNew()
        submit()
        assertEquals(CURRENT, change.lastCurrent)
        assertEquals(NEW, change.lastNew)
        assertEquals(ChangePasswordEffect.Finish(biometricOff = false), viewModel.effects.first())
    }

    @Test
    fun `mientras dura el cambio el boton carga, Atras queda deshabilitado y no se envia otra vez`() = scope.runTest {
        change.gate = CompletableDeferred()
        create()
        typeNew()
        submit()
        assertTrue(state.isLoading)
        assertTrue(state.isBusy)
        viewModel.onAction(ChangePasswordAction.Submit(CURRENT, NEW, NEW))
        runCurrent()
        assertEquals(1, change.calls)
        change.gate!!.complete(Unit)
        runCurrent()
        assertFalse(state.isBusy)
    }

    @Test
    fun `reauth_required marca la actual y vacia el codigo`() = scope.runTest {
        twoFactor.enabled = true
        change.result = ChangePasswordResult.WrongCredentials
        create()
        viewModel.onAction(ChangePasswordAction.CodeChanged("482916"))
        typeNew()
        submit()
        assertTrue(state.credentialsWrong)
        assertEquals("", state.code)
        assertEquals(ChangeField.Current, state.focusRequest?.field)
        viewModel.onAction(ChangePasswordAction.CodeChanged("1"))
        assertFalse(state.credentialsWrong)
    }

    @Test
    fun `409 avisa que cambio en otro dispositivo`() = scope.runTest {
        change.result = ChangePasswordResult.KeyVersionConflict
        create()
        typeNew()
        submit()
        assertTrue(state.conflict)
        assertFalse(state.isLoading)
    }

    @Test
    fun `sin red sale el error generico y se puede reintentar`() = scope.runTest {
        change.result = ChangePasswordResult.Generic
        create()
        typeNew()
        submit()
        assertTrue(state.genericError)
        assertFalse(state.isLoading)
        change.result = ChangePasswordResult.Success(false)
        submit()
        assertEquals(2, change.calls)
    }

    @Test
    fun `el limite de intentos bloquea con segundos`() = scope.runTest {
        change.result = ChangePasswordResult.RateLimited(45)
        create()
        typeNew()
        submit()
        assertEquals(45, state.lockoutSecondsLeft)
        submit()
        assertEquals(1, change.calls)
        advanceTimeBy(46_000)
        runCurrent()
        assertFalse(state.isLockedOut)
    }

    @Test
    fun `si no se pudo guardar aqui se va a iniciar sesion con el aviso`() = scope.runTest {
        change.result = ChangePasswordResult.LocalSaveFailedSignedOut
        create()
        typeNew()
        submit()
        assertEquals(ChangePasswordEffect.NavigateToLogin, viewModel.effects.first())
        assertTrue(notices.consumePasswordChanged())
        assertFalse(notices.consumePasswordChanged())
    }

    // ---- Huella (paso 6)

    @Test
    fun `con la huella activa pide el dialogo y al aceptar la vuelve a envolver`() = scope.runTest {
        change.result = ChangePasswordResult.Success(biometricWasEnabled = true)
        biometric.enablePreparation = BiometricPreparation.Ready(cipher())
        create()
        typeNew()
        submit()
        assertTrue(viewModel.effects.first() is ChangePasswordEffect.ShowBiometricPrompt)
        assertTrue(state.biometricPending)
        assertTrue(state.isBusy)
        viewModel.onAction(ChangePasswordAction.BiometricAuthenticated(cipher()))
        runCurrent()
        assertEquals(1, biometric.completeEnableCalls)
        assertEquals(ChangePasswordEffect.Finish(biometricOff = false), viewModel.effects.first())
        assertFalse(state.biometricPending)
    }

    @Test
    fun `cancelar la huella la desactiva y avisa`() = scope.runTest {
        change.result = ChangePasswordResult.Success(biometricWasEnabled = true)
        biometric.enablePreparation = BiometricPreparation.Ready(cipher())
        create()
        typeNew()
        submit()
        viewModel.effects.first()
        viewModel.onAction(ChangePasswordAction.BiometricCancelled)
        runCurrent()
        assertEquals(1, biometric.disableCalls)
        assertEquals(ChangePasswordEffect.Finish(biometricOff = true), viewModel.effects.first())
    }

    @Test
    fun `si volver a envolver falla se desactiva y avisa`() = scope.runTest {
        change.result = ChangePasswordResult.Success(biometricWasEnabled = true)
        biometric.enablePreparation = BiometricPreparation.Ready(cipher())
        biometric.enableResult = BiometricResult.Failed
        create()
        typeNew()
        submit()
        viewModel.effects.first()
        viewModel.onAction(ChangePasswordAction.BiometricAuthenticated(cipher()))
        runCurrent()
        assertEquals(1, biometric.disableCalls)
        assertEquals(ChangePasswordEffect.Finish(biometricOff = true), viewModel.effects.first())
    }

    @Test
    fun `si no se puede preparar la huella se desactiva sin dialogo`() = scope.runTest {
        change.result = ChangePasswordResult.Success(biometricWasEnabled = true)
        biometric.enablePreparation = BiometricPreparation.NoneEnrolled
        create()
        typeNew()
        submit()
        assertEquals(ChangePasswordEffect.Finish(biometricOff = true), viewModel.effects.first())
        assertEquals(1, biometric.disableCalls)
    }

    @Test
    fun `sin huella activa no se toca la huella`() = scope.runTest {
        create()
        typeNew()
        submit()
        assertEquals(0, biometric.prepareEnableCalls)
        assertEquals(0, biometric.disableCalls)
    }

    @Test
    fun `toString y las acciones no imprimen contrasenas ni codigos`() = scope.runTest {
        twoFactor.enabled = true
        create()
        viewModel.onAction(ChangePasswordAction.CodeChanged("482916"))
        typeNew()
        val printed = state.toString()
        assertFalse(printed.contains(NEW))
        assertFalse(printed.contains("482916"))
        assertFalse(ChangePasswordAction.Submit(CURRENT, NEW, NEW).toString().contains(NEW))
        assertFalse(ChangePasswordAction.NewPasswordsChanged(NEW, NEW).toString().contains(NEW))
        assertFalse(ChangePasswordAction.CodeChanged("482916").toString().contains("482916"))
    }
}
