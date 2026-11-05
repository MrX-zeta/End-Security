package com.luis.end_security.feature.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luis.end_security.core.designsystem.component.SignOutSheet
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

/** Avisos breves al pie de Ajustes. */
enum class NoticeKind {
    NeedsConnection, NeedsConnectionToDelete, NeedsConnectionToChangeEmail, TwoFactorDisabled, PasswordChanged, BiometricOff, EmailChanged,

    /** No se pudo guardar «Novedades y encuestas»: el interruptor volvió a su valor. */
    MarketingFailed,
}

/** [id] cambia en cada aviso, para que dos iguales seguidos vuelvan a mostrarse. */
data class SettingsNotice(val kind: NoticeKind, val id: Int, val argument: String? = null)

/** Estado de Ajustes (SCR-29). Nada secreto vive aquí. */
data class SettingsUiState(
    val profile: AccountProfile? = null,
    /** `null` sin red o con error: la fila solo muestra la flecha. */
    val twoFactorEnabled: Boolean? = null,
    val deviceCount: Int? = null,
    /** «Novedades y encuestas» (DEC-64): `null` sin red o con error; entonces el interruptor se muestra apagado y deshabilitado. */
    val marketingOptIn: Boolean? = null,
    /** Se está guardando el cambio: el interruptor no se vuelve a tocar hasta que responda el servidor. */
    val marketingBusy: Boolean = false,
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
    /** El aviso que se muestra un momento (Disponible pronto, sin conexión, 2 pasos desactivada). */
    val notice: SettingsNotice? = null,
)

sealed interface SettingsAction {
    /** La tarjeta de la cuenta: sin red avisa y no navega; con red abre «Correo» (OP-26). */
    data object ProfileClicked : SettingsAction

    /** Volvió de cambiar el correo: «Tu correo ahora es {correo}». */
    data class EmailChanged(val email: String) : SettingsAction

    /** «Eliminar cuenta»: sin red avisa y no navega; con red abre SCR-36. */
    data object DeleteAccountClicked : SettingsAction

    /** «Verificación en 2 pasos»: activar si está inactiva, administrar si está activa (T17). */
    data object TwoFactorClicked : SettingsAction

    /** Volvió de «Desactivar» con éxito: «Verificación en dos pasos desactivada». */
    data object TwoFactorDisabled : SettingsAction

    /** Volvió de cambiar la contraseña (T18); [biometricOff]: la huella quedó desactivada. */
    data class PasswordChanged(val biometricOff: Boolean) : SettingsAction
    data object AutoLockClicked : SettingsAction
    data class AutoLockSelected(val minutes: Int) : SettingsAction
    data object AutoLockDismissed : SettingsAction
    data class BiometricToggled(val enable: Boolean) : SettingsAction

    /** El interruptor «Novedades y encuestas»: se guarda en el servidor; si falla, vuelve a su valor y avisa. */
    data class MarketingToggled(val enable: Boolean) : SettingsAction

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

    /** 2 pasos inactiva: al flujo de activación. */
    data object OpenTwoFactorSetup : SettingsEffect

    /** 2 pasos activa: a su pantalla de administración. */
    data object OpenTwoFactorActive : SettingsEffect

    /** Con red: a «Correo». */
    data object OpenChangeEmail : SettingsEffect

