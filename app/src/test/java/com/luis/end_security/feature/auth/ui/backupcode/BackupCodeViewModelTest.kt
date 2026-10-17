package com.luis.end_security.feature.auth.ui.backupcode

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
class BackupCodeViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)
    private lateinit var viewModel: BackupCodeViewModel

    private val state get() = viewModel.state.value

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel = BackupCodeViewModel(dispatcher.scheduler.timeSource)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `entrar se habilita solo con 8 caracteres`() {
        viewModel.onAction(BackupCodeAction.CodeLengthChanged(7))
        assertFalse(state.canSubmit)
        viewModel.onAction(BackupCodeAction.CodeLengthChanged(8))
        assertTrue(state.canSubmit)
    }

    @Test
    fun `codigo invalido muestra el error y pide vaciar el campo`() {
        viewModel.onAction(BackupCodeAction.CodeLengthChanged(8))
        val before = viewModel.clearRequests.value

        viewModel.onResult(SecondFactorResult.Rejected)

        assertTrue(state.invalidCode)
        assertEquals(before + 1, viewModel.clearRequests.value)
    }

    @Test
    fun `vaciar el campo no quita el error pero escribir si`() {
        viewModel.onResult(SecondFactorResult.Rejected)
        viewModel.onAction(BackupCodeAction.CodeLengthChanged(0))
        assertTrue("al vaciarse sigue el error", state.invalidCode)
        viewModel.onAction(BackupCodeAction.CodeLengthChanged(1))
        assertFalse(state.invalidCode)
    }

    @Test
    fun `el bloqueo deshabilita entrar, vacia el campo y cuenta hacia atras`() = scope.runTest {
        viewModel.onAction(BackupCodeAction.CodeLengthChanged(8))
        val before = viewModel.clearRequests.value
        viewModel.onResult(SecondFactorResult.LockedOut(minutes = 9))
        runCurrent()

        assertTrue(state.isLockedOut)
        assertFalse(state.canSubmit)
        assertEquals(before + 1, viewModel.clearRequests.value)

        advanceTimeBy(9 * 60_000L)
        runCurrent()
        assertFalse(state.isLockedOut)
    }

    @Test
    fun `la accion de enviar no imprime el codigo`() {
        assertFalse(BackupCodeAction.Submit(StringBuilder("K7QD2M9X")).toString().contains("K7QD"))
    }

    @Test
    fun `entrar con un codigo valido va a la boveda`() = scope.runTest {
        viewModel.onAction(BackupCodeAction.CodeLengthChanged(8))
        viewModel.onAction(BackupCodeAction.Submit(StringBuilder("K7QD2M9X")))
        assertEquals(BackupCodeEffect.NavigateToVault, viewModel.effects.first())
    }
}
