package com.luis.end_security.feature.account.ui

import com.luis.end_security.core.common.ApiResult
import com.luis.end_security.feature.account.domain.DeleteAccount
import com.luis.end_security.feature.account.domain.DeleteAccountResult
import com.luis.end_security.feature.account.domain.TwoFactorRepository
import com.luis.end_security.feature.account.domain.TwoFactorSetup
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

private const val DEL_PASSWORD = "Tortuga-violeta-salta-29-nubes"

private class FakeDelete : DeleteAccount {
    var count = 24
    var result: DeleteAccountResult = DeleteAccountResult.Deleted(cleanupComplete = true)
    var gate: CompletableDeferred<Unit>? = null
    var deleteCalls = 0
    var lastPassword: String? = null
    var lastTotp: String? = null

    override suspend fun itemCount(): Int = count

    override suspend fun delete(password: CharSequence, totpCode: String?): DeleteAccountResult {
        deleteCalls++
        lastPassword = password.toString()
        lastTotp = totpCode
        gate?.await()
        return result
    }
}

private class DeleteFakeTwoFactor : TwoFactorRepository {
    var enabled: Boolean? = false

    override suspend fun isEnabled(): Boolean? = enabled

    override suspend fun startSetup(): ApiResult<TwoFactorSetup> = error("no se usa")
    override suspend fun confirm(code: String): ApiResult<List<String>> = error("no se usa")
    override suspend fun disable(password: CharSequence, totpCode: String): ApiResult<Unit> = error("no se usa")
    override suspend fun regenerateRecoveryCodes(password: CharSequence, totpCode: String): ApiResult<List<String>> = error("no se usa")
}

