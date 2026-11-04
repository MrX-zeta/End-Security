package com.luis.end_security.feature.account.ui

import com.luis.end_security.core.common.ApiError
import com.luis.end_security.core.common.ApiResult
import com.luis.end_security.core.common.DeviceClock
import com.luis.end_security.core.security.SecretClipboard
import com.luis.end_security.feature.account.domain.TwoFactorRepository
import com.luis.end_security.feature.account.domain.TwoFactorSetup
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

private const val SECRET = "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP"
private val CODES = List(10) { "AB${it}D-92QX" }

/** Repositorio programable: guarda lo que recibe para comprobar que con errores no se llama al servidor. */
private class FakeTwoFactorRepository : TwoFactorRepository {
    var enabled: Boolean? = false
    var setupResult: ApiResult<TwoFactorSetup> =
        ApiResult.Success(TwoFactorSetup(SECRET, "End Security", "ana@correo.com", "SHA1", 6, 30))
    var confirmResult: ApiResult<List<String>> = ApiResult.Success(CODES)
    var disableResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var regenerateResult: ApiResult<List<String>> = ApiResult.Success(CODES.reversed())
    var setupCalls = 0
    val confirmed = mutableListOf<String>()
    var disableCalls = 0
    var regenerateCalls = 0
    var lastPassword: String? = null
    var lastTotp: String? = null

    override suspend fun isEnabled() = enabled

    override suspend fun startSetup(): ApiResult<TwoFactorSetup> {
        setupCalls++
        return setupResult
    }

    override suspend fun confirm(code: String): ApiResult<List<String>> {
        confirmed += code
        return confirmResult
    }

    override suspend fun disable(password: CharSequence, totpCode: String): ApiResult<Unit> {
        disableCalls++
        lastPassword = password.toString()
        lastTotp = totpCode
        return disableResult
    }

    override suspend fun regenerateRecoveryCodes(password: CharSequence, totpCode: String): ApiResult<List<String>> {
        regenerateCalls++
        lastPassword = password.toString()
        lastTotp = totpCode
        return regenerateResult
    }
}

private class FakeClipboard : SecretClipboard {
    val copied = mutableListOf<String>()
    override suspend fun copy(text: String): Int {
        copied += text
        return 30
    }
}

private class MutableClock(var millis: Long = 0) : DeviceClock {
    override fun wallMillis() = millis
    override fun elapsedRealtimeMillis() = millis
}

