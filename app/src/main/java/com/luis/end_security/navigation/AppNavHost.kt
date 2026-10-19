package com.luis.end_security.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.luis.end_security.BuildConfig
import com.luis.end_security.core.designsystem.theme.SystemBarsAppearance
import com.luis.end_security.feature.auth.ui.backupcode.BackupCodeRoute
import com.luis.end_security.feature.auth.ui.login.LoginRoute
import com.luis.end_security.feature.auth.ui.twofactor.TwoFactorRoute
import com.luis.end_security.feature.auth.ui.onboarding.OnboardingRoute
import com.luis.end_security.feature.auth.ui.signup.SignUpRoute
import com.luis.end_security.feature.auth.ui.verify.VerifyEmailRoute
import com.luis.end_security.feature.auth.ui.verify.VerifyEmailViewModel
import com.luis.end_security.feature.settings.ui.legal.LegalDocument
import com.luis.end_security.feature.settings.ui.legal.LegalDocumentScreen
import com.luis.end_security.feature.unlock.ui.UnlockRoute
import com.luis.end_security.feature.vault.ui.VaultLoadingScreen
import com.luis.end_security.feature.vault.ui.VaultPlaceholderScreen
import kotlinx.coroutines.delay

@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    /** Solo en depuración: abre la app en una de [Routes.DEBUG_START_ROUTES]. */
    debugStartRoute: String? = null,
    startViewModel: StartDestinationViewModel = hiltViewModel(),
) {
    val startRoute by startViewModel.startRoute.collectAsStateWithLifecycle()
    val route = debugStartRoute ?: startRoute
    if (route == null) {
        // Lectura de DataStore en curso (milisegundos): solo el fondo.
        Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }

    // La app es clara (DEC-51): íconos oscuros en las barras; el onboarding los cambia mientras se ve.
    SystemBarsAppearance(darkBackground = false)
    val navController = rememberNavController()
    // Si la bóveda se bloquea: a Desbloquear (o a Iniciar sesión sin cuenta), sin nada debajo.
    LockGuard(
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
                // TODO(OP-14): solo si la cuenta tiene 2FA; si no, a la bóveda.
                onNavigateToTwoFactor = { navController.navigate(Routes.TWO_FACTOR) { launchSingleTop = true } },
            )
        }
        composable(Routes.TWO_FACTOR) {
            TwoFactorRoute(
                onBack = { navController.popBackStack() },
                onUseBackupCode = { navController.navigate(Routes.BACKUP_CODE) { launchSingleTop = true } },
                // TODO(OP-15): solo cuando el servidor acepte el código.
                onNavigateToVault = { navController.navigateClearingStack(Routes.VAULT_LOADING) },
            )
        }
        composable(Routes.BACKUP_CODE) {
            BackupCodeRoute(
                onBack = { navController.popBackStack() },
                // TODO(OP-16): solo cuando el servidor acepte el código.
                onNavigateToVault = { navController.navigateClearingStack(Routes.VAULT_LOADING) },
            )
        }
        composable(Routes.UNLOCK) {
            // Cerrar sesión deja Iniciar sesión sola en la pila.
            UnlockRoute(onNavigateToLogin = { navController.navigateClearingStack(Routes.LOGIN) })
        }
        composable(Routes.VAULT_LOADING) {
            // TODO(OP-32): al terminar la primera descarga, ir a la Bóveda (SCR-10). Mientras tanto,
            //  en depuración se pasa al marcador tras 2 s; en release se queda aquí.
            if (BuildConfig.DEBUG) {
                LaunchedEffect(Unit) {
                    delay(2_000)
                    navController.navigate(Routes.VAULT) {
                        popUpTo(Routes.VAULT_LOADING) { inclusive = true }
                    }
                }
            }
            // TODO(DEC-56): cada pestaña lleva a su raíz cuando existan Salud, Generador y Ajustes.
            VaultLoadingScreen(onTabSelected = {})
        }
        composable(Routes.VAULT) {
            VaultPlaceholderScreen(onTabSelected = {})
        }
        composable(Routes.SIGN_UP) {
            // Términos y Aviso de privacidad se abren en una hoja inferior dentro de Crear cuenta;
            // la ruta LEGAL_DOCUMENT queda para Ajustes.
            SignUpRoute(
                onBack = { navController.popBackStack() },
                // Crear cuenta sale de la pila: atrás desde Verificar regresa a Onboarding o Iniciar sesión.
                // TODO(OP-11): navegar solo si el registro responde bien.
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
            VerifyEmailRoute(onBack = { navController.popBackStack() })
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
