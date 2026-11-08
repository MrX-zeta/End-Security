package com.luis.end_security.feature.settings.domain

import kotlinx.coroutines.flow.Flow

/** La cuenta que se muestra en la tarjeta de Ajustes (tabla `account`, solo en este teléfono). */
data class AccountProfile(val displayName: String?, val email: String) {
    /** Lo que va en grande: el nombre o, sin nombre, el correo. */
    val title: String get() = displayName?.takeIf { it.isNotBlank() } ?: email

    /** Lo que va debajo: el correo, salvo que ya sea el título. */
    val subtitle: String? get() = email.takeIf { it != title }
}

/** Lo que Ajustes necesita del teléfono y, cuando hay red, de la cuenta (SCR-29). */
interface SettingsRepository {
    val profile: Flow<AccountProfile?>

    /** Minutos de autobloqueo vigentes (0 = al salir). */
    val autolockMinutes: Flow<Int>

    /** OP-21: guarda el valor (saneado); `AutoLocker` escucha el cambio sin reiniciar la app. */
    suspend fun setAutolockMinutes(minutes: Int)

    /** `AccountApi.getAccount().twoFactorEnabled`; `null` sin red o con error. */
    suspend fun twoFactorEnabled(): Boolean?

    /** `AccountApi.getAccount().marketingOptIn` («Novedades y encuestas»); `null` sin red o con error. */
    suspend fun marketingOptIn(): Boolean?

    /** `PATCH /account { marketingOptIn }`: devuelve el valor que quedó en el servidor o `null` si falló (no se guarda nada local). */
    suspend fun setMarketingOptIn(optIn: Boolean): Boolean?

    /** Número de dispositivos con sesión (`SessionApi.listSessions()`); `null` si falla. */
    suspend fun deviceCount(): Int?

    /** Elementos `pending` o `conflict`: lo que cerrar sesión perdería. */
    suspend fun unsyncedCount(): Int

    /** Pide una sincronización y espera como máximo [timeoutMillis]; sin red vuelve al instante. */
    suspend fun syncNow(timeoutMillis: Long)
}
