package com.luis.end_security.navigation

import com.luis.end_security.feature.account.domain.AccountRepository
import com.luis.end_security.feature.auth.domain.FakeAuthRepository
import com.luis.end_security.feature.auth.domain.OnboardingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StartDestinationViewModelTest {

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun startRoute(hasAccount: Boolean, onboardingSeen: Boolean, auth: FakeAuthRepository = FakeAuthRepository()): String? {
        val onboarding = object : OnboardingRepository {
            override val isCompleted = MutableStateFlow(onboardingSeen)
            override suspend fun markCompleted() = Unit
        }
        val accounts = object : AccountRepository {
            override suspend fun hasLocalAccount() = hasAccount
        }
        return StartDestinationViewModel(onboarding, accounts, auth).startRoute.value
    }

    @Test
    fun `con cuenta en el telefono empieza en Desbloquear`() {
        assertEquals(Routes.UNLOCK, startRoute(hasAccount = true, onboardingSeen = true))
        assertEquals(Routes.UNLOCK, startRoute(hasAccount = true, onboardingSeen = false))
    }

    @Test
    fun `sin cuenta y con el onboarding visto empieza en Iniciar sesion`() {
        assertEquals(Routes.LOGIN, startRoute(hasAccount = false, onboardingSeen = true))
    }

    @Test
    fun `la primera vez empieza en el onboarding`() {
        assertEquals(Routes.ONBOARDING, startRoute(hasAccount = false, onboardingSeen = false))
    }

    @Test
    fun `antes de decidir repite la limpieza pendiente de una cuenta eliminada`() {
        val auth = FakeAuthRepository()
        startRoute(hasAccount = false, onboardingSeen = true, auth = auth)
        assertEquals(1, auth.pendingWipeCalls)
    }
}
