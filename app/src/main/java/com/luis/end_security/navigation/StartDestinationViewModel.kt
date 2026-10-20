package com.luis.end_security.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luis.end_security.feature.account.domain.AccountRepository
import com.luis.end_security.feature.auth.domain.OnboardingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Decide una sola vez, al arrancar, la primera pantalla (tarea 10):
 * - con una cuenta en el teléfono: Desbloquear;
 * - sin cuenta pero ya vio el onboarding: Iniciar sesión;
 * - si no: el onboarding.
 *
 * Si Android restaura la pila tras matar el proceso, la llave no sobrevivió: `RequireUnlocked` manda
 * a Desbloquear desde cualquier pantalla protegida. `null` mientras se lee.
 */
@HiltViewModel
class StartDestinationViewModel @Inject constructor(
    onboardingRepository: OnboardingRepository,
    accounts: AccountRepository,
) : ViewModel() {

    val startRoute: StateFlow<String?> = flow {
        emit(
            when {
                accounts.hasLocalAccount() -> Routes.UNLOCK
                onboardingRepository.isCompleted.first() -> Routes.LOGIN
                else -> Routes.ONBOARDING
            },
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
