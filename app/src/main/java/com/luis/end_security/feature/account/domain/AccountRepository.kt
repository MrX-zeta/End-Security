package com.luis.end_security.feature.account.domain

/** La cuenta guardada en este teléfono (TBL-L-ACCOUNT). */
interface AccountRepository {
    /** `true` si hay una cuenta con sesión iniciada aquí (se puede desbloquear sin red). */
    suspend fun hasLocalAccount(): Boolean
}
