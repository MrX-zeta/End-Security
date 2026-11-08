package com.luis.end_security.feature.auth.domain

import kotlinx.coroutines.flow.Flow

/** Recuerda si el usuario ya vio el onboarding para no volver a mostrarlo. */
interface OnboardingRepository {
    val isCompleted: Flow<Boolean>

    suspend fun markCompleted()
}