@OptIn(ExperimentalCoroutinesApi::class)
class TwoFactorSetupViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)
    private val repo = FakeTwoFactorRepository()
    private val clipboard = FakeClipboard()
    private val clock = MutableClock(18_000L) // 18 s dentro del periodo de 30 s
    private lateinit var viewModel: TwoFactorSetupViewModel

    private val state get() = viewModel.state.value

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Al terminar detiene el contador de segundos: sin eso `runTest` esperaría un bucle sin fin de tiempo virtual. */
    private fun runVm(block: suspend TestScope.() -> Unit) = scope.runTest {
        try {
            block()
        } finally {
            if (::viewModel.isInitialized && state.step == SetupStep.Code) viewModel.onAction(SetupAction.Back)
        }
    }

    private fun TestScope.create() {
        viewModel = TwoFactorSetupViewModel(repo, clipboard, clock, dispatcher.scheduler.timeSource)
        runCurrent()
    }

    private fun TestScope.toCodeStep() {
        create()
        viewModel.onAction(SetupAction.Continue)
        runCurrent()
    }

    @Test
    fun `al entrar pide la clave y la muestra en grupos de 4 de dos en dos`() = runVm {
        create()
        assertEquals(1, repo.setupCalls)
        assertFalse(state.isLoadingKey)
        assertEquals("JBSW Y3DP\nEHPK 3PXP\nJBSW Y3DP\nEHPK 3PXP", state.keyDisplay)
        assertEquals(SetupStep.Key, state.step)
    }

    @Test
    fun `mientras responde EP-19 esta cargando`() = runVm {
        viewModel = TwoFactorSetupViewModel(repo, clipboard, clock, dispatcher.scheduler.timeSource)
        assertTrue(state.isLoadingKey)
        assertNull(state.keyDisplay)
        runCurrent()
        assertFalse(state.isLoadingKey)
    }

    @Test
    fun `sin red o con error no hay clave y se puede reintentar`() = runVm {
        repo.setupResult = ApiResult.Failure(ApiError.NetworkUnavailable)
        create()
        assertTrue(state.keyError)
        assertNull(state.keyDisplay)
        repo.setupResult = ApiResult.Success(TwoFactorSetup(SECRET, "End Security", "a@b.c", "SHA1", 6, 30))
        viewModel.onAction(SetupAction.Retry)
        runCurrent()
        assertFalse(state.keyError)
        assertEquals(2, repo.setupCalls)
    }

    @Test
    fun `copiar la clave usa el portapapeles sensible con la clave sin espacios`() = runVm {
        create()
        viewModel.onAction(SetupAction.CopyKey)
        runCurrent()
        assertEquals(listOf(SECRET), clipboard.copied)
        assertEquals(30, state.copiedSeconds)
        advanceTimeBy(3_500)
        assertNull(state.copiedSeconds)
    }

    @Test
    fun `abrir la app de autenticacion manda el URI otpauth completo`() = runVm {
        create()
        viewModel.onAction(SetupAction.OpenAuthenticator)
        val effect = viewModel.effects.first() as SetupEffect.OpenUri
        assertTrue(effect.uri.startsWith("otpauth://totp/End%20Security%3Aana%40correo.com?secret=$SECRET"))
        assertTrue(effect.uri.contains("issuer=End%20Security"))
        assertTrue(effect.uri.contains("algorithm=SHA1"))
        assertTrue(effect.uri.contains("digits=6"))
        assertTrue(effect.uri.contains("period=30"))
        assertFalse(effect.toString().contains(SECRET))
    }

    @Test
    fun `el paso 2 cuenta los segundos que faltan con el reloj del telefono`() = runVm {
        toCodeStep()
        assertEquals(SetupStep.Code, state.step)
        assertEquals(12, state.secondsLeft)
        clock.millis = 29_000L
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(1, state.secondsLeft)
        clock.millis = 30_000L
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(30, state.secondsLeft)
    }

    @Test
    fun `confirmar con menos de 6 digitos marca el campo, enfoca y no llama al servidor`() = runVm {
        toCodeStep()
        viewModel.onAction(SetupAction.CodeChanged("482"))
        assertFalse(state.showIncomplete)
        val before = state.focusRequest
        viewModel.onAction(SetupAction.Confirm)
        runCurrent()
        assertTrue(state.showIncomplete)
        assertTrue(state.focusRequest > before)
        assertTrue(repo.confirmed.isEmpty())
        viewModel.onAction(SetupAction.CodeChanged("482916"))
        assertFalse(state.showIncomplete)
    }

    @Test
    fun `un codigo incorrecto lo avisa y vacia el campo`() = runVm {
        repo.confirmResult = ApiResult.Failure(ApiError.InvalidCode(null))
        toCodeStep()
        viewModel.onAction(SetupAction.CodeChanged("000000"))
        viewModel.onAction(SetupAction.Confirm)
        runCurrent()
        assertTrue(state.wrongCode)
        assertEquals("", state.code)
        assertEquals(SetupStep.Code, state.step)
        viewModel.onAction(SetupAction.CodeChanged("1"))
        assertFalse(state.wrongCode)
    }

    @Test
    fun `el limite de intentos muestra el bloqueo`() = runVm {
        repo.confirmResult = ApiResult.Failure(ApiError.RateLimited(900))
        toCodeStep()
        viewModel.onAction(SetupAction.CodeChanged("000000"))
        viewModel.onAction(SetupAction.Confirm)
        runCurrent()
        assertTrue(state.isLockedOut)
        assertEquals(900, state.lockoutSecondsLeft)
        // Bloqueado: no vuelve a llamar.
        viewModel.onAction(SetupAction.CodeChanged("111111"))
        viewModel.onAction(SetupAction.Confirm)
        runCurrent()
        assertEquals(1, repo.confirmed.size)
    }

    @Test
    fun `sin red al confirmar sale el error generico`() = runVm {
        repo.confirmResult = ApiResult.Failure(ApiError.NetworkUnavailable)
        toCodeStep()
        viewModel.onAction(SetupAction.CodeChanged("482916"))
        viewModel.onAction(SetupAction.Confirm)
        runCurrent()
        assertTrue(state.genericError)
        assertEquals(SetupStep.Code, state.step)
    }

    @Test
    fun `confirmar con exito pasa al paso 3 con los 10 codigos y suelta la clave`() = runVm {
        toCodeStep()
        viewModel.onAction(SetupAction.CodeChanged("482916"))
        viewModel.onAction(SetupAction.Confirm)
        runCurrent()
        assertEquals(listOf("482916"), repo.confirmed)
        assertEquals(SetupStep.Codes, state.step)
        assertEquals(CODES, state.codes)
        assertNull(state.keyDisplay)
        assertEquals("", state.code)
    }

    @Test
    fun `copiar los codigos manda uno por linea`() = runVm {
        toCodeStep()
        viewModel.onAction(SetupAction.CodeChanged("482916"))
        viewModel.onAction(SetupAction.Confirm)
        runCurrent()
        viewModel.onAction(SetupAction.CopyCodes)
        runCurrent()
        assertEquals(CODES.joinToString("\n"), clipboard.copied.single())
    }

    @Test
    fun `atras en el paso 2 vuelve al paso 1 y no sale`() = runVm {
        toCodeStep()
        viewModel.onAction(SetupAction.CodeChanged("48"))
        viewModel.onAction(SetupAction.Back)
        assertEquals(SetupStep.Key, state.step)
        assertEquals("", state.code)
    }

    @Test
    fun `atras en el paso 3 sale del flujo y nunca regresa al paso 2`() = runVm {
        toCodeStep()
        viewModel.onAction(SetupAction.CodeChanged("482916"))
        viewModel.onAction(SetupAction.Confirm)
        runCurrent()
        viewModel.onAction(SetupAction.Back)
        assertEquals(SetupEffect.Exit, viewModel.effects.first())
        assertNotStep(SetupStep.Code)
    }

    private fun assertNotStep(step: SetupStep) = assertTrue(state.step != step)

    @Test
    fun `ya los guarde sale y vacia clave y codigos`() = runVm {
        toCodeStep()
        viewModel.onAction(SetupAction.CodeChanged("482916"))
        viewModel.onAction(SetupAction.Confirm)
        runCurrent()
        viewModel.onAction(SetupAction.Finish)
        assertEquals(SetupEffect.Exit, viewModel.effects.first())
        assertTrue(state.codes.isEmpty())
        assertNull(state.keyDisplay)
    }

    @Test
    fun `salir en el paso 1 vacia la clave`() = runVm {
        create()
        viewModel.onAction(SetupAction.Back)
        assertEquals(SetupEffect.Exit, viewModel.effects.first())
        assertNull(state.keyDisplay)
    }

    @Test
    fun `volver a entrar pide una clave nueva`() = runVm {
        create()
        val first = state.keyDisplay
        repo.setupResult = ApiResult.Success(TwoFactorSetup("MFRGGZDFMZTWQ2LKNNWG23TPOBYXE43U", "End Security", "ana@correo.com", "SHA1", 6, 30))
        create()
        assertEquals(2, repo.setupCalls)
        assertTrue(first != state.keyDisplay)
    }

    @Test
    fun `toString no imprime secretos`() = runVm {
        toCodeStep()
        viewModel.onAction(SetupAction.CodeChanged("482916"))
        viewModel.onAction(SetupAction.Confirm)
        runCurrent()
        val printed = state.toString()
        assertFalse(printed.contains(SECRET))
        assertFalse(printed.contains("482916"))
        assertFalse(printed.contains(CODES.first()))
        assertFalse(SetupAction.CodeChanged("482916").toString().contains("482916"))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class TwoFactorManageViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)
    private val repo = FakeTwoFactorRepository()
    private val clipboard = FakeClipboard()
    private lateinit var viewModel: TwoFactorManageViewModel

    private val state get() = viewModel.state.value

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel = TwoFactorManageViewModel(repo, clipboard, dispatcher.scheduler.timeSource)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.submit(password: String = "contraseña", code: String = "482916") {
        viewModel.onAction(ManageAction.PasswordEmptinessChanged(password.isEmpty()))
        viewModel.onAction(ManageAction.CodeChanged(code))
        viewModel.onAction(ManageAction.Submit(password))
        runCurrent()
    }

    @Test
    fun `las dos acciones abren la hoja con su proposito`() {
        viewModel.onAction(ManageAction.RegenerateClicked)
        assertEquals(ReauthPurpose.Regenerate, state.sheet)
        viewModel.onAction(ManageAction.SheetDismissed)
        assertNull(state.sheet)
        viewModel.onAction(ManageAction.DisableClicked)
        assertEquals(ReauthPurpose.Disable, state.sheet)
    }

    @Test
    fun `con la contrasena vacia marca el campo, enfoca y no llama al servidor`() = scope.runTest {
        viewModel.onAction(ManageAction.DisableClicked)
        submit(password = "", code = "482916")
        assertTrue(state.showPasswordRequired)
        assertEquals(ReauthField.Password, state.focusRequest?.field)
        assertEquals(0, repo.disableCalls)
        viewModel.onAction(ManageAction.PasswordEmptinessChanged(false))
        assertFalse(state.showPasswordRequired)
    }

    @Test
    fun `con el codigo incompleto lo marca y enfoca el codigo`() = scope.runTest {
        viewModel.onAction(ManageAction.RegenerateClicked)
        submit(code = "48")
        assertTrue(state.showCodeIncomplete)
        assertFalse(state.showPasswordRequired)
        assertEquals(ReauthField.Code, state.focusRequest?.field)
        assertEquals(0, repo.regenerateCalls)
        viewModel.onAction(ManageAction.CodeChanged("482916"))
        assertFalse(state.showCodeIncomplete)
    }

    @Test
    fun `todo vacio marca los dos y enfoca la contrasena`() = scope.runTest {
        viewModel.onAction(ManageAction.DisableClicked)
        submit(password = "", code = "")
        assertTrue(state.showPasswordRequired)
        assertTrue(state.showCodeIncomplete)
        assertEquals(ReauthField.Password, state.focusRequest?.field)
    }

    @Test
    fun `desactivar manda la contrasena y el codigo, cierra la hoja y avisa`() = scope.runTest {
        viewModel.onAction(ManageAction.DisableClicked)
        submit()
        assertEquals(1, repo.disableCalls)
        assertEquals("contraseña", repo.lastPassword)
        assertEquals("482916", repo.lastTotp)
        assertNull(state.sheet)
        assertEquals(ManageEffect.Disabled, viewModel.effects.first())
    }

    @Test
    fun `reauth_required marca los dos campos y vacia el codigo`() = scope.runTest {
        repo.disableResult = ApiResult.Failure(ApiError.ReauthRequired)
        viewModel.onAction(ManageAction.DisableClicked)
        submit()
        assertTrue(state.credentialsWrong)
        assertEquals("", state.code)
        assertEquals(ReauthPurpose.Disable, state.sheet)
        // Al corregir, se quita.
        viewModel.onAction(ManageAction.CodeChanged("1"))
        assertFalse(state.credentialsWrong)
    }

    @Test
    fun `sin red sale el error generico`() = scope.runTest {
        repo.regenerateResult = ApiResult.Failure(ApiError.NetworkUnavailable)
        viewModel.onAction(ManageAction.RegenerateClicked)
        submit()
        assertTrue(state.genericError)
        assertFalse(state.credentialsWrong)
        assertFalse(state.isLoading)
    }

    @Test
    fun `el limite de intentos bloquea`() = scope.runTest {
        repo.regenerateResult = ApiResult.Failure(ApiError.RateLimited(900))
        viewModel.onAction(ManageAction.RegenerateClicked)
        submit()
        assertEquals(900, state.lockoutSecondsLeft)
        viewModel.onAction(ManageAction.Submit("contraseña"))
        runCurrent()
        assertEquals(1, repo.regenerateCalls)
    }

    @Test
    fun `regenerar muestra los codigos nuevos y cierra la hoja`() = scope.runTest {
        viewModel.onAction(ManageAction.RegenerateClicked)
        submit()
        assertEquals(CODES.reversed(), state.newCodes)
        assertNull(state.sheet)
        viewModel.onAction(ManageAction.CopyCodes)
        runCurrent()
        assertEquals(CODES.reversed().joinToString("\n"), clipboard.copied.single())
        viewModel.onAction(ManageAction.CodesClosed)
        assertNull(state.newCodes)
    }

    @Test
    fun `cerrar la hoja descarta lo escrito`() = scope.runTest {
        viewModel.onAction(ManageAction.RegenerateClicked)
        viewModel.onAction(ManageAction.CodeChanged("482916"))
        viewModel.onAction(ManageAction.SheetDismissed)
        viewModel.onAction(ManageAction.RegenerateClicked)
        assertEquals("", state.code)
    }

    @Test
    fun `toString no imprime codigos ni la contrasena`() = scope.runTest {
        viewModel.onAction(ManageAction.RegenerateClicked)
        submit()
        val printed = state.toString()
        assertFalse(printed.contains(CODES.first()))
        assertFalse(printed.contains("482916"))
        assertFalse(ManageAction.Submit("contraseña").toString().contains("contraseña"))
        assertFalse(ManageAction.CodeChanged("482916").toString().contains("482916"))
    }
}
