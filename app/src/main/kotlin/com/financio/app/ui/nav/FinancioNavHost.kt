package com.financio.app.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.financio.app.ui.budgets.BudgetsScreen
import com.financio.app.ui.charts.ChartsScreen
import com.financio.app.ui.importing.ImportScreen
import com.financio.app.ui.settings.SettingsScreen
import com.financio.app.ui.transactions.TransactionsScreen

private sealed class Destination(val route: String, val label: String) {
    data object Transactions : Destination("transactions", "Transacties")
    data object Budgets : Destination("budgets", "Budgetten")
    data object Charts : Destination("charts", "Grafieken")
    data object Settings : Destination("settings", "Instellingen")
    data object Import : Destination("import", "Importeren")
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
                                navController.navigate(destination.route) {
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
            composable(Destination.Budgets.route) { BudgetsScreen() }
            composable(Destination.Charts.route) { ChartsScreen() }
            composable(Destination.Settings.route) { SettingsScreen() }
            composable(Destination.Import.route) { ImportScreen(onDone = { navController.popBackStack() }) }
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
    }
}
