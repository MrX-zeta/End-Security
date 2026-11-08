package com.luis.end_security.feature.auth.data

import com.luis.end_security.core.datastore.SettingsStore
import com.luis.end_security.feature.auth.domain.OnboardingRepository
import kotlinx.coroutines.flow.Flow
import java.io.IOException
import javax.inject.Inject

class OnboardingRepositoryImpl @Inject constructor(
    private val settingsStore: SettingsStore,
) : OnboardingRepository {

    override val isCompleted: Flow<Boolean> = settingsStore.onboardingCompleted

    override suspend fun markCompleted() {
        try {
            settingsStore.setOnboardingCompleted()
        } catch (_: IOException) {
            // Si no se pudo escribir, el onboarding solo se vuelve a mostrar en el siguiente
            // arranque; no vale la pena detener al usuario por eso.
        }
    }
}
