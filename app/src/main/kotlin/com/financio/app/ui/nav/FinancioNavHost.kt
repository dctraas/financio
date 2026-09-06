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
import com.financio.app.ui.savings.SavingsGoalsScreen
import com.financio.app.ui.settings.SettingsScreen
import com.financio.app.ui.subscriptions.SubscriptionsScreen
import com.financio.app.ui.transactions.TransactionsScreen

private const val ARG_CATEGORY_ID = "categoryId"
private const val CHARTS_ROUTE = "charts?categoryId={categoryId}"

private sealed class Destination(val route: String, val label: String) {
    data object Transactions : Destination("transactions", "Transacties")
    data object Budgets : Destination("budgets", "Budgetten")
    /** Registered with an optional `categoryId` so Budgets can deep-link into one category's chart. */
    data object Charts : Destination(CHARTS_ROUTE, "Grafieken")
    data object Settings : Destination("settings", "Instellingen")
    data object Import : Destination("import", "Importeren")
    data object CategoryManagement : Destination("categories", "Categorieën & regels")
    data object Subscriptions : Destination("subscriptions", "Abonnementen")
    data object SavingsGoals : Destination("savings-goals", "Spaardoelen")
    data object Accounts : Destination("accounts", "Rekeningen")
}

private val bottomTabs = listOf(Destination.Transactions, Destination.Budgets, Destination.Charts, Destination.Settings)

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
            startDestination = Destination.Transactions.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Destination.Transactions.route) {
                TransactionsScreen(onImportClick = { navController.navigate(Destination.Import.route) })
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
                ChartsScreen(initialCategoryId = categoryId)
            }
            composable(Destination.Settings.route) {
                SettingsScreen(
                    onManageCategoriesClick = { navController.navigate(Destination.CategoryManagement.route) },
                    onSubscriptionsClick = { navController.navigate(Destination.Subscriptions.route) },
                    onSavingsGoalsClick = { navController.navigate(Destination.SavingsGoals.route) },
                    onAccountsClick = { navController.navigate(Destination.Accounts.route) },
                )
            }
            composable(Destination.Import.route) { ImportScreen(onDone = { navController.popBackStack() }) }
            composable(Destination.CategoryManagement.route) { CategoryManagementScreen() }
            composable(Destination.Subscriptions.route) { SubscriptionsScreen() }
            composable(Destination.SavingsGoals.route) { SavingsGoalsScreen() }
            composable(Destination.Accounts.route) { AccountsScreen() }
        }
    }
}

@Composable
private fun NavIcon(destination: Destination) {
    when (destination) {
        Destination.Transactions -> TransactionsIcon()
        Destination.Budgets -> BudgetsIcon()
        Destination.Charts -> ChartsIcon()
        Destination.Settings -> SettingsIcon()
        Destination.Import -> Unit
        Destination.CategoryManagement -> Unit
        Destination.Subscriptions -> Unit
        Destination.SavingsGoals -> Unit
        Destination.Accounts -> Unit
    }
}
