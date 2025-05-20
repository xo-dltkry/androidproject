package ui

import android.util.Log
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import data.*
import data.isInRoute
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.KoinContext
import org.koin.compose.viewmodel.koinViewModel
import recurringexpensetracker.app.generated.resources.Res
import recurringexpensetracker.app.generated.resources.edit_expense_button_add
import recurringexpensetracker.app.generated.resources.home_title
import recurringexpensetracker.app.generated.resources.upcoming_title
import ui.auth.LoginScreen
import ui.auth.RegisterScreen
import ui.editexpense.EditRecurringExpenseScreen
import ui.settings.SettingsScreen
import ui.upcomingexpenses.UpcomingPaymentsScreen
import viewmodel.AuthViewModel
import viewmodel.MainNavigationViewModel
import androidx.compose.runtime.collectAsState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContent(
    isGridMode: Boolean,
    biometricSecurity: Boolean,
    canUseBiometric: Boolean,
    canUseNotifications: Boolean,
    hasNotificationPermission: Boolean,
    toggleGridMode: () -> Unit,
    onBiometricSecurityChange: (Boolean) -> Unit,
    requestNotificationPermission: () -> Unit,
    navigateToPermissionsSettings: () -> Unit,
    onClickBackup: () -> Unit,
    onClickRestore: () -> Unit,
    updateWidget: () -> Unit,
    modifier: Modifier = Modifier,
    startRoute: MainNavRoute = LoginPane,
    mainNavigationViewModel: MainNavigationViewModel = koinViewModel<MainNavigationViewModel>(),
    authViewModel: AuthViewModel = koinViewModel<AuthViewModel>(),
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val isLoggedIn by authViewModel.isLoggedIn.collectAsState(initial = false)

    // Check authentication state and navigate accordingly
    LaunchedEffect(isLoggedIn) {
        Log.d("MainContent", "Auth state changed: isLoggedIn = $isLoggedIn, current route = ${backStackEntry?.destination?.route}")
        if (!isLoggedIn && backStackEntry?.destination?.route != LoginPane.route && backStackEntry?.destination?.route != RegisterPane.route) {
            Log.d("MainContent", "Navigating to LoginPane")
            navController.navigate(LoginPane) {
                popUpTo(0) { inclusive = true }
            }
        } else if (isLoggedIn && (backStackEntry?.destination?.route == LoginPane.route || backStackEntry?.destination?.route == RegisterPane.route)) {
            Log.d("MainContent", "Navigating to HomePane")
            navController.navigate(HomePane) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    KoinContext {
        Scaffold(
            modifier = modifier,
            topBar = {
                mainNavigationViewModel.topAppBar()
            },
            bottomBar = {
                if (backStackEntry?.destination?.isInRoute(HomePane, UpcomingPane, SettingsPane) == true) {
                    BottomNavBar(navController = navController)
                }
            },
            floatingActionButton = {
                if (backStackEntry?.destination?.isInRoute(HomePane, UpcomingPane) == true) {
                    FloatingActionButton(
                        onClick = {
                            navController.navigate(EditExpensePane())
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription =
                                stringResource(Res.string.edit_expense_button_add),
                        )
                    }
                }
            },
            content = { paddingValues ->
                NavHost(
                    navController = navController,
                    startDestination = LoginPane,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    composable<LoginPane> {
                        LoginScreen(
                            authViewModel = authViewModel,
                            onNavigateToRegister = {
                                navController.navigate(RegisterPane)
                            },
                            onLoginSuccess = {
                                navController.navigate(HomePane) {
                                    popUpTo(LoginPane) { inclusive = true }
                                }
                            }
                        )
                    }

                    composable<RegisterPane> {
                        RegisterScreen(
                            authViewModel = authViewModel,
                            onNavigateToLogin = {
                                navController.navigate(LoginPane) {
                                    popUpTo(RegisterPane) { inclusive = true }
                                }
                            },
                            onRegisterSuccess = {
                                navController.navigate(HomePane) {
                                    popUpTo(RegisterPane) { inclusive = true }
                                }
                            }
                        )
                    }

                    composable<HomePane> {
                        mainNavigationViewModel.topAppBar = {
                            TopAppBar(
                                title = {
                                    Text(
                                        text = stringResource(Res.string.home_title),
                                    )
                                },
                                actions = {
                                    ToggleGridModeButton(
                                        onToggleGridMode = toggleGridMode,
                                        isGridMode = isGridMode,
                                    )
                                },
                            )
                        }

                        RecurringExpenseOverview(
                            isGridMode = isGridMode,
                            navController = navController,
                            contentPadding =
                                PaddingValues(
                                    top = 8.dp,
                                    start = 16.dp,
                                    end = 16.dp,
                                ),
                            modifier = Modifier.padding(paddingValues),
                        )
                    }
                    composable<UpcomingPane> {
                        mainNavigationViewModel.topAppBar = {
                            TopAppBar(
                                title = {
                                    Text(
                                        text = stringResource(Res.string.upcoming_title),
                                    )
                                },
                                actions = {
                                    ToggleGridModeButton(
                                        onToggleGridMode = toggleGridMode,
                                        isGridMode = isGridMode,
                                    )
                                },
                            )
                        }

                        UpcomingPaymentsScreen(
                            isGridMode = isGridMode,
                            navController = navController,
                            contentPadding =
                                PaddingValues(
                                    top = 8.dp,
                                    start = 16.dp,
                                    end = 16.dp,
                                ),
                            modifier = Modifier.padding(paddingValues),
                        )
                    }
                    composable<SettingsPane> {
                        var topAppBar by remember { mutableStateOf<@Composable () -> Unit>({}) }
                        SettingsScreen(
                            biometricsChecked = biometricSecurity,
                            onClickBackup = onClickBackup,
                            onClickRestore = onClickRestore,
                            onBiometricCheckedChange = onBiometricSecurityChange,
                            canUseBiometric = canUseBiometric,
                            canUseNotifications = canUseNotifications,
                            hasNotificationPermission = hasNotificationPermission,
                            requestNotificationPermission = requestNotificationPermission,
                            navigateToPermissionsSettings = navigateToPermissionsSettings,
                            onLogout = {
                                authViewModel.logout()
                                navController.navigate(LoginPane) {
                                    popUpTo(0) { inclusive = true }
                                }
                            },
                            setTopAppBar = {
                                mainNavigationViewModel.topAppBar = it
                                topAppBar = it
                            },
                            modifier = Modifier.padding(paddingValues),
                        )
                        mainNavigationViewModel.topAppBar = topAppBar
                    }
                    composable<EditExpensePane> { backStackEntry ->
                        var topAppBar by remember { mutableStateOf<@Composable () -> Unit>({}) }
                        val editExpensePane = backStackEntry.toRoute<EditExpensePane>()
                        EditRecurringExpenseScreen(
                            expenseId = editExpensePane.expenseId,
                            canUseNotifications = canUseNotifications,
                            onDismiss = {
                                navController.navigateUp()
                                updateWidget()
                            },
                            setTopAppBar = {
                                mainNavigationViewModel.topAppBar = it
                                topAppBar = it
                            },
                            modifier = Modifier.padding(paddingValues),
                        )
                        mainNavigationViewModel.topAppBar = topAppBar
                    }
                }
            },
        )
    }
}

// @Preview
// @Composable
// private fun MainActivityContentPreview() {
//    var isGridMode by remember { mutableStateOf(false) }
//    var biometricSecurity by remember { mutableStateOf(false) }
//    ExpenseTrackerTheme {
//        Surface(
//            modifier = Modifier.fillMaxSize(),
//            color = MaterialTheme.colorScheme.background,
//        ) {
//            MainContent(
//                weeklyExpense = "4,00 €",
//                monthlyExpense = "16,00 €",
//                yearlyExpense = "192,00 €",
//                recurringExpenseData =
//                    listOf(
//                        RecurringExpenseData(
//                            id = 0,
//                            name = "Netflix",
//                            description = "My Netflix description",
//                            price = 9.99f,
//                            monthlyPrice = 9.99f,
//                            everyXRecurrence = 1,
//                            recurrence = Recurrence.Monthly,
//                            Clock.System.now(),
//                            ExpenseColor.Dynamic,
//                        ),
//                        RecurringExpenseData(
//                            id = 1,
//                            name = "Disney Plus",
//                            description = "My Disney Plus description",
//                            price = 5f,
//                            monthlyPrice = 5f,
//                            everyXRecurrence = 1,
//                            recurrence = Recurrence.Monthly,
//                            Clock.System.now(),
//                            ExpenseColor.Red,
//                        ),
//                        RecurringExpenseData(
//                            id = 2,
//                            name = "Amazon Prime",
//                            description = "My Disney Plus description",
//                            price = 7.95f,
//                            monthlyPrice = 7.95f,
//                            everyXRecurrence = 1,
//                            recurrence = Recurrence.Monthly,
//                            Clock.System.now(),
//                            ExpenseColor.Blue,
//                        ),
//                    ),
//                onRecurringExpenseAdd = {},
//                onRecurringExpenseEdit = {},
//                onRecurringExpenseDelete = {},
//                onClickBackup = { },
//                onClickRestore = { },
//                upcomingPaymentsViewModel = UpcomingPaymentsViewModel(null),
//                isGridMode = isGridMode,
//                toggleGridMode = { isGridMode = !isGridMode },
//                biometricSecurity = biometricSecurity,
//                onBiometricSecurityChange = { biometricSecurity = it },
//                canUseBiometric = true,
//            )
//        }
//    }
// }
