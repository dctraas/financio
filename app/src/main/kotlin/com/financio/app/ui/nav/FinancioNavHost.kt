package com.financio.app.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.financio.app.ui.accounts.AccountsScreen
import com.financio.app.ui.budgets.BudgetsScreen
import com.financio.app.ui.categories.CategoryManagementScreen
import com.financio.app.ui.charts.ChartsScreen
import com.financio.app.ui.importing.ImportScreen
import com.financio.app.ui.meer.MeerScreen
import com.financio.app.ui.merchants.MerchantManagementScreen
import com.financio.app.ui.networth.NetWorthScreen
import com.financio.app.ui.savings.SavingsGoalsScreen
import com.financio.app.ui.settings.AppearanceScreen
import com.financio.app.ui.settings.BackupExportScreen
import com.financio.app.ui.settings.LockPrivacyScreen
import com.financio.app.ui.settings.MonthStartScreen
import com.financio.app.ui.settings.NotificationsScreen
import com.financio.app.ui.subscriptions.SubscriptionsScreen
import com.financio.app.ui.transactions.TransactionDetailScreen
import com.financio.app.ui.transactions.TransactionsScreen
import com.financio.app.ui.vandaag.VandaagScreen
import com.financio.app.ui.yearreview.YearReviewScreen

private const val ARG_CATEGORY_ID = "categoryId"
private const val ARG_TRANSACTION_ID = "transactionId"
private const val CHARTS_ROUTE = "charts?categoryId={categoryId}"
private const val TRANSACTION_DETAIL_ROUTE = "transaction/{transactionId}"

private sealed class Destination(val route: String, val label: String) {
    // Vandaag is the start destination - "het antwoord, niet de data" - so it's first both here
    // and in the bottom bar.
    data object Vandaag : Destination("vandaag", "Vandaag")
    data object Transactions : Destination("transactions", "Transacties")
    data object Budgets : Destination("budgets", "Budget")
    /** Registered with an optional `categoryId` so Budget can deep-link into one category's chart. */
    data object Charts : Destination(CHARTS_ROUTE, "Inzicht")
    data object Meer : Destination("meer", "Meer")
    data object Import : Destination("import", "Importeren")
    /** Registered with a required `transactionId` — what a tap on a transaction row now opens (R3). */
    data object TransactionDetail : Destination(TRANSACTION_DETAIL_ROUTE, "Transactie")
    data object CategoryManagement : Destination("categories", "Categorieën & regels")
    data object MerchantManagement : Destination("merchants", "Ondernemingen")
    data object NetWorth : Destination("net-worth", "Vermogen")
    data object YearReview : Destination("year-review", "Jaaroverzicht")
    data object Subscriptions : Destination("subscriptions", "Abonnementen")
    data object SavingsGoals : Destination("savings-goals", "Spaardoelen")
    data object Accounts : Destination("accounts", "Rekeningen")
    data object Appearance : Destination("settings/appearance", "Weergave")
    data object LockPrivacy : Destination("settings/lock-privacy", "Vergrendeling & privacy")
    data object Notifications : Destination("settings/notifications", "Meldingen")
    data object MonthStart : Destination("settings/month-start", "Maand begint op")
    data object BackupExport : Destination("settings/backup-export", "Back-up & export")
}

private val bottomTabs = listOf(Destination.Vandaag, Destination.Transactions, Destination.Budgets, Destination.Charts, Destination.Meer)