    /** Con red: a Eliminar cuenta. */
    data object OpenDeleteAccount : SettingsEffect
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
        // Mientras se cierra la sesión, lo que se ve queda congelado: `signOutLocally` borra la bandera de la huella y Room, y
        // Ajustes no debe parpadear antes de cambiar de pantalla.
        viewModelScope.launch {
            settings.profile.collect { profile -> _state.update { if (it.isSigningOut) it else it.copy(profile = profile) } }
        }
        viewModelScope.launch {
            settings.autolockMinutes.collect { minutes ->
                _state.update { if (it.isSigningOut) it else it.copy(autolockMinutes = minutes) }
            }
        }
        viewModelScope.launch {
            biometric.enabled.collect { enabled ->
                _state.update { if (it.isSigningOut) it else it.copy(biometricEnabled = enabled) }
            }
        }
        refreshBiometricRow()
        // Con red: los dos valores de la cuenta. Sin ella se quedan sin valor.
        refreshTwoFactor()
        refreshDeviceCount()
        refreshMarketing()
    }

    /** El valor del servidor; sin red conserva el último conocido y mientras se guarda un cambio no pisa lo que se ve. */
    private fun refreshMarketing() {
        // Un GET que salió antes de un cambio y llega después no debe pisar lo que la persona ya cambió (DEC-64): si la generación
        // cambió mientras esperaba, su respuesta se descarta.
        val generation = marketingGeneration
        viewModelScope.launch {
            val value = settings.marketingOptIn() ?: return@launch
            _state.update {
                if (it.isSigningOut || it.marketingBusy || generation != marketingGeneration) it else it.copy(marketingOptIn = value)
            }
        }
    }

    /** Cambia con cada interruptor tocado: invalida las lecturas del servidor que ya iban en camino. */
    private var marketingGeneration = 0

    /** PATCH del interruptor: se ve el cambio al instante y, si el servidor no lo confirma, se revierte con un aviso genérico. */
    private fun toggleMarketing(enable: Boolean) {
        val previous = _state.value.marketingOptIn ?: return
        if (_state.value.marketingBusy || previous == enable) return
        marketingGeneration++
        _state.update { it.copy(marketingOptIn = enable, marketingBusy = true) }
        viewModelScope.launch {
            val saved = try {
                settings.setMarketingOptIn(enable)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            marketingGeneration++
            _state.update { it.copy(marketingOptIn = saved ?: previous, marketingBusy = false) }
            if (saved == null || saved != enable) {
                notify(NoticeKind.MarketingFailed)
                // Con la respuesta perdida el servidor pudo aplicar el cambio: se vuelve a leer para mostrar lo que de verdad tiene.
                refreshMarketing()
            }
        }
    }

    /** El número de dispositivos; sin red conserva el último conocido (al volver de «Dispositivos» se recarga). */
    private fun refreshDeviceCount() {
        viewModelScope.launch {
            val value = settings.deviceCount() ?: return@launch
            _state.update { if (it.isSigningOut) it else it.copy(deviceCount = value) }
        }
    }

    private var noticeSequence = 0

    private fun notify(kind: NoticeKind, argument: String? = null) {
        _state.update { it.copy(notice = SettingsNotice(kind, ++noticeSequence, argument)) }
    }

    private fun refreshTwoFactor() {
        viewModelScope.launch {
            val value = settings.twoFactorEnabled()
            // Con error se conserva el último valor conocido: no se borra «Activa» por una falla de red pasajera.
            if (value != null) _state.update { if (it.isSigningOut) it else it.copy(twoFactorEnabled = value) }
        }
    }

    /** Pregunta al servidor el estado actual: sin red no navega y avisa (T17). */
    private fun openTwoFactor() {
        viewModelScope.launch {
            when (settings.twoFactorEnabled()) {
                null -> notify(NoticeKind.NeedsConnection)
                true -> _effects.send(SettingsEffect.OpenTwoFactorActive)
                false -> _effects.send(SettingsEffect.OpenTwoFactorSetup)
            }
        }
    }

    /** Cambiar el correo necesita red: se pregunta al servidor; sin respuesta, avisa y no navega. */
    private fun openChangeEmail() {
        viewModelScope.launch {
            if (settings.twoFactorEnabled() == null) notify(NoticeKind.NeedsConnectionToChangeEmail) else _effects.send(SettingsEffect.OpenChangeEmail)
        }
    }

    /** Eliminar la cuenta necesita red: se pregunta al servidor (y de paso si hay 2FA); sin respuesta, avisa y no navega. */
    private fun openDeleteAccount() {
        viewModelScope.launch {
            if (settings.twoFactorEnabled() == null) notify(NoticeKind.NeedsConnectionToDelete) else _effects.send(SettingsEffect.OpenDeleteAccount)
        }
    }

    private fun refreshBiometricRow() {
        val status = availability.status()
        _state.update {
            if (it.isSigningOut) return@update it
            it.copy(
                biometricRowVisible = status != BiometricStatus.NoHardware,
                // El aviso se quita solo en cuanto ya hay una huella registrada.
                biometricNeedsEnrollment = it.biometricNeedsEnrollment && status == BiometricStatus.NoneEnrolled,
            )
        }
    }

    fun onAction(action: SettingsAction) {
        when (action) {
            SettingsAction.DeleteAccountClicked -> openDeleteAccount()
            SettingsAction.ProfileClicked -> openChangeEmail()
            is SettingsAction.EmailChanged -> notify(NoticeKind.EmailChanged, action.email)
            SettingsAction.TwoFactorClicked -> openTwoFactor()
            is SettingsAction.PasswordChanged -> viewModelScope.launch {
                notify(NoticeKind.PasswordChanged)
                if (action.biometricOff) {
                    // Un aviso a la vez: el segundo sale cuando el primero ya se leyó.
                    kotlinx.coroutines.delay(BIOMETRIC_NOTICE_DELAY_MILLIS)
                    notify(NoticeKind.BiometricOff)
                }
            }
            SettingsAction.TwoFactorDisabled -> {
                notify(NoticeKind.TwoFactorDisabled)
                refreshTwoFactor()
            }
            SettingsAction.AutoLockClicked -> _state.update { it.copy(showAutoLockSheet = true) }
            SettingsAction.AutoLockDismissed -> _state.update { it.copy(showAutoLockSheet = false) }
            is SettingsAction.AutoLockSelected -> {
                _state.update { it.copy(showAutoLockSheet = false) }
                viewModelScope.launch { settings.setAutolockMinutes(action.minutes) }
            }
            is SettingsAction.BiometricToggled -> if (action.enable) enableBiometric() else disableBiometric()
            is SettingsAction.MarketingToggled -> toggleMarketing(action.enable)
            is SettingsAction.BiometricAuthenticated -> completeEnable(action.cipher)
            SettingsAction.BiometricCancelled -> {
                _state.update { it.copy(biometricBusy = false) }
                // La llave se creó al preparar: sin huella aceptada no se deja huérfana en el Keystore.
                viewModelScope.launch { biometric.disable() }
            }
            SettingsAction.SignOutClicked -> startSignOut()
            SettingsAction.SignOutConfirmed -> signOut()
            // Con el cierre en curso la hoja no se cierra: sigue cargando hasta navegar.
            SettingsAction.SignOutDismissed -> _state.update { if (it.isSigningOut) it else it.copy(signOutSheet = null) }
            SettingsAction.Resumed -> {
                refreshBiometricRow()
                // Al volver de activar o desactivar, «Activa» / «Inactiva» se recarga.
                refreshTwoFactor()
                // Al volver de «Dispositivos» (cerró sesiones) el número se recarga.
                refreshDeviceCount()
                refreshMarketing()
            }
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
        // Se congela lo que se ve; la hoja sigue abierta con su botón cargando hasta que se navega.
        _state.update { it.copy(isSigningOut = true) }
        viewModelScope.launch {
            try {
                auth.signOut()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                // `signOutLocally` ya bloqueó la sesión en su `finally`: igual se va a Iniciar sesión (M4).
            }
            _effects.send(SettingsEffect.NavigateToLogin)
        }
    }

    companion object {
        /** Espera máxima de la sincronización antes de decidir qué hoja mostrar. */
        const val SYNC_WAIT_MILLIS = 5_000L

        /** Lo que dura el primer aviso antes de mostrar el de la huella. */
        const val BIOMETRIC_NOTICE_DELAY_MILLIS = 4_200L
    }
}
