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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.luis.end_security.core.designsystem.theme.SystemBarsAppearance
import com.luis.end_security.feature.auth.ui.login.LoginRoute
import com.luis.end_security.feature.auth.ui.onboarding.OnboardingRoute
import com.luis.end_security.feature.auth.ui.signup.SignUpRoute
import com.luis.end_security.feature.settings.ui.legal.LegalDocument
import com.luis.end_security.feature.settings.ui.legal.LegalDocumentScreen

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

    // La app es clara (DEC-51): íconos oscuros en las barras; el onboarding los cambia mientras se ve.
    SystemBarsAppearance(darkBackground = false)
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = route, modifier = modifier) {
        composable(Routes.ONBOARDING) {
            OnboardingRoute(
                // «Omitir» y «Ya tengo una cuenta» sacan el onboarding de la pila.
                onNavigateToLogin = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                // «Comenzar» lo deja debajo: atrás desde Crear cuenta regresa a él (DEC-56).
                onNavigateToSignUp = {
                    navController.navigate(Routes.SIGN_UP) { launchSingleTop = true }
                },
            )
        }
        composable(Routes.LOGIN) {
            LoginRoute(
                onCreateAccount = { navController.navigate(Routes.SIGN_UP) { launchSingleTop = true } },
            )
        }
        composable(Routes.SIGN_UP) {
            SignUpRoute(
                onBack = { navController.popBackStack() },
                onOpenLegalDocument = { document ->
                    navController.navigate(Routes.legalDocument(document)) { launchSingleTop = true }
                },
            )
        }
        composable(
            route = Routes.LEGAL_DOCUMENT,
            arguments = listOf(navArgument(Routes.LEGAL_DOCUMENT_ARG) { type = NavType.StringType }),
        ) { entry ->
            val document = LegalDocument.fromRouteArg(entry.arguments?.getString(Routes.LEGAL_DOCUMENT_ARG))
            if (document != null) {
                LegalDocumentScreen(document = document, onBack = { navController.popBackStack() })
            }
        }
    }
}
