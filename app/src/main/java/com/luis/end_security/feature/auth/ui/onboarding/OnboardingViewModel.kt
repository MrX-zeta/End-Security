package com.luis.end_security.feature.auth.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luis.end_security.feature.auth.domain.OnboardingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val repository: OnboardingRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    private val _effects = Channel<OnboardingEffect>(Channel.BUFFERED)
    val effects: Flow<OnboardingEffect> = _effects.receiveAsFlow()

    private var finishJob: Job? = null

    fun onAction(action: OnboardingAction) {
        when (action) {
            OnboardingAction.Next -> goTo(_state.value.currentPage + 1)
            OnboardingAction.Previous -> goTo(_state.value.currentPage - 1)
            is OnboardingAction.PageSettled -> goTo(action.page)
            OnboardingAction.Skip,
            OnboardingAction.AlreadyHaveAccount,
            -> finish(OnboardingEffect.NavigateToLogin)
            OnboardingAction.Start -> finish(OnboardingEffect.NavigateToSignUp)
        }
    }

    private fun goTo(page: Int) {
        _state.update { it.copy(currentPage = page.coerceIn(0, it.pageCount - 1)) }
    }

    private fun finish(destination: OnboardingEffect) {
        // Evita el doble toque mientras se guarda. Al terminar se libera: con «Comenzar» el
        // onboarding queda en la pila y, si el usuario regresa, puede volver a terminar.
        if (finishJob?.isActive == true) return
        _state.update { it.copy(isFinishing = true) }
        finishJob = viewModelScope.launch {
            repository.markCompleted()
            _effects.send(destination)
            _state.update { it.copy(isFinishing = false) }
        }
    }
}
