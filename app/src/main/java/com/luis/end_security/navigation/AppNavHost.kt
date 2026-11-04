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
import com.luis.end_security.core.designsystem.component.BottomNavTab
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import java.util.UUID
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
import com.luis.end_security.feature.vault.ui.detail.ItemDetailRoute
import com.luis.end_security.feature.vault.ui.edit.ItemEditRoute
import com.luis.end_security.feature.vault.ui.generator.GeneratorRoute
import com.luis.end_security.feature.health.ui.HealthBreachedRoute
import com.luis.end_security.feature.health.ui.HealthOldRoute
import com.luis.end_security.feature.health.ui.HealthRankingRoute
import com.luis.end_security.feature.health.ui.HealthRepeatedRoute
import com.luis.end_security.feature.health.ui.HealthRoute
import com.luis.end_security.feature.account.ui.AccountDeletedRoute
import com.luis.end_security.feature.account.ui.ChangePasswordRoute
import com.luis.end_security.feature.account.ui.DeleteAccountRoute
import com.luis.end_security.feature.activity.ui.ActivityRoute
import com.luis.end_security.feature.activity.ui.DevicesRoute
import com.luis.end_security.feature.account.ui.TwoFactorActiveRoute
import com.luis.end_security.feature.account.ui.TwoFactorSetupRoute
import com.luis.end_security.feature.settings.ui.SettingsRoute
import com.luis.end_security.feature.vault.ui.conflict.ConflictDetailRoute
import com.luis.end_security.feature.vault.ui.conflict.ConflictListRoute
import com.luis.end_security.feature.vault.ui.list.VaultListRoute

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
    // Barra inferior (DEC-56): las cuatro pestañas navegan sin sumar historial.
    val onTabSelected: (BottomNavTab) -> Unit = { tab ->
        when (tab) {
            BottomNavTab.Vault -> navController.navigate(Routes.VAULT) {
                popUpTo(Routes.VAULT) { inclusive = false }
                launchSingleTop = true
            }
            BottomNavTab.Generator -> {
                // Desde la verificación en dos pasos salen sus secretos de la pila: así Atrás no los vuelve a mostrar (M3).
                if (navController.currentDestination?.route in TWO_FACTOR_ROUTES) {
                    navController.popBackStack(Routes.SETTINGS, inclusive = false)
                }
                navController.navigate(Routes.GENERATOR) { launchSingleTop = true }
            }
            BottomNavTab.Health -> navController.navigate(Routes.HEALTH) {
                popUpTo(Routes.HEALTH) { inclusive = false }
                launchSingleTop = true
            }
            BottomNavTab.Settings -> navController.navigate(Routes.SETTINGS) {
                popUpTo(Routes.SETTINGS) { inclusive = false }
                launchSingleTop = true
            }
        }
    }
    // Si la bóveda se bloquea: a Desbloquear (o a Iniciar sesión sin cuenta), sin nada debajo.
    LockGuard(
        viewModel = lockGuardViewModel,
        // Cuenta eliminada: sin `RequireUnlocked` (la sesión ya se cerró) y con la pila limpia.
        onAccountDeleted = { navController.navigateClearingStack(Routes.ACCOUNT_DELETED) },
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
        composable(Routes.SETTINGS, exitTransition = { ExitTransition.None }, popExitTransition = { ExitTransition.None }) { backStackEntry ->
            RequireUnlocked(onLocked = lockGuardViewModel::onProtectedScreenLocked) {
                val handle = backStackEntry.savedStateHandle
                val disabled by handle.getStateFlow(Routes.RESULT_TWO_FACTOR_DISABLED, false).collectAsStateWithLifecycle()
                val passwordResult by handle.getStateFlow(Routes.RESULT_PASSWORD_CHANGED, 0).collectAsStateWithLifecycle()
                SettingsRoute(
                    onTabSelected = onTabSelected,
                    onNavigateToLogin = { navController.navigateClearingStack(Routes.LOGIN) },
                    onOpenTwoFactorSetup = { navController.navigate(Routes.TWO_FACTOR_SETUP) { launchSingleTop = true } },
                    onOpenTwoFactorActive = { navController.navigate(Routes.TWO_FACTOR_ACTIVE) { launchSingleTop = true } },
                    onOpenChangePassword = { navController.navigate(Routes.CHANGE_PASSWORD) { launchSingleTop = true } },
                    onOpenDevices = { navController.navigate(Routes.DEVICES) { launchSingleTop = true } },
                    onOpenActivity = { navController.navigate(Routes.ACTIVITY) { launchSingleTop = true } },
                    onOpenDeleteAccount = { navController.navigate(Routes.DELETE_ACCOUNT) { launchSingleTop = true } },
                    twoFactorDisabled = disabled,
                    onTwoFactorDisabledShown = { handle[Routes.RESULT_TWO_FACTOR_DISABLED] = false },
                    passwordChanged = passwordResult,
                    onPasswordChangedShown = { handle[Routes.RESULT_PASSWORD_CHANGED] = 0 },
                )
            }
        }
        composable(Routes.DELETE_ACCOUNT) {
            RequireUnlocked(onLocked = lockGuardViewModel::onProtectedScreenLocked) {
                DeleteAccountRoute(
                    onBack = { navController.popBackStack() },
                    onTabSelected = onTabSelected,
                )
            }
        }
        // Cuenta eliminada: sin `RequireUnlocked` (la sesión ya se cerró) y con la pila limpia.
        composable(Routes.ACCOUNT_DELETED, enterTransition = { EnterTransition.None }, exitTransition = { ExitTransition.None }) {
            AccountDeletedRoute(onGoHome = { navController.navigateClearingStack(Routes.LOGIN) })
        }
        composable(Routes.DEVICES) {
            RequireUnlocked(onLocked = lockGuardViewModel::onProtectedScreenLocked) {
                DevicesRoute(
                    onBack = { navController.popBackStack() },
                    onChangePassword = { navController.navigate(Routes.CHANGE_PASSWORD) { launchSingleTop = true } },
                    onTabSelected = onTabSelected,
                )
            }
        }
        composable(Routes.ACTIVITY) {
            RequireUnlocked(onLocked = lockGuardViewModel::onProtectedScreenLocked) {
                ActivityRoute(
                    onBack = { navController.popBackStack() },
                    onChangePassword = { navController.navigate(Routes.CHANGE_PASSWORD) { launchSingleTop = true } },
                    onTabSelected = onTabSelected,
                )
            }
        }
        composable(Routes.CHANGE_PASSWORD) {
            RequireUnlocked(onLocked = lockGuardViewModel::onProtectedScreenLocked) {
                ChangePasswordRoute(
                    onBack = { navController.popBackStack() },
                    onFinish = { biometricOff ->
                        navController.previousBackStackEntry?.savedStateHandle?.set(
                            Routes.RESULT_PASSWORD_CHANGED,
                            if (biometricOff) Routes.PASSWORD_CHANGED_BIOMETRIC_OFF else Routes.PASSWORD_CHANGED,
                        )
                        navController.popBackStack()
                    },
                    onNavigateToLogin = { navController.navigateClearingStack(Routes.LOGIN) },
                    onTabSelected = onTabSelected,
                )
            }
        }
        composable(Routes.TWO_FACTOR_SETUP) {
            RequireUnlocked(onLocked = lockGuardViewModel::onProtectedScreenLocked) {
                TwoFactorSetupRoute(
                    // «Ya los guardé» o Atrás en el Paso 3: fuera todo el flujo, de vuelta a Ajustes.
                    onExit = { navController.popBackStack(Routes.SETTINGS, inclusive = false) },
                    onTabSelected = onTabSelected,
                )
            }
        }
        composable(Routes.TWO_FACTOR_ACTIVE) {
            RequireUnlocked(onLocked = lockGuardViewModel::onProtectedScreenLocked) {
                TwoFactorActiveRoute(
                    onBack = { navController.popBackStack() },
                    onDisabled = {
                        navController.previousBackStackEntry?.savedStateHandle?.set(Routes.RESULT_TWO_FACTOR_DISABLED, true)
                        navController.popBackStack()
                    },
                    onTabSelected = onTabSelected,
                )
            }
        }
        composable(Routes.HEALTH, exitTransition = { ExitTransition.None }, popExitTransition = { ExitTransition.None }) {
            RequireUnlocked(onLocked = lockGuardViewModel::onProtectedScreenLocked) {
                HealthRoute(
                    onTabSelected = onTabSelected,
                    onOpenBreached = { navController.navigate(Routes.HEALTH_BREACHED) { launchSingleTop = true } },
                    onOpenRepeated = { navController.navigate(Routes.HEALTH_REPEATED) { launchSingleTop = true } },
                    onOpenWeak = { navController.navigate(Routes.healthRanking(weakest = true)) { launchSingleTop = true } },
                    onOpenOld = { navController.navigate(Routes.HEALTH_OLD) { launchSingleTop = true } },
                    onOpenRanking = { navController.navigate(Routes.healthRanking(weakest = false)) { launchSingleTop = true } },
                )
            }
        }
        composable(
            route = Routes.HEALTH_RANKING,
            arguments = listOf(
                navArgument(Routes.RANKING_TAB_ARG) {
                    type = NavType.StringType
                    defaultValue = "Strongest"
                },
            ),
            exitTransition = { ExitTransition.None },
            popExitTransition = { ExitTransition.None },
        ) {
            RequireUnlocked(onLocked = lockGuardViewModel::onProtectedScreenLocked) {
                HealthRankingRoute(onBack = { navController.popBackStack() }, onTabSelected = onTabSelected)
            }
        }
        composable(Routes.HEALTH_BREACHED, exitTransition = { ExitTransition.None }, popExitTransition = { ExitTransition.None }) {
            RequireUnlocked(onLocked = lockGuardViewModel::onProtectedScreenLocked) {
                HealthBreachedRoute(
                    onBack = { navController.popBackStack() },
                    onChange = { id -> navController.navigate(Routes.vaultEdit(id)) { launchSingleTop = true } },
                    onTabSelected = onTabSelected,
                )
            }
        }
        composable(Routes.HEALTH_REPEATED, exitTransition = { ExitTransition.None }, popExitTransition = { ExitTransition.None }) {
            RequireUnlocked(onLocked = lockGuardViewModel::onProtectedScreenLocked) {
                HealthRepeatedRoute(
                    onBack = { navController.popBackStack() },
                    onChange = { id -> navController.navigate(Routes.vaultEdit(id)) { launchSingleTop = true } },
                    onTabSelected = onTabSelected,
                )
            }
        }
        composable(Routes.HEALTH_OLD, exitTransition = { ExitTransition.None }, popExitTransition = { ExitTransition.None }) {
            RequireUnlocked(onLocked = lockGuardViewModel::onProtectedScreenLocked) {
                HealthOldRoute(
                    onBack = { navController.popBackStack() },
                    onChange = { id -> navController.navigate(Routes.vaultEdit(id)) { launchSingleTop = true } },
                    onTabSelected = onTabSelected,
                )
            }
        }
        composable(Routes.GENERATOR, exitTransition = { ExitTransition.None }, popExitTransition = { ExitTransition.None }) {
            RequireUnlocked(onLocked = lockGuardViewModel::onProtectedScreenLocked) {
                GeneratorRoute(
                    onTabSelected = onTabSelected,
                    onOpenNewWithPassword = { navController.navigate(Routes.VAULT_NEW_GENERATED) { launchSingleTop = true } },
                )
            }
        }
        composable(Routes.VAULT_LOADING, exitTransition = { ExitTransition.None }, popExitTransition = { ExitTransition.None }) {
            RequireUnlocked(onLocked = lockGuardViewModel::onProtectedScreenLocked) {
                // OP-32: descarga inicial real y después a la Bóveda.
                VaultLoadingRoute(
                    onTabSelected = onTabSelected,
                    onDone = {
                        navController.navigate(Routes.VAULT) {
                            popUpTo(Routes.VAULT_LOADING) { inclusive = true }
                        }
                    },
                )
            }
        }
        composable(Routes.VAULT, exitTransition = { ExitTransition.None }, popExitTransition = { ExitTransition.None }) {
            RequireUnlocked(onLocked = lockGuardViewModel::onProtectedScreenLocked) {
                VaultListRoute(
                    onTabSelected = onTabSelected,
                    onOpenItem = { id -> navController.navigate(Routes.vaultItem(id)) { launchSingleTop = true } },
                    onOpenNew = { navController.navigate(Routes.VAULT_NEW) { launchSingleTop = true } },
                    onOpenConflict = { id -> navController.navigate(Routes.conflictItem(id)) { launchSingleTop = true } },
                    onOpenConflicts = { navController.navigate(Routes.CONFLICTS) { launchSingleTop = true } },
                    onOpenHealth = { onTabSelected(BottomNavTab.Health) },
                )
            }
        }
        composable(Routes.CONFLICTS, exitTransition = { ExitTransition.None }, popExitTransition = { ExitTransition.None }) {
            RequireUnlocked(onLocked = lockGuardViewModel::onProtectedScreenLocked) {
                ConflictListRoute(
                    onBack = { navController.popBackStack() },
                    onOpen = { id -> navController.navigate(Routes.conflictItem(id)) { launchSingleTop = true } },
                    onTabSelected = onTabSelected,
                )
            }
        }
        composable(
            route = Routes.CONFLICT_ITEM,
            arguments = listOf(navArgument(Routes.ITEM_ID_ARG) { type = NavType.StringType }),
            exitTransition = { ExitTransition.None },
            popExitTransition = { ExitTransition.None },
        ) {
            RequireUnlocked(onLocked = lockGuardViewModel::onProtectedScreenLocked) {
                ConflictDetailRoute(
                    onClose = { navController.popBackStack() },
                    onBack = { navController.popBackStack() },
                    onTabSelected = onTabSelected,
                )
            }
        }
        composable(
            route = Routes.VAULT_ITEM,
            arguments = listOf(navArgument(Routes.ITEM_ID_ARG) { type = NavType.StringType }),
            exitTransition = { ExitTransition.None },
            popExitTransition = { ExitTransition.None },
        ) { entry ->
            val itemId = entry.arguments?.getString(Routes.ITEM_ID_ARG)?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            RequireUnlocked(onLocked = lockGuardViewModel::onProtectedScreenLocked) {
                if (itemId != null) {
                    ItemDetailRoute(
                        itemId = itemId,
                        onBack = { navController.popBackStack() },
                        onEdit = { id -> navController.navigate(Routes.vaultEdit(id)) { launchSingleTop = true } },
                        onTabSelected = onTabSelected,
                    )
                }
            }
        }
        composable(
            route = Routes.VAULT_EDIT,
            // Sin `id`, Nueva contraseña; con `id`, Editar.
            arguments = listOf(
                navArgument(Routes.ITEM_ID_ARG) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument(Routes.GENERATED_ARG) {
                    type = NavType.BoolType
                    defaultValue = false
                },
            ),
            exitTransition = { ExitTransition.None },
            popExitTransition = { ExitTransition.None },
        ) { entry ->
            // Si vino de «Guardar en la bóveda» del Generador, al guardar se va a la Bóveda a ver la nueva.
            val fromGenerator = entry.arguments?.getBoolean(Routes.GENERATED_ARG) == true
            RequireUnlocked(onLocked = lockGuardViewModel::onProtectedScreenLocked) {
                ItemEditRoute(
                    onClose = { navController.popBackStack() },
                    onSaved = { if (fromGenerator) onTabSelected(BottomNavTab.Vault) else navController.popBackStack() },
                    onTabSelected = onTabSelected,
                )
            }
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

/** Destinos que muestran la clave TOTP o códigos de respaldo (T17). */
private val TWO_FACTOR_ROUTES = setOf(Routes.TWO_FACTOR_SETUP, Routes.TWO_FACTOR_ACTIVE)
