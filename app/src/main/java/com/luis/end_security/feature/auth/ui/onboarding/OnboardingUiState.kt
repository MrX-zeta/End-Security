package com.luis.end_security.feature.auth.ui.onboarding

data class OnboardingUiState(
    val currentPage: Int = 0,
    val pageCount: Int = ONBOARDING_PAGE_COUNT,
    val isFinishing: Boolean = false,
) {
    val isLastPage: Boolean get() = currentPage == pageCount - 1
}

const val ONBOARDING_PAGE_COUNT = 3

sealed interface OnboardingAction {
    /** «Siguiente». */
    data object Next : OnboardingAction

    /** Botón «atrás» del sistema en las páginas 2 y 3. */
    data object Previous : OnboardingAction

    /** El usuario deslizó y el pager se detuvo en [page]. */
    data class PageSettled(val page: Int) : OnboardingAction

    /** «Omitir», «Comenzar» o «Ya tengo una cuenta»: todos terminan el onboarding. */
    data object Finish : OnboardingAction
}

sealed interface OnboardingEffect {
    data object NavigateToLogin : OnboardingEffect
}