@OptIn(ExperimentalCoroutinesApi::class)
class DeleteAccountViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)
    private val delete = FakeDelete()
    private val twoFactor = DeleteFakeTwoFactor()
    private lateinit var viewModel: DeleteAccountViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.create() {
        viewModel = DeleteAccountViewModel(delete, twoFactor, dispatcher.scheduler.timeSource)
        runCurrent()
    }

    private val state get() = viewModel.state.value

    private fun TestScope.submit(password: String = DEL_PASSWORD) {
        viewModel.onAction(DeleteAccountAction.PasswordEmptinessChanged(password.isEmpty()))
        viewModel.onAction(DeleteAccountAction.Submit(password))
        runCurrent()
    }

    private fun TestScope.confirm(password: String = DEL_PASSWORD) {
        viewModel.onAction(DeleteAccountAction.Confirm(password))
        runCurrent()
    }

    // --- Campos ---

    @Test
    fun `sin 2FA no pide el codigo`() = scope.runTest {
        twoFactor.enabled = false
        create()
        assertEquals(false, state.twoFactorRequired)
    }

    @Test
    fun `con 2FA activa pide el codigo`() = scope.runTest {
        twoFactor.enabled = true
        create()
        assertEquals(true, state.twoFactorRequired)
    }

    @Test
    fun `contrasena vacia marca el error y no abre la hoja ni llama al servidor`() = scope.runTest {
        create()
        submit(password = "")
        assertTrue(state.showPasswordRequired)
        assertEquals(DeleteField.Password, state.focusRequest?.field)
        assertFalse(state.sheetOpen)
        assertEquals(0, delete.deleteCalls)
    }

    @Test
    fun `con 2FA un codigo incompleto marca el error y no abre la hoja`() = scope.runTest {
        twoFactor.enabled = true
        create()
        viewModel.onAction(DeleteAccountAction.CodeChanged("123"))
        submit()
        assertTrue(state.showCodeIncomplete)
        assertEquals(DeleteField.Code, state.focusRequest?.field)
        assertFalse(state.sheetOpen)
    }

    @Test
    fun `sin 2FA un codigo incompleto no importa`() = scope.runTest {
        create()
        viewModel.onAction(DeleteAccountAction.CodeChanged("123"))
        submit()
        assertFalse(state.showCodeIncomplete)
        assertTrue(state.sheetOpen)
    }

    @Test
    fun `el primer error recibe el foco, primero la contrasena`() = scope.runTest {
        twoFactor.enabled = true
        create()
        submit(password = "")
        assertEquals(DeleteField.Password, state.focusRequest?.field)
        assertTrue(state.showCodeIncomplete)
    }

    @Test
    fun `corregir los campos quita sus errores`() = scope.runTest {
        twoFactor.enabled = true
        create()
        submit(password = "")
        viewModel.onAction(DeleteAccountAction.PasswordEmptinessChanged(false))
        viewModel.onAction(DeleteAccountAction.CodeChanged("123456"))
        assertFalse(state.showPasswordRequired)
        assertFalse(state.showCodeIncomplete)
    }

    // --- La hoja ---

    @Test
    fun `con todo bien abre la hoja con el numero de elementos y no llama al servidor todavia`() = scope.runTest {
        delete.count = 24
        create()
        submit()
        assertEquals(24, state.confirmCount)
        assertEquals(0, delete.deleteCalls)
    }

    @Test
    fun `con cero elementos la hoja lleva 0`() = scope.runTest {
        delete.count = 0
        create()
        submit()
        assertEquals(0, state.confirmCount)
    }

    @Test
    fun `con una contrasena la hoja lleva 1 para el singular`() = scope.runTest {
        delete.count = 1
        create()
        submit()
        assertEquals(1, state.confirmCount)
    }

    @Test
    fun `cancelar cierra la hoja sin llamar al servidor`() = scope.runTest {
        create()
        submit()
        viewModel.onAction(DeleteAccountAction.SheetDismissed)
        assertFalse(state.sheetOpen)
        assertEquals(0, delete.deleteCalls)
    }

    @Test
    fun `sin red no se puede saber si hay 2FA y no abre la hoja`() = scope.runTest {
        twoFactor.enabled = null
        create()
        submit()
        assertTrue(state.genericError)
        assertFalse(state.sheetOpen)
        assertEquals(0, delete.deleteCalls)
    }

    @Test
    fun `si al volver la red se descubre que hay 2FA pide el codigo antes de abrir la hoja`() = scope.runTest {
        twoFactor.enabled = null
        create()
        twoFactor.enabled = true
        submit()
        assertEquals(true, state.twoFactorRequired)
        assertTrue(state.showCodeIncomplete)
        assertFalse(state.sheetOpen)
    }

    // --- Confirmar ---

    @Test
    fun `confirmar manda la contrasena y, con 2FA, el codigo`() = scope.runTest {
        twoFactor.enabled = true
        create()
        viewModel.onAction(DeleteAccountAction.CodeChanged("123456"))
        submit()
        confirm()
        assertEquals(DEL_PASSWORD, delete.lastPassword)
        assertEquals("123456", delete.lastTotp)
    }

    @Test
    fun `sin 2FA no manda codigo`() = scope.runTest {
        create()
        viewModel.onAction(DeleteAccountAction.CodeChanged("123456"))
        submit()
        confirm()
        assertNull(delete.lastTotp)
    }

    @Test
    fun `mientras elimina el boton carga, la hoja no se cierra y no se confirma otra vez`() = scope.runTest {
        create()
        submit()
        val deferred = CompletableDeferred<Unit>()
        delete.gate = deferred
        confirm()
        assertTrue(state.isLoading)
        viewModel.onAction(DeleteAccountAction.SheetDismissed)
        viewModel.onAction(DeleteAccountAction.Confirm(DEL_PASSWORD))
        runCurrent()
        assertTrue("la hoja sigue abierta", state.sheetOpen)
        assertEquals("una sola llamada", 1, delete.deleteCalls)
        deferred.complete(Unit)
        runCurrent()
        assertFalse(state.isLoading)
    }

    @Test
    fun `con el 204 deja de cargar y cierra la hoja, y la navegacion la avisa la puerta`() = scope.runTest {
        create()
        submit()
        confirm()
        assertFalse(state.isLoading)
        assertFalse(state.sheetOpen)
        assertEquals(1, delete.deleteCalls)
    }

    @Test
    fun `con el 204 aunque la limpieza quede a medias navega igual`() = scope.runTest {
        delete.result = DeleteAccountResult.Deleted(cleanupComplete = false)
        create()
        submit()
        confirm()
        assertFalse(state.isLoading)
        assertFalse(state.sheetOpen)
    }

    // --- Errores ---

    @Test
    fun `reauth_required cierra la hoja y vuelve a la pantalla con los campos marcados`() = scope.runTest {
        twoFactor.enabled = true
        delete.result = DeleteAccountResult.WrongCredentials
        create()
        viewModel.onAction(DeleteAccountAction.CodeChanged("123456"))
        submit()
        confirm()
        assertFalse(state.sheetOpen)
        assertTrue(state.credentialsWrong)
        assertEquals("", state.code)
        assertEquals(DeleteField.Password, state.focusRequest?.field)
        assertFalse(state.isLoading)
    }

    @Test
    fun `corregir la contrasena quita el error de credenciales`() = scope.runTest {
        delete.result = DeleteAccountResult.WrongCredentials
        create()
        submit()
        confirm()
        viewModel.onAction(DeleteAccountAction.PasswordEmptinessChanged(false))
        assertFalse(state.credentialsWrong)
    }

    @Test
    fun `una key_version mayor en el servidor muestra el conflicto y no incorrectos`() = scope.runTest {
        delete.result = DeleteAccountResult.KeyVersionConflict
        create()
        submit()
        confirm()
        assertTrue(state.conflict)
        assertFalse(state.credentialsWrong)
        assertFalse(state.sheetOpen)
    }

    @Test
    fun `el limite de intentos muestra la cuenta regresiva y cierra la hoja`() = scope.runTest {
        delete.result = DeleteAccountResult.RateLimited(45)
        create()
        submit()
        confirm()
        assertEquals(45, state.lockoutSecondsLeft)
        assertFalse(state.sheetOpen)
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(35, state.lockoutSecondsLeft)
    }

    @Test
    fun `bloqueado por intentos el boton no abre la hoja`() = scope.runTest {
        delete.result = DeleteAccountResult.RateLimited(45)
        create()
        submit()
        confirm()
        submit()
        assertFalse(state.sheetOpen)
    }

    @Test
    fun `sin red o 5xx el generico va en la hoja y nada se cierra`() = scope.runTest {
        delete.result = DeleteAccountResult.Generic
        create()
        submit()
        confirm()
        assertTrue(state.sheetOpen)
        assertTrue(state.sheetError)
        assertFalse(state.isLoading)
        // Se puede reintentar desde la misma hoja.
        delete.result = DeleteAccountResult.Deleted(true)
        confirm()
        assertFalse(state.sheetOpen)
        assertEquals(2, delete.deleteCalls)
    }

    @Test
    fun `una excepcion inesperada se trata como error generico`() = scope.runTest {
        create()
        submit()
        val failing = object : DeleteAccount {
            override suspend fun itemCount() = 3
            override suspend fun delete(password: CharSequence, totpCode: String?): DeleteAccountResult = error("falló")
        }
        viewModel = DeleteAccountViewModel(failing, twoFactor, dispatcher.scheduler.timeSource)
        runCurrent()
        submit()
        confirm()
        assertTrue(state.sheetError)
    }

    @Test
    fun `el estado no imprime secretos`() = scope.runTest {
        twoFactor.enabled = true
        create()
        viewModel.onAction(DeleteAccountAction.CodeChanged("123456"))
        assertFalse(state.toString().contains("123456"))
        assertFalse(DeleteAccountAction.Submit(DEL_PASSWORD).toString().contains(DEL_PASSWORD))
        assertFalse(DeleteAccountAction.Confirm(DEL_PASSWORD).toString().contains(DEL_PASSWORD))
        assertFalse(DeleteAccountAction.CodeChanged("123456").toString().contains("123456"))
    }
}