@Composable
fun FinancioNavHost() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = backStackEntry?.destination?.route
            if (bottomTabs.any { it.route == currentRoute }) {
                NavigationBar {
                    bottomTabs.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = {
                                // Charts is registered with an optional arg; a plain tab tap
                                // navigates to the bare path so it falls back to the default.
                                val target = if (destination == Destination.Charts) "charts" else destination.route
                                navController.navigate(target) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { NavIcon(destination) },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Vandaag.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Destination.Vandaag.route) {
                VandaagScreen(
                    onSeeAllClick = {
                        navController.navigate(Destination.Transactions.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onImportClick = { navController.navigate(Destination.Import.route) },
                    onOpenDetail = { transactionId -> navController.navigate("transaction/$transactionId") },
                )
            }
            composable(Destination.Transactions.route) {
                TransactionsScreen(
                    onImportClick = { navController.navigate(Destination.Import.route) },
                    onOpenDetail = { transactionId -> navController.navigate("transaction/$transactionId") },
                )
            }
            composable(Destination.Budgets.route) {
                BudgetsScreen(onCategoryClick = { categoryId ->
                    navController.navigate("charts?categoryId=$categoryId") { launchSingleTop = true }
                })
            }
            composable(
                route = Destination.Charts.route,
                arguments = listOf(navArgument(ARG_CATEGORY_ID) { type = NavType.LongType; defaultValue = -1L }),
            ) { backStackEntry ->
                val categoryId = backStackEntry.arguments?.getLong(ARG_CATEGORY_ID)?.takeIf { it > 0 }
                ChartsScreen(
                    initialCategoryId = categoryId,
                    onGoToSubscriptionsClick = { navController.navigate(Destination.Subscriptions.route) },
                    onManageMerchantsClick = { navController.navigate(Destination.MerchantManagement.route) },
                )
            }
            composable(Destination.Meer.route) {
                MeerScreen(
                    onSubscriptionsClick = { navController.navigate(Destination.Subscriptions.route) },
                    onSavingsGoalsClick = { navController.navigate(Destination.SavingsGoals.route) },
                    onAccountsClick = { navController.navigate(Destination.Accounts.route) },
                    onManageCategoriesClick = { navController.navigate(Destination.CategoryManagement.route) },
                    onMerchantManagementClick = { navController.navigate(Destination.MerchantManagement.route) },
                    onNetWorthClick = { navController.navigate(Destination.NetWorth.route) },
                    onYearReviewClick = { navController.navigate(Destination.YearReview.route) },
                    onImportClick = { navController.navigate(Destination.Import.route) },
                    onAppearanceClick = { navController.navigate(Destination.Appearance.route) },
                    onLockPrivacyClick = { navController.navigate(Destination.LockPrivacy.route) },
                    onNotificationsClick = { navController.navigate(Destination.Notifications.route) },
                    onMonthStartClick = { navController.navigate(Destination.MonthStart.route) },
                    onBackupExportClick = { navController.navigate(Destination.BackupExport.route) },
                )
            }
            composable(Destination.Import.route) { ImportScreen(onDone = { navController.popBackStack() }) }
            composable(
                route = Destination.TransactionDetail.route,
                arguments = listOf(navArgument(ARG_TRANSACTION_ID) { type = NavType.LongType }),
            ) {
                TransactionDetailScreen(
                    onBackClick = { navController.popBackStack() },
                    onManageRulesClick = { navController.navigate(Destination.CategoryManagement.route) },
                )
            }
            composable(Destination.CategoryManagement.route) { CategoryManagementScreen(onBackClick = { navController.popBackStack() }) }
            composable(Destination.MerchantManagement.route) { MerchantManagementScreen(onBackClick = { navController.popBackStack() }) }
            composable(Destination.NetWorth.route) { NetWorthScreen(onBackClick = { navController.popBackStack() }) }
            composable(Destination.YearReview.route) { YearReviewScreen(onBackClick = { navController.popBackStack() }) }
            composable(Destination.Subscriptions.route) { SubscriptionsScreen(onBackClick = { navController.popBackStack() }) }
            composable(Destination.SavingsGoals.route) { SavingsGoalsScreen(onBackClick = { navController.popBackStack() }) }
            composable(Destination.Accounts.route) { AccountsScreen(onBackClick = { navController.popBackStack() }) }
            composable(Destination.Appearance.route) { AppearanceScreen(onBackClick = { navController.popBackStack() }) }
            composable(Destination.LockPrivacy.route) { LockPrivacyScreen(onBackClick = { navController.popBackStack() }) }
            composable(Destination.Notifications.route) { NotificationsScreen(onBackClick = { navController.popBackStack() }) }
            composable(Destination.MonthStart.route) { MonthStartScreen(onBackClick = { navController.popBackStack() }) }
            composable(Destination.BackupExport.route) { BackupExportScreen(onBackClick = { navController.popBackStack() }) }
        }
    }
}

@Composable
private fun NavIcon(destination: Destination) {
    when (destination) {
        Destination.Vandaag -> VandaagIcon()
        Destination.Transactions -> TransactionsIcon()
        Destination.Budgets -> BudgetsIcon()
        Destination.Charts -> ChartsIcon()
        Destination.Meer -> MeerIcon()
        Destination.Import -> Unit
        Destination.TransactionDetail -> Unit
        Destination.CategoryManagement -> Unit
        Destination.MerchantManagement -> Unit
        Destination.NetWorth -> Unit
        Destination.YearReview -> Unit
        Destination.Subscriptions -> Unit
        Destination.SavingsGoals -> Unit
        Destination.Accounts -> Unit
        Destination.Appearance -> Unit
        Destination.LockPrivacy -> Unit
        Destination.Notifications -> Unit
        Destination.MonthStart -> Unit
        Destination.BackupExport -> Unit
    }
}
