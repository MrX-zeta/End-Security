package com.luis.end_security.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luis.end_security.feature.auth.domain.OnboardingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Decide una sola vez, al arrancar, la primera pantalla: el onboarding si nunca se vio,
 * si no, Iniciar sesión. `null` mientras se lee DataStore.
 */
@HiltViewModel
class StartDestinationViewModel @Inject constructor(
    onboardingRepository: OnboardingRepository,
) : ViewModel() {

    val startRoute: StateFlow<String?> = flow {
        val completed = onboardingRepository.isCompleted.first()
        emit(if (completed) Routes.LOGIN else Routes.ONBOARDING)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
