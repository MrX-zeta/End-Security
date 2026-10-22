package com.luis.end_security.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.luis.end_security.core.designsystem.theme.SystemBarsAppearance
import com.luis.end_security.feature.auth.ui.backupcode.BackupCodeRoute
import com.luis.end_security.feature.auth.ui.login.LoginRoute
import com.luis.end_security.feature.auth.ui.twofactor.TwoFactorRoute
import com.luis.end_security.feature.auth.ui.onboarding.OnboardingRoute
import com.luis.end_security.feature.auth.ui.signup.SignUpRoute
import com.luis.end_security.feature.auth.ui.splash.SplashRoute
import com.luis.end_security.feature.auth.ui.verify.VerifyEmailRoute
import com.luis.end_security.feature.auth.ui.verify.VerifyEmailViewModel
import com.luis.end_security.feature.settings.ui.legal.LegalDocument
import com.luis.end_security.feature.settings.ui.legal.LegalDocumentScreen
import com.luis.end_security.feature.unlock.ui.UnlockRoute
import com.luis.end_security.feature.vault.ui.VaultLoadingRoute
import com.luis.end_security.feature.vault.ui.VaultRoute

@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    /** Solo en depuración: abre la app en una de [Routes.DEBUG_START_ROUTES]. */
    debugStartRoute: String? = null,
    startViewModel: StartDestinationViewModel = hiltViewModel(),
) {
    val startRoute by startViewModel.startRoute.collectAsStateWithLifecycle()
    val route = debugStartRoute ?: startRoute
    // El splash (SCR-46) se ve en cada arranque en frío y no queda en la pila. `rememberSaveable`
    // evita que reaparezca al girar la pantalla o al restaurar la actividad. Con una ruta de
    // depuración no se muestra.
    var splashFinished by rememberSaveable { mutableStateOf(debugStartRoute != null) }
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // La ruta se arma debajo en cuanto se decide, para que el fundido de salida la revele.
        if (route != null) AppNavigation(route = route)
        if (!splashFinished) {
            SplashRoute(destinationReady = route != null, onFinished = { splashFinished = true })
        }
    }
}

@Composable
private fun AppNavigation(route: String, modifier: Modifier = Modifier) {
    // La app es clara (DEC-51): íconos oscuros en las barras; el onboarding los cambia mientras se ve.
    SystemBarsAppearance(darkBackground = false)
    val navController = rememberNavController()
    val lockGuardViewModel: LockGuardViewModel = hiltViewModel()
    // Si la bóveda se bloquea: a Desbloquear (o a Iniciar sesión sin cuenta), sin nada debajo.
    LockGuard(
        viewModel = lockGuardViewModel,
        onLocked = { destination ->
            navController.navigateClearingStack(
                when (destination) {
                    LockDestination.Unlock -> Routes.UNLOCK
                    LockDestination.Login -> Routes.LOGIN
                },
            )
        },
    )
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
                onNavigateToTwoFactor = { navController.navigate(Routes.TWO_FACTOR) { launchSingleTop = true } },
                onNavigateToVault = { navController.navigateClearingStack(Routes.VAULT_LOADING) },
                onNavigateToVerifyEmail = { email -> navController.navigate(Routes.verifyEmail(email)) { launchSingleTop = true } },
            )
        }
        composable(Routes.TWO_FACTOR) {
            TwoFactorRoute(
                onBack = { navController.popBackStack() },
                onUseBackupCode = { navController.navigate(Routes.BACKUP_CODE) { launchSingleTop = true } },
                onNavigateToVault = { navController.navigateClearingStack(Routes.VAULT_LOADING) },
                onNavigateToLogin = { navController.navigateClearingStack(Routes.LOGIN) },
            )
        }
        composable(Routes.BACKUP_CODE) {
            BackupCodeRoute(
                onBack = { navController.popBackStack() },
                onNavigateToVault = { navController.navigateClearingStack(Routes.VAULT_LOADING) },
                onNavigateToLogin = { navController.navigateClearingStack(Routes.LOGIN) },
            )
        }
        // A Desbloquear sin transición: no se ve la bóveda desvanecerse debajo (hallazgo 3 de la tarea 09).
        composable(Routes.UNLOCK, enterTransition = { EnterTransition.None }, exitTransition = { ExitTransition.None }) {
            // Cerrar sesión deja Iniciar sesión sola en la pila.
            UnlockRoute(
                // Los elementos ya están en Room: directo a la Bóveda, sin la descarga inicial.
                onNavigateToVault = { navController.navigateClearingStack(Routes.VAULT) },
                onNavigateToLogin = { navController.navigateClearingStack(Routes.LOGIN) },
            )
        }
        composable(Routes.VAULT_LOADING, exitTransition = { ExitTransition.None }, popExitTransition = { ExitTransition.None }) {
            RequireUnlocked(onLocked = lockGuardViewModel::onProtectedScreenLocked) {
                // OP-32: descarga inicial real y después a la Bóveda.
                // TODO(DEC-56): cada pestaña lleva a su raíz cuando existan Salud, Generador y Ajustes.
                VaultLoadingRoute(
                    onTabSelected = {},
                    onDone = {
                        navController.navigate(Routes.VAULT) {
                            popUpTo(Routes.VAULT_LOADING) { inclusive = true }
                        }
                    },
                )
            }
        }
        composable(Routes.VAULT, exitTransition = { ExitTransition.None }, popExitTransition = { ExitTransition.None }) {
            RequireUnlocked(onLocked = lockGuardViewModel::onProtectedScreenLocked) { VaultRoute(onTabSelected = {}) }
        }
        composable(Routes.SIGN_UP) {
            // Términos y Aviso de privacidad se abren en una hoja inferior dentro de Crear cuenta;
            // la ruta LEGAL_DOCUMENT queda para Ajustes.
            SignUpRoute(
                onBack = { navController.popBackStack() },
                // Crear cuenta sale de la pila: atrás desde Verificar regresa a Onboarding o Iniciar sesión.
                onNavigateToVerifyEmail = { email ->
                    navController.navigate(Routes.verifyEmail(email)) {
                        popUpTo(Routes.SIGN_UP) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(
            route = Routes.VERIFY_EMAIL,
            arguments = listOf(navArgument(VerifyEmailViewModel.EMAIL_ARG) { type = NavType.StringType }),
        ) {
            VerifyEmailRoute(
                onBack = { navController.popBackStack() },
                onNavigateToVault = { navController.navigateClearingStack(Routes.VAULT_LOADING) },
                onNavigateToLogin = { navController.navigateClearingStack(Routes.LOGIN) },
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

/** Navega a [route] y saca de la pila todo lo anterior: atrás ya no regresa al acceso. */
private fun NavHostController.navigateClearingStack(route: String) {
    navigate(route) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}
