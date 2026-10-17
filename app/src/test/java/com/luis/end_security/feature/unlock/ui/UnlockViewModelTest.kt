package com.luis.end_security.feature.unlock.ui

import com.luis.end_security.core.datastore.UnlockLockout
import com.luis.end_security.feature.unlock.domain.UnlockAttemptRepository
import com.luis.end_security.feature.unlock.domain.UnlockSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class UnlockViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)
    private val settings = FakeUnlockSettings()
    private val attempts = FakeUnlockAttempts()
    private lateinit var viewModel: UnlockViewModel

    private val state get() = viewModel.state.value

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel = UnlockViewModel(settings, attempts, dispatcher.scheduler.timeSource)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun typePassword() = viewModel.onAction(UnlockAction.PasswordEmptinessChanged(isEmpty = false))

    @Test
    fun `desbloquear se habilita solo con la contrasena escrita`() {
        assertFalse(state.canSubmit)
        typePassword()
        assertTrue(state.canSubmit)
        viewModel.onAction(UnlockAction.PasswordEmptinessChanged(isEmpty = true))
        assertFalse(state.canSubmit)
    }

    @Test
    fun `contrasena incorrecta muestra el error, cuenta el fallo y pide vaciar el campo`() = scope.runTest {
        typePassword()
        viewModel.onWrongPassword()
        runCurrent()
        assertTrue(state.wrongPassword)
        assertEquals(1, attempts.failures)
        assertEquals(1, viewModel.clearRequests.value)
    }

    @Test
    fun `el error sigue con el campo vacio y se quita al escribir otra vez`() = scope.runTest {
        typePassword()
        viewModel.onWrongPassword()
        runCurrent()
        viewModel.onAction(UnlockAction.PasswordEmptinessChanged(isEmpty = true))
        assertTrue(state.wrongPassword)
        typePassword()
        assertFalse(state.wrongPassword)
    }

    @Test
    fun `el quinto fallo bloquea 1 min y desbloquear vuelve al terminar`() = scope.runTest {
        repeat(5) {
            typePassword()
            viewModel.onWrongPassword()
            runCurrent()
        }
        typePassword()
        assertTrue(state.isLockedOut)
        assertEquals(60, state.lockoutSecondsLeft)
        assertFalse(state.canSubmit)
        assertFalse("el bloqueo reemplaza al error", state.wrongPassword)
        assertEquals(5, viewModel.clearRequests.value)

        advanceTimeBy(30_000)
        runCurrent()
        assertTrue(state.isLockedOut)

        advanceTimeBy(31_000)
        runCurrent()
        assertFalse(state.isLockedOut)
        assertTrue(state.canSubmit)
    }

    @Test
    fun `un bloqueo guardado sigue al abrir la pantalla`() = scope.runTest {
        attempts.remaining = 40.seconds
        viewModel = UnlockViewModel(settings, attempts, dispatcher.scheduler.timeSource)
        runCurrent()
        assertEquals(40, state.lockoutSecondsLeft)
    }

    @Test
    fun `la huella aparece solo con biometric_enabled`() = scope.runTest {
        runCurrent()
        assertFalse(state.biometricAvailable)
        settings.biometricEnabled.value = true
        runCurrent()
        assertTrue(state.biometricAvailable)
        settings.biometricEnabled.value = false
        runCurrent()
        assertFalse(state.biometricAvailable)
    }

    @Test
    fun `cerrar sesion abre la confirmacion y cancelar la cierra`() {
        viewModel.onAction(UnlockAction.SignOutClicked)
        assertTrue(state.showSignOutConfirmation)
        viewModel.onAction(UnlockAction.SignOutDismissed)
        assertFalse(state.showSignOutConfirmation)
    }

    @Test
    fun `confirmar cerrar sesion va a iniciar sesion`() = scope.runTest {
        viewModel.onAction(UnlockAction.SignOutClicked)
        viewModel.onAction(UnlockAction.SignOutConfirmed)
        assertFalse(state.showSignOutConfirmation)
        assertEquals(UnlockEffect.NavigateToLogin, viewModel.effects.first())
    }

    @Test
    fun `el estado no imprime el nombre`() {
        val printed = UnlockUiState(displayName = "Ana").toString()
        assertFalse(printed.contains("Ana"))
    }

    private class FakeUnlockSettings : UnlockSettingsRepository {
        override val biometricEnabled = MutableStateFlow(false)
    }

    /** Lleva la cuenta en memoria con las reglas reales de [UnlockLockout]. */
    private class FakeUnlockAttempts : UnlockAttemptRepository {
        var failures = 0
        var remaining = Duration.ZERO

        override suspend fun remainingLockout(): Duration = remaining

        override suspend fun recordFailure(): Duration {
            failures++
            remaining = UnlockLockout.durationAfter(failures)
            return remaining
        }

        override suspend fun recordSuccess() {
            failures = 0
            remaining = Duration.ZERO
        }
    }
}
