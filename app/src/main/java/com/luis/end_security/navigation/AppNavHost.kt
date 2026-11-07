package com.luis.end_security.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.Column
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.compose.runtime.remember
import com.luis.end_security.core.designsystem.theme.rememberReducedMotion
import com.luis.end_security.core.designsystem.theme.Motion
import androidx.compose.ui.platform.LocalDensity
import com.luis.end_security.core.designsystem.component.BottomNavTab
import com.luis.end_security.core.designsystem.component.LocalTabGuard
import com.luis.end_security.core.designsystem.component.TabGuard
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.dp
import com.luis.end_security.core.designsystem.component.LocalMessageHost
import com.luis.end_security.core.designsystem.component.MessageHost
import com.luis.end_security.core.designsystem.component.isKeyboardVisible
import com.luis.end_security.core.designsystem.component.rememberMessageHostState
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
import com.luis.end_security.feature.settings.ui.legal.AboutScreen
import com.luis.end_security.feature.settings.ui.legal.LegalDocumentScreen
import com.luis.end_security.feature.settings.ui.legal.LicensesScreen
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
import com.luis.end_security.feature.account.ui.ChangeEmailRoute
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
    val unlocked by lockGuardViewModel.unlocked.collectAsStateWithLifecycle()
    // Barra inferior (DEC-56): las cuatro pestañas navegan sin sumar historial. La barra es una sola (tarea 26) y la pantalla visible
    // puede interceptar el toque ([TabGuard]): pedir confirmación o ignorarlo mientras trabaja.
    val tabGuard = remember { TabGuard() }
    val navigateToTab: (BottomNavTab) -> Unit = { tab -> navController.navigateToTab(tab) }
    val onBarTabSelected: (BottomNavTab) -> Unit = { tab -> if (!tabGuard.intercepts(tab)) navigateToTab(tab) }
    val currentEntry by navController.currentBackStackEntryAsState()
    // Con la bóveda bloqueada no hay barra aunque la pantalla protegida aún no haya terminado de salir.
    val barTab = if (unlocked) tabForRoute(currentEntry?.destination?.route) else null
    // Si la bóveda se bloquea: a Desbloquear (o a Iniciar sesión sin cuenta), sin nada debajo.
    // Avisos de toda la app en un solo lugar (tarea 26): encima de la barra inferior y nunca encima del teclado.
    val messageHost = rememberMessageHostState()
    LockGuard(
        viewModel = lockGuardViewModel,
        // Cuenta eliminada: sin `RequireUnlocked` (la sesión ya se cerró) y con la pila limpia.
        onAccountDeleted = {
            messageHost.clear()
            navController.navigateClearingStack(Routes.ACCOUNT_DELETED)
        },
        onLocked = { destination ->
            // Un aviso con el nombre de un elemento no debe verse sobre Desbloquear ni Iniciar sesión.
            messageHost.clear()
            navController.navigateClearingStack(
                when (destination) {
                    LockDestination.Unlock -> Routes.UNLOCK
                    LockDestination.Login -> Routes.LOGIN
                },
            )
        },
    )
    // Transiciones de toda la app (tarea 24): una sola política en `NavMotion`, ninguna pantalla pone las suyas.
    val slidePx = with(LocalDensity.current) { Motion.Slide.roundToPx() }
    val reducedMotion = rememberReducedMotion()
    val transitions = remember(slidePx, reducedMotion) { NavTransitions(slidePx, reducedMotion) }
    val messagesViewModel: MessageHostViewModel = hiltViewModel()
    val resources = LocalResources.current
    LaunchedEffect(messagesViewModel) {
        messagesViewModel.messages.collect { message ->
            messageHost.post(resources.getString(message.text, *message.args.toTypedArray()), message.icon)
        }
    }
    CompositionLocalProvider(LocalTabGuard provides tabGuard, LocalMessageHost provides messageHost) {
    Column(modifier.fillMaxSize()) {
    Box(Modifier.weight(1f)) {
    NavHost(
        navController = navController,
        startDestination = route,
        modifier = Modifier,
        enterTransition = { transitions.enter(this) },
        exitTransition = { transitions.exit(this) },
        popEnterTransition = { transitions.popEnter(this) },
        popExitTransition = { transitions.popExit(this) },
        // Gesto de regreso predictivo: sigue el dedo y se puede cancelar, con las mismas transiciones de «Regresar».
        predictivePopEnterTransition = { transitions.popEnter(this) },
        predictivePopExitTransition = { transitions.popExit(this) },
    ) {
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
        composable(Routes.UNLOCK) {
            // Cerrar sesión deja Iniciar sesión sola en la pila.
            UnlockRoute(
                // Los elementos ya están en Room: directo a la Bóveda, sin la descarga inicial.
                onNavigateToVault = { navController.navigateClearingStack(Routes.VAULT) },
                onNavigateToLogin = { navController.navigateClearingStack(Routes.LOGIN) },
            )
        }
        composable(Routes.SETTINGS) { backStackEntry ->
            RequireUnlocked(onLocked = lockGuardViewModel::onProtectedScreenLocked) {
                val handle = backStackEntry.savedStateHandle
                val disabled by handle.getStateFlow(Routes.RESULT_TWO_FACTOR_DISABLED, false).collectAsStateWithLifecycle()
                val passwordResult by handle.getStateFlow(Routes.RESULT_PASSWORD_CHANGED, 0).collectAsStateWithLifecycle()
                val emailResult by handle.getStateFlow(Routes.RESULT_EMAIL_CHANGED, "").collectAsStateWithLifecycle()
                SettingsRoute(
                    onNavigateToLogin = { navController.navigateClearingStack(Routes.LOGIN) },
                    onOpenTwoFactorSetup = { navController.navigate(Routes.TWO_FACTOR_SETUP) { launchSingleTop = true } },
                    onOpenTwoFactorActive = { navController.navigate(Routes.TWO_FACTOR_ACTIVE) { launchSingleTop = true } },
                    onOpenChangePassword = { navController.navigate(Routes.CHANGE_PASSWORD) { launchSingleTop = true } },
                    onOpenDevices = { navController.navigate(Routes.DEVICES) { launchSingleTop = true } },
                    onOpenActivity = { navController.navigate(Routes.ACTIVITY) { launchSingleTop = true } },
                    onOpenDeleteAccount = { navController.navigate(Routes.DELETE_ACCOUNT) { launchSingleTop = true } },
                    onOpenChangeEmail = { navController.navigate(Routes.CHANGE_EMAIL) { launchSingleTop = true } },
                    onOpenLegalDocument = { document -> navController.navigate(Routes.legalDocument(document)) { launchSingleTop = true } },
                    onOpenLicenses = { navController.navigate(Routes.LICENSES) { launchSingleTop = true } },
                    onOpenAbout = { navController.navigate(Routes.ABOUT) { launchSingleTop = true } },
                    emailChanged = emailResult,
                    onEmailChangedShown = { handle[Routes.RESULT_EMAIL_CHANGED] = "" },
                    twoFactorDisabled = disabled,
                    onTwoFactorDisabledShown = { handle[Routes.RESULT_TWO_FACTOR_DISABLED] = false },
                    passwordChanged = passwordResult,
                    onPasswordChangedShown = { handle[Routes.RESULT_PASSWORD_CHANGED] = 0 },
                )
            }
        }
        composable(Routes.CHANGE_EMAIL) {
            RequireUnlocked(onLocked = lockGuardViewModel::onProtectedScreenLocked) {
                ChangeEmailRoute(
                    onBack = { navController.popBackStack() },
                    onChanged = { email ->
                        navController.previousBackStackEntry?.savedStateHandle?.set(Routes.RESULT_EMAIL_CHANGED, email)
                        navController.popBackStack()
                    },
                )
            }
        }
        composable(Routes.DELETE_ACCOUNT) {
            RequireUnlocked(onLocked = lockGuardViewModel::onProtectedScreenLocked) {
                DeleteAccountRoute(
                    onBack = { navController.popBackStack() },
                )
            }
        }
        // Cuenta eliminada: sin `RequireUnlocked` (la sesión ya se cerró) y con la pila limpia.
        composable(Routes.ACCOUNT_DELETED) {
            AccountDeletedRoute(onGoHome = { navController.navigateClearingStack(Routes.LOGIN) })
        }
        composable(Routes.DEVICES) {
            RequireUnlocked(onLocked = lockGuardViewModel::onProtectedScreenLocked) {
                DevicesRoute(
                    onBack = { navController.popBackStack() },
                    onChangePassword = { navController.navigate(Routes.CHANGE_PASSWORD) { launchSingleTop = true } },
                )
            }
        }
        composable(Routes.ACTIVITY) {
            RequireUnlocked(onLocked = lockGuardViewModel::onProtectedScreenLocked) {
                ActivityRoute(
                    onBack = { navController.popBackStack() },
                    onChangePassword = { navController.navigate(Routes.CHANGE_PASSWORD) { launchSingleTop = true } },
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
                )
            }
        }
        composable(Routes.TWO_FACTOR_SETUP) {
            RequireUnlocked(onLocked = lockGuardViewModel::onProtectedScreenLocked) {
                TwoFactorSetupRoute(
                    // «Ya los guardé» o Atrás en el Paso 3: fuera todo el flujo, de vuelta a Ajustes.
                    onExit = { navController.popBackStack(Routes.SETTINGS, inclusive = false) },
                    // Tras confirmar en los códigos de respaldo, la pantalla navega ella misma a la pestaña elegida.
                    onTabSelected = navigateToTab,
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
                )
            }
        }
        composable(Routes.HEALTH) {
            RequireUnlocked(onLocked = lockGuardViewModel::onProtectedScreenLocked) {
                HealthRoute(
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
        ) {
            RequireUnlocked(onLocked = lockGuardViewModel::onProtectedScreenLocked) {
                HealthRankingRoute(onBack = { navController.popBackStack() })
            }
        }
        composable(Routes.HEALTH_BREACHED) {
            RequireUnlocked(onLocked = lockGuardViewModel::onProtectedScreenLocked) {
                HealthBreachedRoute(
                    onBack = { navController.popBackStack() },
                    onChange = { id -> navController.navigate(Routes.vaultEdit(id)) { launchSingleTop = true } },
                )
            }
        }
        composable(Routes.HEALTH_REPEATED) {
            RequireUnlocked(onLocked = lockGuardViewModel::onProtectedScreenLocked) {
                HealthRepeatedRoute(
                    onBack = { navController.popBackStack() },
                    onChange = { id -> navController.navigate(Routes.vaultEdit(id)) { launchSingleTop = true } },
                )
            }
        }
        composable(Routes.HEALTH_OLD) {
            RequireUnlocked(onLocked = lockGuardViewModel::onProtectedScreenLocked) {
                HealthOldRoute(
                    onBack = { navController.popBackStack() },
                    onChange = { id -> navController.navigate(Routes.vaultEdit(id)) { launchSingleTop = true } },
                )
            }
        }
        composable(Routes.GENERATOR) {
            RequireUnlocked(onLocked = lockGuardViewModel::onProtectedScreenLocked) {
                GeneratorRoute(
                    onOpenNewWithPassword = { navController.navigate(Routes.VAULT_NEW_GENERATED) { launchSingleTop = true } },
                )
            }
        }
        composable(Routes.VAULT_LOADING) {
            RequireUnlocked(onLocked = lockGuardViewModel::onProtectedScreenLocked) {
                // OP-32: descarga inicial real y después a la Bóveda.
                VaultLoadingRoute(
                    onDone = {
                        navController.navigate(Routes.VAULT) {
                            popUpTo(Routes.VAULT_LOADING) { inclusive = true }
                        }
                    },
                )
            }
        }
        composable(Routes.VAULT) {
            RequireUnlocked(onLocked = lockGuardViewModel::onProtectedScreenLocked) {
                VaultListRoute(
                    onOpenItem = { id -> navController.navigate(Routes.vaultItem(id)) { launchSingleTop = true } },
                    onOpenNew = { navController.navigate(Routes.VAULT_NEW) { launchSingleTop = true } },
                    onOpenConflict = { id -> navController.navigate(Routes.conflictItem(id)) { launchSingleTop = true } },
                    onOpenConflicts = { navController.navigate(Routes.CONFLICTS) { launchSingleTop = true } },
                    onOpenHealth = { navigateToTab(BottomNavTab.Health) },
                )
            }
        }
        composable(Routes.CONFLICTS) {
            RequireUnlocked(onLocked = lockGuardViewModel::onProtectedScreenLocked) {
                ConflictListRoute(
                    onBack = { navController.popBackStack() },
                    onOpen = { id -> navController.navigate(Routes.conflictItem(id)) { launchSingleTop = true } },
                )
            }
        }
        composable(
            route = Routes.CONFLICT_ITEM,
            arguments = listOf(navArgument(Routes.ITEM_ID_ARG) { type = NavType.StringType }),
        ) {
            RequireUnlocked(onLocked = lockGuardViewModel::onProtectedScreenLocked) {
                ConflictDetailRoute(
                    onClose = { navController.popBackStack() },
                    onBack = { navController.popBackStack() },
                )
            }
        }
        composable(
            route = Routes.VAULT_ITEM,
            arguments = listOf(navArgument(Routes.ITEM_ID_ARG) { type = NavType.StringType }),
        ) { entry ->
            val itemId = entry.arguments?.getString(Routes.ITEM_ID_ARG)?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            RequireUnlocked(onLocked = lockGuardViewModel::onProtectedScreenLocked) {
                if (itemId != null) {
                    ItemDetailRoute(
                        itemId = itemId,
                        onBack = { navController.popBackStack() },
                        onEdit = { id -> navController.navigate(Routes.vaultEdit(id)) { launchSingleTop = true } },
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
        ) { entry ->
            // Si vino de «Guardar en la bóveda» del Generador, al guardar se va a la Bóveda a ver la nueva.
            val fromGenerator = entry.arguments?.getBoolean(Routes.GENERATED_ARG) == true
            RequireUnlocked(onLocked = lockGuardViewModel::onProtectedScreenLocked) {
                ItemEditRoute(
                    onClose = { navController.popBackStack() },
                    onSaved = { if (fromGenerator) navigateToTab(BottomNavTab.Vault) else navController.popBackStack() },
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
        composable(Routes.LICENSES) { LicensesScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.ABOUT) { AboutScreen(onBack = { navController.popBackStack() }) }
    }
    // Con barra, el cuadro termina justo encima de ella; sin barra, encima de la barra de gestos (o del teclado, si está abierto).
    MessageHost(
        state = messageHost,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .then(if (barTab == null || isKeyboardVisible()) Modifier.windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)) else Modifier)
            .padding(bottom = 12.dp),
    )
    }
    PersistentTabBar(tab = barTab, onTabSelected = onBarTabSelected)
    }
    }
}

/**
 * Navega a [route] y saca de la pila todo lo anterior: atrás ya no regresa al acceso. También olvida lo que las pestañas habían
 * guardado ([clearSavedTabStates]): tras bloquear o cerrar sesión no debe volver nada de antes.
 */
internal fun NavHostController.navigateClearingStack(route: String) {
    clearSavedTabStates()
    navigate(route) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}
