package com.luis.end_security.feature.settings.ui

import com.luis.end_security.core.crypto.FakeBiometricAvailability
import com.luis.end_security.feature.auth.domain.FakeAuthRepository
import com.luis.end_security.feature.settings.domain.AccountProfile
import com.luis.end_security.feature.settings.domain.SettingsRepository
import com.luis.end_security.feature.unlock.FakeBiometricUnlock
import com.luis.end_security.feature.unlock.domain.BiometricPreparation
import com.luis.end_security.feature.unlock.domain.BiometricResult
import com.luis.end_security.feature.unlock.domain.BiometricStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private class FakeSettings : SettingsRepository {
        val profileFlow = MutableStateFlow<AccountProfile?>(AccountProfile("Ana López", "ana@correo.com"))
        override val profile = profileFlow
        val minutes = MutableStateFlow(5)
        override val autolockMinutes = minutes
        var twoFactor: Boolean? = true
        var devices: Int? = 3
        var unsynced = 0

        /** Lo que queda sin subir tras pedir la sincronización. */
        var unsyncedAfterSync: Int? = null
        var syncCalls = 0
        var syncTimeout = 0L

        override suspend fun setAutolockMinutes(minutes: Int) {
            this.minutes.value = minutes
        }

        override suspend fun twoFactorEnabled() = twoFactor

        override suspend fun deviceCount() = devices

        override suspend fun unsyncedCount() = unsynced

        override suspend fun syncNow(timeoutMillis: Long) {
            syncCalls++
            syncTimeout = timeoutMillis
            unsyncedAfterSync?.let { unsynced = it }
        }
    }

    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)
    private val repo = FakeSettings()
    private val biometric = FakeBiometricUnlock()
    private val availability = FakeBiometricAvailability()
    private val auth = FakeAuthRepository()
    private lateinit var viewModel: SettingsViewModel

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
        viewModel = SettingsViewModel(repo, biometric, availability, auth)
        runCurrent()
    }

    private fun cipher(): Cipher = Cipher.getInstance("AES/GCM/NoPadding")

    @Test
    fun `muestra la cuenta, el estado de 2 pasos y el numero de dispositivos`() = scope.runTest {
        create()
        assertEquals("Ana López", state.profile?.title)
        assertEquals(true, state.twoFactorEnabled)
        assertEquals(3, state.deviceCount)
        assertEquals(5, state.autolockMinutes)
    }

    @Test
    fun `sin red o con error no hay valor de 2 pasos ni de dispositivos`() = scope.runTest {
        repo.twoFactor = null
        repo.devices = null
        create()
        assertNull(state.twoFactorEnabled)
        assertNull(state.deviceCount)
    }

    @Test
    fun `2 pasos inactiva se refleja`() = scope.runTest {
        repo.twoFactor = false
        create()
        assertEquals(false, state.twoFactorEnabled)
    }

    @Test
    fun `las filas de tareas futuras solo avisan Disponible pronto`() = scope.runTest {
        create()
        val before = state.comingSoonCount
        for (row in FutureRow.entries) viewModel.onAction(SettingsAction.FutureRowClicked(row))
        assertEquals(before + FutureRow.entries.size, state.comingSoonCount)
        assertNull(state.signOutSheet)
        assertFalse(state.showAutoLockSheet)
    }

    @Test
    fun `sin sensor la fila de la huella no se muestra`() = scope.runTest {
        availability.status = BiometricStatus.NoHardware
        create()
        assertFalse(state.biometricRowVisible)
    }

    @Test
    fun `con sensor la fila se muestra`() = scope.runTest {
        create()
        assertTrue(state.biometricRowVisible)
    }

    // ---- Bloqueo automático

    @Test
    fun `tocar la fila abre la hoja y elegir una opcion la guarda y la cierra`() = scope.runTest {
        create()
        viewModel.onAction(SettingsAction.AutoLockClicked)
        assertTrue(state.showAutoLockSheet)
        viewModel.onAction(SettingsAction.AutoLockSelected(15))
        runCurrent()
        assertFalse(state.showAutoLockSheet)
        assertEquals(15, repo.minutes.value)
        assertEquals(15, state.autolockMinutes)
    }

    @Test
    fun `cada opcion permitida se puede guardar`() = scope.runTest {
        create()
        for (minutes in listOf(0, 1, 5, 15, 30)) {
            viewModel.onAction(SettingsAction.AutoLockSelected(minutes))
            runCurrent()
            assertEquals(minutes, repo.minutes.value)
        }
    }

    // ---- Huella: activar y desactivar

    @Test
    fun `activar con huellas pide el dialogo con el cipher de cifrado`() = scope.runTest {
        val cipher = cipher()
        biometric.enablePreparation = BiometricPreparation.Ready(cipher)
        create()
        viewModel.onAction(SettingsAction.BiometricToggled(true))
        runCurrent()
        val effect = viewModel.effects.first() as SettingsEffect.ShowBiometricPrompt
        assertTrue(effect.cipher === cipher)
        assertTrue(state.biometricBusy)
        // La opción aún no está encendida hasta que la huella se acepte.
        assertFalse(state.biometricEnabled)
    }

    @Test
    fun `al aceptar la huella se envuelve la llave y queda activada`() = scope.runTest {
        biometric.enablePreparation = BiometricPreparation.Ready(cipher())
        create()
        viewModel.onAction(SettingsAction.BiometricToggled(true))
        runCurrent()
        viewModel.onAction(SettingsAction.BiometricAuthenticated(cipher()))
        runCurrent()
        assertEquals(1, biometric.completeEnableCalls)
        assertTrue(state.biometricEnabled)
        assertFalse(state.biometricBusy)
    }

    @Test
    fun `si envolver falla no queda a medias`() = scope.runTest {
        biometric.enablePreparation = BiometricPreparation.Ready(cipher())
        biometric.enableResult = BiometricResult.Failed
        create()
        viewModel.onAction(SettingsAction.BiometricToggled(true))
        runCurrent()
        viewModel.onAction(SettingsAction.BiometricAuthenticated(cipher()))
        runCurrent()
        assertFalse(state.biometricEnabled)
        assertEquals(1, biometric.disableCalls)
    }

    @Test
    fun `cancelar el dialogo vuelve el interruptor a apagado sin mensaje`() = scope.runTest {
        biometric.enablePreparation = BiometricPreparation.Ready(cipher())
        create()
        viewModel.onAction(SettingsAction.BiometricToggled(true))
        runCurrent()
        viewModel.onAction(SettingsAction.BiometricCancelled)
        runCurrent()
        // La llave creada al preparar no se deja huérfana.
        assertEquals(1, biometric.disableCalls)
        assertFalse(state.biometricBusy)
        assertFalse(state.biometricEnabled)
        assertFalse(state.biometricNeedsEnrollment)
        assertEquals(0, biometric.completeEnableCalls)
    }

    @Test
    fun `sin huellas registradas no se enciende y aparece el aviso`() = scope.runTest {
        biometric.enablePreparation = BiometricPreparation.NoneEnrolled
        create()
        viewModel.onAction(SettingsAction.BiometricToggled(true))
        runCurrent()
        assertTrue(state.biometricNeedsEnrollment)
        assertFalse(state.biometricEnabled)
        assertFalse(state.biometricBusy)
    }

    @Test
    fun `el aviso se quita al registrar una huella y volver a la pantalla`() = scope.runTest {
        biometric.enablePreparation = BiometricPreparation.NoneEnrolled
        availability.status = BiometricStatus.NoneEnrolled
        create()
        viewModel.onAction(SettingsAction.BiometricToggled(true))
        runCurrent()
        assertTrue(state.biometricNeedsEnrollment)
        availability.status = BiometricStatus.Available
        viewModel.onAction(SettingsAction.Resumed)
        assertFalse(state.biometricNeedsEnrollment)
    }

    @Test
    fun `desactivar no pide confirmacion y borra todo`() = scope.runTest {
        biometric.enabledFlow.value = true
        create()
        viewModel.onAction(SettingsAction.BiometricToggled(false))
        runCurrent()
        assertEquals(1, biometric.disableCalls)
        assertFalse(state.biometricEnabled)
    }

    // ---- Cerrar sesión

    @Test
    fun `sin cambios pendientes sale la hoja normal`() = scope.runTest {
        create()
        viewModel.onAction(SettingsAction.SignOutClicked)
        runCurrent()
        assertEquals(SignOutSheet.Confirm, state.signOutSheet)
        assertEquals(0, repo.syncCalls)
    }

    @Test
    fun `con cambios sin subir pide sincronizar hasta 5 segundos`() = scope.runTest {
        repo.unsynced = 2
        create()
        viewModel.onAction(SettingsAction.SignOutClicked)
        runCurrent()
        assertEquals(1, repo.syncCalls)
        assertEquals(5_000L, repo.syncTimeout)
        assertEquals(SignOutSheet.Unsynced(2), state.signOutSheet)
    }

    @Test
    fun `si la sincronizacion sube todo sale la hoja normal`() = scope.runTest {
        repo.unsynced = 2
        repo.unsyncedAfterSync = 0
        create()
        viewModel.onAction(SettingsAction.SignOutClicked)
        runCurrent()
        assertEquals(SignOutSheet.Confirm, state.signOutSheet)
    }

    @Test
    fun `pendientes y conflictos cuentan juntos`() = scope.runTest {
        repo.unsynced = 3
        create()
        viewModel.onAction(SettingsAction.SignOutClicked)
        runCurrent()
        assertEquals(SignOutSheet.Unsynced(3), state.signOutSheet)
    }

    @Test
    fun `cancelar cierra la hoja sin cerrar sesion`() = scope.runTest {
        create()
        viewModel.onAction(SettingsAction.SignOutClicked)
        runCurrent()
        viewModel.onAction(SettingsAction.SignOutDismissed)
        assertNull(state.signOutSheet)
        assertFalse(auth.signedOut)
    }

    @Test
    fun `confirmar cierra la sesion y va a iniciar sesion`() = scope.runTest {
        create()
        viewModel.onAction(SettingsAction.SignOutClicked)
        runCurrent()
        viewModel.onAction(SettingsAction.SignOutConfirmed)
        runCurrent()
        assertTrue(auth.signedOut)
        assertEquals(SettingsEffect.NavigateToLogin, viewModel.effects.first())
    }
}
