package com.luis.end_security.feature.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luis.end_security.feature.auth.domain.AuthRepository
import com.luis.end_security.feature.settings.domain.AccountProfile
import com.luis.end_security.feature.settings.domain.SettingsRepository
import com.luis.end_security.feature.unlock.domain.BiometricAvailability
import com.luis.end_security.feature.unlock.domain.BiometricPreparation
import com.luis.end_security.feature.unlock.domain.BiometricResult
import com.luis.end_security.feature.unlock.domain.BiometricStatus
import com.luis.end_security.feature.unlock.domain.BiometricUnlock
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.crypto.Cipher
import javax.inject.Inject

/** Hoja de «Cerrar sesión» que se muestra (Figma 215:3811 y 215:4020). */
sealed interface SignOutSheet {
    /** «¿Cerrar sesión?» */
    data object Confirm : SignOutSheet

    /** «Tienes N cambios sin subir»: [count] elementos `pending` o `conflict`. */
    data class Unsynced(val count: Int) : SignOutSheet
}

/** Las filas que llevan a pantallas de las tareas 17–20; por ahora solo avisan «Disponible pronto». */
enum class FutureRow { TwoFactor, MasterPassword, Devices, Activity, DeleteAccount }

/** Estado de Ajustes (SCR-29). Nada secreto vive aquí. */
data class SettingsUiState(
    val profile: AccountProfile? = null,
    /** `null` sin red o con error: la fila solo muestra la flecha. */
    val twoFactorEnabled: Boolean? = null,
    val deviceCount: Int? = null,
    /** Hay sensor biométrico: sin él, la fila no se muestra. */
    val biometricRowVisible: Boolean = false,
    val biometricEnabled: Boolean = false,
    /** «Agrega una huella en los ajustes del teléfono…» bajo la fila. */
    val biometricNeedsEnrollment: Boolean = false,
    /** Se está activando (llave creada, esperando la huella): el interruptor no se vuelve a tocar. */
    val biometricBusy: Boolean = false,
    val autolockMinutes: Int = 5,
    val showAutoLockSheet: Boolean = false,
    val signOutSheet: SignOutSheet? = null,
    val isSigningOut: Boolean = false,
    /** Cambia con cada toque a una fila futura: la pantalla muestra «Disponible pronto» un momento. */
    val comingSoonCount: Int = 0,
)

sealed interface SettingsAction {
    data class FutureRowClicked(val row: FutureRow) : SettingsAction
    data object AutoLockClicked : SettingsAction
    data class AutoLockSelected(val minutes: Int) : SettingsAction
    data object AutoLockDismissed : SettingsAction
    data class BiometricToggled(val enable: Boolean) : SettingsAction

    /** El `BiometricPrompt` aceptó la huella con este `Cipher` ya autenticado. */
    class BiometricAuthenticated(val cipher: Cipher) : SettingsAction {
        override fun toString(): String = "BiometricAuthenticated(***)"
    }

    /** El `BiometricPrompt` se canceló o falló: el interruptor vuelve a apagado. */
    data object BiometricCancelled : SettingsAction
    data object SignOutClicked : SettingsAction
    data object SignOutConfirmed : SettingsAction
    data object SignOutDismissed : SettingsAction

    /** La pantalla volvió a verse (p. ej. tras registrar una huella en los ajustes del teléfono). */
    data object Resumed : SettingsAction
}

sealed interface SettingsEffect {
    /** La pantalla muestra el `BiometricPrompt` con este `Cipher` (activar la huella). */
    class ShowBiometricPrompt(val cipher: Cipher) : SettingsEffect

    /** Sesión cerrada: a Iniciar sesión con la pila limpia. */
    data object NavigateToLogin : SettingsEffect
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val biometric: BiometricUnlock,
    private val availability: BiometricAvailability,
    private val auth: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    private val _effects = Channel<SettingsEffect>(Channel.BUFFERED)
    val effects: Flow<SettingsEffect> = _effects.receiveAsFlow()

    init {
        viewModelScope.launch { settings.profile.collect { profile -> _state.update { it.copy(profile = profile) } } }
        viewModelScope.launch {
            settings.autolockMinutes.collect { minutes -> _state.update { it.copy(autolockMinutes = minutes) } }
        }
        viewModelScope.launch {
            biometric.enabled.collect { enabled -> _state.update { it.copy(biometricEnabled = enabled) } }
        }
        refreshBiometricRow()
        // Con red: los dos valores de la cuenta. Sin ella se quedan sin valor.
        viewModelScope.launch { settings.twoFactorEnabled().let { value -> _state.update { it.copy(twoFactorEnabled = value) } } }
        viewModelScope.launch { settings.deviceCount().let { value -> _state.update { it.copy(deviceCount = value) } } }
    }

