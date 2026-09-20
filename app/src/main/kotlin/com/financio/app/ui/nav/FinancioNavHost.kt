package com.financio.app.ui.nav

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.financio.app.data.local.StartTab
import com.financio.app.ui.accounts.AccountsScreen
import com.financio.app.ui.budgets.BudgetsScreen
import com.financio.app.ui.categories.CategoryManagementScreen
import com.financio.app.ui.categorizequeue.CategorizeQueueScreen
import com.financio.app.ui.charts.ChartsScreen
import com.financio.app.ui.debts.DebtsScreen
import com.financio.app.ui.importing.ImportScreen
import com.financio.app.ui.meer.MeerScreen
import com.financio.app.ui.merchants.MerchantManagementScreen
import com.financio.app.ui.networth.NetWorthScreen
import com.financio.app.ui.savings.SavingsGoalsScreen
import com.financio.app.ui.settings.BackupExportScreen
import com.financio.app.ui.settings.MonthStartScreen
import com.financio.app.ui.settings.SettingsScreen
import com.financio.app.ui.subscriptions.SubscriptionsScreen
import com.financio.app.ui.transactions.TransactionDetailScreen
import com.financio.app.ui.transactions.TransactionsScreen
import com.financio.app.ui.vandaag.VandaagScreen
import com.financio.app.ui.yearreview.YearReviewScreen

private const val ARG_CATEGORY_ID = "categoryId"
private const val ARG_TRANSACTION_ID = "transactionId"
private const val ARG_UNCATEGORIZED = "uncategorized"
private const val ARG_FILTER_ID = "filterId"
private const val CHARTS_ROUTE = "charts?categoryId={categoryId}"
private const val TRANSACTION_DETAIL_ROUTE = "transaction/{transactionId}"
private const val TRANSACTIONS_ROUTE = "transactions?uncategorized={uncategorized}&filterId={filterId}"

private sealed class Destination(val route: String, val label: String) {
    // Vandaag is the start destination - "het antwoord, niet de data" - so it's first both here
    // and in the bottom bar.
    data object Vandaag : Destination("vandaag", "Vandaag")
    /** Registered with optional `uncategorized`/`filterId` args so Vandaag's "Nu doen" tile and pinned saved-filter chips can both deep-link straight into an already-filtered list. */
    data object Transactions : Destination(TRANSACTIONS_ROUTE, "Transacties")
    data object Budgets : Destination("budgets", "Budget")
    /** Registered with an optional `categoryId` so Budget can deep-link into one category's chart. */
    data object Charts : Destination(CHARTS_ROUTE, "Inzicht")
    /** Route/screen title stay "meer"/`MeerScreen` (see github.md's screen map) - only the tab's own label changed to match the schermontwerp redesign's "Beheer" rename. */
    data object Meer : Destination("meer", "Beheer")
    data object Import : Destination("import", "Importeren")
    /** Registered with a required `transactionId` — what a tap on a transaction row now opens (R3). */
    data object TransactionDetail : Destination(TRANSACTION_DETAIL_ROUTE, "Transactie")
    data object CategoryManagement : Destination("categories", "Categorieën & regels")
    /** The "spelletje" categorize screen over the already-imported backlog - reached from Vandaag's "Nu doen" tile or Transacties' "Zonder categorie" filter. */
    data object CategorizeQueue : Destination("categorize-queue", "Categoriseren")
    data object MerchantManagement : Destination("merchants", "Tegenpartijen")
    data object NetWorth : Destination("net-worth", "Vermogen")
    data object YearReview : Destination("year-review", "Jaaroverzicht")
    data object Subscriptions : Destination("subscriptions", "Abonnementen")
    data object SavingsGoals : Destination("savings-goals", "Spaardoelen")
    data object Debts : Destination("debts", "Schulden & leningen")
    data object Accounts : Destination("accounts", "Rekeningen")
    /** Screen 17 "Instellingen" - folds what used to be Weergave/Vergrendeling & privacy/Meldingen into one screen; see SettingsScreen's own doc comment. */
    data object Settings : Destination("settings", "Instellingen")
    data object MonthStart : Destination("settings/month-start", "Maand begint op")
    data object BackupExport : Destination("settings/backup-export", "Back-up & export")
}

