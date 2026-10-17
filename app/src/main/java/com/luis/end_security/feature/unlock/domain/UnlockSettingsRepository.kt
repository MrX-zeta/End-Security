package com.luis.end_security.feature.unlock.domain

import kotlinx.coroutines.flow.Flow

/** Ajustes locales que necesita Desbloquear (SCR-09). */
interface UnlockSettingsRepository {
    /** `biometric_enabled` de DataStore (DEC-09): si se muestra el botón de huella. */
    val biometricEnabled: Flow<Boolean>
}