    private fun refreshBiometricRow() {
        val status = availability.status()
        _state.update {
            it.copy(
                biometricRowVisible = status != BiometricStatus.NoHardware,
                // El aviso se quita solo en cuanto ya hay una huella registrada.
                biometricNeedsEnrollment = it.biometricNeedsEnrollment && status == BiometricStatus.NoneEnrolled,
            )
        }
    }

    fun onAction(action: SettingsAction) {
        when (action) {
            is SettingsAction.FutureRowClicked ->
                // TODO(T17…T20): 2 pasos (T17), Contraseña maestra (T18), Dispositivos y Actividad (T19), Eliminar cuenta (T20).
                _state.update { it.copy(comingSoonCount = it.comingSoonCount + 1) }
            SettingsAction.AutoLockClicked -> _state.update { it.copy(showAutoLockSheet = true) }
            SettingsAction.AutoLockDismissed -> _state.update { it.copy(showAutoLockSheet = false) }
            is SettingsAction.AutoLockSelected -> {
                _state.update { it.copy(showAutoLockSheet = false) }
                viewModelScope.launch { settings.setAutolockMinutes(action.minutes) }
            }
            is SettingsAction.BiometricToggled -> if (action.enable) enableBiometric() else disableBiometric()
            is SettingsAction.BiometricAuthenticated -> completeEnable(action.cipher)
            SettingsAction.BiometricCancelled -> {
                _state.update { it.copy(biometricBusy = false) }
                // La llave se creó al preparar: sin huella aceptada no se deja huérfana en el Keystore.
                viewModelScope.launch { biometric.disable() }
            }
            SettingsAction.SignOutClicked -> startSignOut()
            SettingsAction.SignOutConfirmed -> signOut()
            SettingsAction.SignOutDismissed -> _state.update { it.copy(signOutSheet = null) }
            SettingsAction.Resumed -> refreshBiometricRow()
        }
    }

    /** OP-35: activar. */
    private fun enableBiometric() {
        if (_state.value.biometricBusy) return
        _state.update { it.copy(biometricBusy = true, biometricNeedsEnrollment = false) }
        viewModelScope.launch {
            val prepared = try {
                biometric.prepareEnable()
            } catch (_: RuntimeException) {
                BiometricPreparation.Failed
            }
            when (prepared) {
                is BiometricPreparation.Ready -> _effects.send(SettingsEffect.ShowBiometricPrompt(prepared.cipher))
                BiometricPreparation.NoneEnrolled ->
                    _state.update { it.copy(biometricBusy = false, biometricNeedsEnrollment = true) }
                // Cualquier otro caso: no se enciende y sin mensaje (el sistema ya dijo lo que pasó).
                else -> _state.update { it.copy(biometricBusy = false) }
            }
        }
    }

    private fun completeEnable(cipher: Cipher) {
        viewModelScope.launch {
            try {
                val result = biometric.completeEnable(cipher)
                if (result != BiometricResult.Done) biometric.disable()
            } catch (_: RuntimeException) {
                biometric.disable()
            } finally {
                _state.update { it.copy(biometricBusy = false) }
            }
        }
    }

    /** OP-35: desactivar, sin confirmación. */
    private fun disableBiometric() {
        viewModelScope.launch { biometric.disable() }
    }

    /**
     * OP-18: antes de mostrar la hoja, si hay cambios sin subir se intenta sincronizar (hasta 5 s). Si se subieron todos
     * sale la hoja normal; si no, la de «cambios sin subir».
     */
    private fun startSignOut() {
        if (_state.value.isSigningOut) return
        viewModelScope.launch {
            var count = settings.unsyncedCount()
            if (count > 0) {
                settings.syncNow(SYNC_WAIT_MILLIS)
                count = settings.unsyncedCount()
            }
            _state.update { it.copy(signOutSheet = if (count > 0) SignOutSheet.Unsynced(count) else SignOutSheet.Confirm) }
        }
    }

    private fun signOut() {
        if (_state.value.isSigningOut) return
        _state.update { it.copy(isSigningOut = true, signOutSheet = null) }
        viewModelScope.launch {
            auth.signOut()
            _state.update { it.copy(isSigningOut = false) }
            _effects.send(SettingsEffect.NavigateToLogin)
        }
    }

    companion object {
        /** Espera máxima de la sincronización antes de decidir qué hoja mostrar. */
        const val SYNC_WAIT_MILLIS = 5_000L
    }
}
