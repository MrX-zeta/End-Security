package com.luis.end_security.feature.settings.ui

import com.luis.end_security.core.crypto.FakeBiometricAvailability
import com.luis.end_security.core.designsystem.component.SignOutSheet
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

        /** El valor del servidor; `null` = sin red. */
        var marketing: Boolean? = false

        /** Si [setMarketingOptIn] falla (sin red o error del servidor). */
        var marketingSaveFails = false
        val marketingSaves = mutableListOf<Boolean>()

        override suspend fun marketingOptIn() = marketing

        override suspend fun setMarketingOptIn(optIn: Boolean): Boolean? {
            marketingSaves += optIn
            if (marketingSaveFails) return null
            marketing = optIn
            return optIn
        }

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
    fun `Novedades y encuestas muestra el valor del servidor`() = scope.runTest {
        repo.marketing = true
        create()
        assertEquals(true, state.marketingOptIn)
        assertFalse(state.marketingBusy)
    }

    @Test
    fun `Novedades y encuestas sin red no tiene valor y tocarlo no hace nada`() = scope.runTest {
        repo.marketing = null
        create()
        assertNull(state.marketingOptIn)
        viewModel.onAction(SettingsAction.MarketingToggled(true))
        runCurrent()
        assertNull(state.marketingOptIn)
        assertTrue(repo.marketingSaves.isEmpty())
        assertNull(state.notice)
    }

    @Test
    fun `activar Novedades y encuestas lo guarda y lo deja activo`() = scope.runTest {
        create()
        viewModel.onAction(SettingsAction.MarketingToggled(true))
        // Se ve el cambio al instante y el interruptor queda ocupado hasta que responde el servidor.
        assertEquals(true, state.marketingOptIn)
        assertTrue(state.marketingBusy)
        runCurrent()
        assertEquals(listOf(true), repo.marketingSaves)
        assertEquals(true, state.marketingOptIn)
        assertFalse(state.marketingBusy)
        assertNull(state.notice)
    }

    @Test
    fun `desactivar Novedades y encuestas lo guarda`() = scope.runTest {
        repo.marketing = true
        create()
        viewModel.onAction(SettingsAction.MarketingToggled(false))
        runCurrent()
        assertEquals(listOf(false), repo.marketingSaves)
        assertEquals(false, state.marketingOptIn)
    }

    @Test
    fun `si Novedades y encuestas no se guarda vuelve a su valor y avisa con el mensaje generico`() = scope.runTest {
        repo.marketingSaveFails = true
        create()
        viewModel.onAction(SettingsAction.MarketingToggled(true))
        runCurrent()
        assertEquals(false, state.marketingOptIn)
        assertFalse(state.marketingBusy)
        assertEquals(NoticeKind.MarketingFailed, state.notice?.kind)
    }

    @Test
    fun `un segundo toque mientras se guarda se ignora`() = scope.runTest {
        create()
        viewModel.onAction(SettingsAction.MarketingToggled(true))
        viewModel.onAction(SettingsAction.MarketingToggled(false))
        runCurrent()
        assertEquals(listOf(true), repo.marketingSaves)
        assertEquals(true, state.marketingOptIn)
    }

    @Test
    fun `al volver a la pantalla Novedades y encuestas se recarga`() = scope.runTest {
        create()
        assertEquals(false, state.marketingOptIn)
        repo.marketing = true
        viewModel.onAction(SettingsAction.Resumed)
        runCurrent()
        assertEquals(true, state.marketingOptIn)
    }

    @Test
    fun `2 pasos inactiva se refleja`() = scope.runTest {
        repo.twoFactor = false
        create()
        assertEquals(false, state.twoFactorEnabled)
    }

    @Test
    fun `Eliminar cuenta con red abre la pantalla`() = scope.runTest {
        create()
        viewModel.onAction(SettingsAction.DeleteAccountClicked)
        runCurrent()
        assertEquals(SettingsEffect.OpenDeleteAccount, viewModel.effects.first())
        assertNull(state.notice)
    }

    @Test
    fun `Eliminar cuenta sin red avisa y no navega`() = scope.runTest {
        create()
        repo.twoFactor = null
        viewModel.onAction(SettingsAction.DeleteAccountClicked)
        runCurrent()
        assertEquals(NoticeKind.NeedsConnectionToDelete, state.notice?.kind)
        // Ningún efecto de navegación: el canal sigue vacío.
        val effect = kotlinx.coroutines.withTimeoutOrNull(1) { viewModel.effects.first() }
        assertNull(effect)
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

    // ---- Tarea 17: entrada a la verificación en dos pasos

    @Test
    fun `con 2 pasos inactiva abre el flujo de activacion`() = scope.runTest {
        repo.twoFactor = false
        create()
        viewModel.onAction(SettingsAction.TwoFactorClicked)
        runCurrent()
        assertEquals(SettingsEffect.OpenTwoFactorSetup, viewModel.effects.first())
    }

    @Test
    fun `con 2 pasos activa abre su pantalla`() = scope.runTest {
        repo.twoFactor = true
        create()
        viewModel.onAction(SettingsAction.TwoFactorClicked)
        runCurrent()
        assertEquals(SettingsEffect.OpenTwoFactorActive, viewModel.effects.first())
    }

    @Test
    fun `sin red avisa que necesita conexion y no navega`() = scope.runTest {
        create()
        repo.twoFactor = null
        viewModel.onAction(SettingsAction.TwoFactorClicked)
        runCurrent()
        assertEquals(NoticeKind.NeedsConnection, state.notice?.kind)
        assertNull(kotlinx.coroutines.withTimeoutOrNull(1_000) { viewModel.effects.first() })
    }

    @Test
    fun `al volver se recarga Activa o Inactiva`() = scope.runTest {
        repo.twoFactor = false
        create()
        assertEquals(false, state.twoFactorEnabled)
        repo.twoFactor = true
        viewModel.onAction(SettingsAction.Resumed)
        runCurrent()
        assertEquals(true, state.twoFactorEnabled)
    }

    @Test
    fun `una falla de red al volver no borra el ultimo valor`() = scope.runTest {
        repo.twoFactor = true
        create()
        repo.twoFactor = null
        viewModel.onAction(SettingsAction.Resumed)
        runCurrent()
        assertEquals(true, state.twoFactorEnabled)
    }

    @Test
    fun `volver de desactivar avisa y recarga`() = scope.runTest {
        repo.twoFactor = true
        create()
        repo.twoFactor = false
        viewModel.onAction(SettingsAction.TwoFactorDisabled)
        runCurrent()
        assertEquals(NoticeKind.TwoFactorDisabled, state.notice?.kind)
        assertEquals(false, state.twoFactorEnabled)
    }

    // ---- Tarea 18: Ajustes no parpadea al cerrar sesión

    @Test
    fun `durante el cierre lo que se ve no cambia aunque cambien la bandera o Room`() = scope.runTest {
        biometric.enabledFlow.value = true
        auth.gate = kotlinx.coroutines.CompletableDeferred()
        create()
        val before = state
        viewModel.onAction(SettingsAction.SignOutClicked)
        runCurrent()
        viewModel.onAction(SettingsAction.SignOutConfirmed)
        runCurrent()
        assertTrue(state.isSigningOut)
        // `signOutLocally` borra la bandera de la huella, la cuenta y los ajustes: nada de eso se refleja.
        biometric.enabledFlow.value = false
        repo.profileFlow.value = null
        repo.minutes.value = 30
        runCurrent()
        assertEquals(before.biometricEnabled, state.biometricEnabled)
        assertEquals(before.profile, state.profile)
        assertEquals(before.autolockMinutes, state.autolockMinutes)
        assertEquals(before.twoFactorEnabled, state.twoFactorEnabled)
        assertEquals(before.deviceCount, state.deviceCount)
        auth.gate!!.complete(Unit)
        runCurrent()
    }

    @Test
    fun `durante el cierre la hoja sigue abierta y no se puede cerrar`() = scope.runTest {
        auth.gate = kotlinx.coroutines.CompletableDeferred()
        create()
        viewModel.onAction(SettingsAction.SignOutClicked)
        runCurrent()
        viewModel.onAction(SettingsAction.SignOutConfirmed)
        runCurrent()
        assertTrue(state.signOutSheet != null)
        viewModel.onAction(SettingsAction.SignOutDismissed)
        assertTrue(state.signOutSheet != null)
        auth.gate!!.complete(Unit)
        runCurrent()
        assertEquals(SettingsEffect.NavigateToLogin, viewModel.effects.first())
    }

    @Test
    fun `al volver de Resumed durante el cierre no se recarga nada`() = scope.runTest {
        repo.twoFactor = true
        auth.gate = kotlinx.coroutines.CompletableDeferred()
        create()
        viewModel.onAction(SettingsAction.SignOutClicked)
        runCurrent()
        viewModel.onAction(SettingsAction.SignOutConfirmed)
        runCurrent()
        repo.twoFactor = false
        viewModel.onAction(SettingsAction.Resumed)
        runCurrent()
        assertEquals(true, state.twoFactorEnabled)
        auth.gate!!.complete(Unit)
        runCurrent()
    }

    // ---- Tarea 18: volver de cambiar la contraseña

    @Test
    fun `volver de cambiar la contrasena avisa que cerramos las otras sesiones`() = scope.runTest {
        create()
        viewModel.onAction(SettingsAction.PasswordChanged(biometricOff = false))
        runCurrent()
        assertEquals(NoticeKind.PasswordChanged, state.notice?.kind)
    }

    @Test
    fun `si la huella quedo desactivada sale un segundo aviso despues del primero`() = scope.runTest {
        create()
        viewModel.onAction(SettingsAction.PasswordChanged(biometricOff = true))
        runCurrent()
        assertEquals(NoticeKind.PasswordChanged, state.notice?.kind)
        advanceTimeBy(SettingsViewModel.BIOMETRIC_NOTICE_DELAY_MILLIS + 100)
        runCurrent()
        assertEquals(NoticeKind.BiometricOff, state.notice?.kind)
    }

    @Test
    fun `si el borrado local falla igual va a iniciar sesion y no se queda congelada`() = scope.runTest {
        auth.signOutError = IllegalStateException("Room falló")
        create()
        viewModel.onAction(SettingsAction.SignOutClicked)
        runCurrent()
        viewModel.onAction(SettingsAction.SignOutConfirmed)
        runCurrent()
        assertEquals(SettingsEffect.NavigateToLogin, viewModel.effects.first())
    }

    @Test
    fun `al volver a Ajustes el numero de dispositivos se recarga`() = scope.runTest {
        create()
        assertEquals(3, state.deviceCount)
        // Se cerró la sesión de otros dos dispositivos desde «Dispositivos».
        repo.devices = 1
        viewModel.onAction(SettingsAction.Resumed)
        runCurrent()
        assertEquals(1, state.deviceCount)
    }

    @Test
    fun `sin red el numero de dispositivos conserva el ultimo conocido`() = scope.runTest {
        create()
        assertEquals(3, state.deviceCount)
        repo.devices = null
        viewModel.onAction(SettingsAction.Resumed)
        runCurrent()
        assertEquals(3, state.deviceCount)
    }

    @Test
    fun `la tarjeta de la cuenta con red abre Correo`() = scope.runTest {
        create()
        viewModel.onAction(SettingsAction.ProfileClicked)
        runCurrent()
        assertEquals(SettingsEffect.OpenChangeEmail, viewModel.effects.first())
    }

    @Test
    fun `la tarjeta de la cuenta sin red avisa y no navega`() = scope.runTest {
        create()
        repo.twoFactor = null
        viewModel.onAction(SettingsAction.ProfileClicked)
        runCurrent()
        assertEquals(NoticeKind.NeedsConnectionToChangeEmail, state.notice?.kind)
        assertNull(kotlinx.coroutines.withTimeoutOrNull(1) { viewModel.effects.first() })
    }

    @Test
    fun `al volver de cambiar el correo avisa con el correo nuevo`() = scope.runTest {
        create()
        viewModel.onAction(SettingsAction.EmailChanged("luis.ruiz@gmail.com"))
        assertEquals(NoticeKind.EmailChanged, state.notice?.kind)
        assertEquals("luis.ruiz@gmail.com", state.notice?.argument)
    }

    @Test
    fun `dos cambios de correo seguidos vuelven a mostrar el aviso`() = scope.runTest {
        create()
        viewModel.onAction(SettingsAction.EmailChanged("a@correo.com"))
        val first = state.notice
        viewModel.onAction(SettingsAction.EmailChanged("a@correo.com"))
        assertTrue(first != state.notice)
    }
}