// Order per the schermontwerp redesign: Inzicht now comes before Doelen (was the reverse).
private val bottomTabs = listOf(Destination.Vandaag, Destination.Transactions, Destination.Charts, Destination.SavingsGoals, Destination.Meer)

/** See [com.financio.app.data.local.AppPreferences.startTab]'s own doc comment - only picks which bottom tab opens first; the arg-optional Transacties/Inzicht routes still resolve to their bare, un-filtered path here, same as a plain bottom-tab tap does. */
private fun startRouteFor(startTab: StartTab): String = when (startTab) {
    StartTab.VANDAAG -> Destination.Vandaag.route
    StartTab.TRANSACTIES -> "transactions"
    StartTab.INZICHT -> "charts"
    StartTab.DOELEN -> Destination.SavingsGoals.route
    StartTab.MEER -> Destination.Meer.route
}

@Composable
fun FinancioNavHost(startTab: StartTab = StartTab.VANDAAG) {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = backStackEntry?.destination?.route
            if (bottomTabs.any { it.route == currentRoute }) {
                Column {
                    // NavigationBar draws no border of its own - a 1dp top line is how the
                    // schermontwerp redesign separates the bar from scrolled-under content
                    // instead of an elevation shadow (the design has none anywhere).
                    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                        bottomTabs.forEach { destination ->
                            NavigationBarItem(
                                selected = currentRoute == destination.route,
                                onClick = {
                                    // Charts and Transactions are both registered with an optional
                                    // arg; a plain tab tap navigates to the bare path so it falls
                                    // back to the default instead of literally targeting the
                                    // "{categoryId}"/"{uncategorized}" placeholder route string.
                                    val target = when (destination) {
                                        Destination.Charts -> "charts"
                                        Destination.Transactions -> "transactions"
                                        else -> destination.route
                                    }
                                    navController.navigate(target) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { NavIcon(destination) },
                                label = { Text(destination.label, fontWeight = if (currentRoute == destination.route) FontWeight.SemiBold else FontWeight.Medium) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startRouteFor(startTab),
            modifier = Modifier.padding(padding),
        ) {
            composable(Destination.Vandaag.route) {
                VandaagScreen(
                    onSeeAllClick = {
                        navController.navigate("transactions") {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onCategorizeClick = { navController.navigate(Destination.CategorizeQueue.route) },
                    onImportClick = { navController.navigate(Destination.Import.route) },
                    onOpenDetail = { transactionId -> navController.navigate("transaction/$transactionId") },
                    // Deliberately not popUpTo/saveState/restoreState like the bottom-tab switch
                    // above: restoring a previously saved Transacties back stack entry would keep
                    // its old filter and silently ignore the new filterId this chip is meant to
                    // apply. A plain navigate (same as onOpenDetail's drill-in) always re-reads it.
                    onOpenSavedFilter = { filterId -> navController.navigate("transactions?filterId=$filterId") { launchSingleTop = true } },
                )
            }
            composable(
                route = Destination.Transactions.route,
                arguments = listOf(
                    navArgument(ARG_UNCATEGORIZED) { type = NavType.BoolType; defaultValue = false },
                    navArgument(ARG_FILTER_ID) { type = NavType.LongType; defaultValue = -1L },
                ),
            ) { backStackEntry ->
                TransactionsScreen(
                    onImportClick = { navController.navigate(Destination.Import.route) },
                    onOpenDetail = { transactionId -> navController.navigate("transaction/$transactionId") },
                    onPlayCategorize = { navController.navigate(Destination.CategorizeQueue.route) },
                    startWithUncategorizedFilter = backStackEntry.arguments?.getBoolean(ARG_UNCATEGORIZED) ?: false,
                    startWithFilterId = backStackEntry.arguments?.getLong(ARG_FILTER_ID)?.takeIf { it > 0 },
                )
            }
            composable(Destination.SavingsGoals.route) { SavingsGoalsScreen() }
            composable(
                route = Destination.Charts.route,
                arguments = listOf(navArgument(ARG_CATEGORY_ID) { type = NavType.LongType; defaultValue = -1L }),
            ) { backStackEntry ->
                val categoryId = backStackEntry.arguments?.getLong(ARG_CATEGORY_ID)?.takeIf { it > 0 }
                ChartsScreen(
                    initialCategoryId = categoryId,
                    onGoToSubscriptionsClick = { navController.navigate(Destination.Subscriptions.route) },
                    onManageMerchantsClick = { navController.navigate(Destination.MerchantManagement.route) },
                    onOpenDetail = { transactionId -> navController.navigate("transaction/$transactionId") },
                )
            }
            composable(Destination.Meer.route) {
                MeerScreen(
                    onSubscriptionsClick = { navController.navigate(Destination.Subscriptions.route) },
                    onBudgetsClick = { navController.navigate(Destination.Budgets.route) },
                    onAccountsClick = { navController.navigate(Destination.Accounts.route) },
                    onManageCategoriesClick = { navController.navigate(Destination.CategoryManagement.route) },
                    onMerchantManagementClick = { navController.navigate(Destination.MerchantManagement.route) },
                    onNetWorthClick = { navController.navigate(Destination.NetWorth.route) },
                    onYearReviewClick = { navController.navigate(Destination.YearReview.route) },
                    onDebtsClick = { navController.navigate(Destination.Debts.route) },
                    onImportClick = { navController.navigate(Destination.Import.route) },
                    onSettingsClick = { navController.navigate(Destination.Settings.route) },
                )
            }
            composable(Destination.Import.route) {
                ImportScreen(
                    onDone = { navController.popBackStack() },
                    onGoToInsights = {
                        navController.navigate("charts") {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
            composable(
                route = Destination.TransactionDetail.route,
                arguments = listOf(navArgument(ARG_TRANSACTION_ID) { type = NavType.LongType }),
            ) {
                TransactionDetailScreen(
                    onBackClick = { navController.popBackStack() },
                    onManageRulesClick = { navController.navigate(Destination.CategoryManagement.route) },
                    onOpenTransaction = { transactionId -> navController.navigate("transaction/$transactionId") },
                )
            }
            composable(Destination.CategoryManagement.route) { CategoryManagementScreen(onBackClick = { navController.popBackStack() }) }
            composable(Destination.CategorizeQueue.route) { CategorizeQueueScreen(onDone = { navController.popBackStack() }) }
            composable(Destination.MerchantManagement.route) { MerchantManagementScreen(onBackClick = { navController.popBackStack() }) }
            composable(Destination.NetWorth.route) { NetWorthScreen(onBackClick = { navController.popBackStack() }) }
            composable(Destination.YearReview.route) { YearReviewScreen(onBackClick = { navController.popBackStack() }) }
            composable(Destination.Subscriptions.route) { SubscriptionsScreen(onBackClick = { navController.popBackStack() }) }
            composable(Destination.Budgets.route) {
                BudgetsScreen(
                    onBackClick = { navController.popBackStack() },
                    onCategoryClick = { categoryId -> navController.navigate("charts?categoryId=$categoryId") { launchSingleTop = true } },
                )
            }
            composable(Destination.Debts.route) { DebtsScreen(onBackClick = { navController.popBackStack() }) }
            composable(Destination.Accounts.route) {
                AccountsScreen(
                    onBackClick = { navController.popBackStack() },
                    onImportClick = { navController.navigate(Destination.Import.route) },
                )
            }
            composable(Destination.Settings.route) {
                SettingsScreen(
                    onBackClick = { navController.popBackStack() },
                    onMonthStartClick = { navController.navigate(Destination.MonthStart.route) },
                    onBackupExportClick = { navController.navigate(Destination.BackupExport.route) },
                )
            }
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
        Destination.SavingsGoals -> SavingsGoalsIcon()
        Destination.Charts -> ChartsIcon()
        Destination.Meer -> MeerIcon()
        Destination.Import -> Unit
        Destination.TransactionDetail -> Unit
        Destination.CategoryManagement -> Unit
        Destination.CategorizeQueue -> Unit
        Destination.MerchantManagement -> Unit
        Destination.NetWorth -> Unit
        Destination.YearReview -> Unit
        Destination.Subscriptions -> Unit
        Destination.Budgets -> Unit
        Destination.Debts -> Unit
        Destination.Accounts -> Unit
        Destination.Settings -> Unit
        Destination.MonthStart -> Unit
        Destination.BackupExport -> Unit
    }
}
