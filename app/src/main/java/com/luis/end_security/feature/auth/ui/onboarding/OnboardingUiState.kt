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

    /** «Omitir» (páginas 1 y 2): termina y va a Iniciar sesión. */
    data object Skip : OnboardingAction

    /** «Comenzar» (página 3): termina y va a Crear cuenta (DEC-56). */
    data object Start : OnboardingAction

    /** «Ya tengo una cuenta» (página 3): termina y va a Iniciar sesión. */
    data object AlreadyHaveAccount : OnboardingAction
}

sealed interface OnboardingEffect {
    /** SCR-04. */
    data object NavigateToLogin : OnboardingEffect

    /** SCR-05. */
    data object NavigateToSignUp : OnboardingEffect
}
