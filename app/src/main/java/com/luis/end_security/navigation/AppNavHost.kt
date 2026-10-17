package com.luis.end_security.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.luis.end_security.feature.auth.ui.login.LoginScreen
import com.luis.end_security.feature.auth.ui.onboarding.OnboardingRoute
import com.luis.end_security.feature.auth.ui.signup.SignUpScreen

@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    startViewModel: StartDestinationViewModel = hiltViewModel(),
) {
    val startRoute by startViewModel.startRoute.collectAsStateWithLifecycle()
    val route = startRoute
    if (route == null) {
        // Lectura de DataStore en curso (milisegundos): solo el fondo.
        Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }

    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = route, modifier = modifier) {
        composable(Routes.ONBOARDING) {
            // El onboarding no vuelve a aparecer: cualquier salida lo saca de la pila.
            OnboardingRoute(
                onNavigateToLogin = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
                onNavigateToSignUp = {
                    navController.navigate(Routes.SIGN_UP) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.LOGIN) {
            LoginScreen()
        }
        composable(Routes.SIGN_UP) {
            SignUpScreen()
        }
    }
}
