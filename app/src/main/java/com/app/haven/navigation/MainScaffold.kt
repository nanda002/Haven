package com.app.haven.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import com.app.haven.actions.UserActions
import com.app.haven.data.model.User
import com.app.haven.view.expenseUI.AddExpenseScreen
import com.app.haven.view.expenseUI.LedgerScreen
import com.app.haven.view.expenseUI.SettleExpenseScreen
import com.app.haven.view.homeUI.HomeScreen
import com.app.haven.view.homeUI.HouseholdDetailsScreen
import com.app.haven.view.homeUI.ProfileScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", "Home", Icons.Default.Home),
    Tab("ledger_tab", "Ledger", Icons.AutoMirrored.Filled.List),
    Tab("chores", "Chores", Icons.Default.Check),
    Tab("pantry", "Pantry", Icons.Default.ShoppingCart)
)

@Composable
fun MainScaffold(onLogout: () -> Unit, onLeftHousehold: () -> Unit) {
    val nav = rememberNavController()
    val currentDest = nav.currentBackStackEntryAsState().value?.destination

    // Ledger screens need the household id and uid; loaded here so the session/login code stays unchanged
    val userResult by produceState<Result<User?>?>(initialValue = null) {
        value = runCatching { UserActions().loadUser() }
    }

    // same behavior as tapping a bottom-bar tab (keeps tab highlight)
    // tabs always open at their main screen, so e.g. a half-filled Add expense form isn't brought back
    // when returning to the Ledger (unsaved form text is still kept as a draft)
    val goToTab: (String) -> Unit = { route ->
        nav.navigate(route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = false }
            launchSingleTop = true
            restoreState = false
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        // hierarchy keeps the Ledger tab selected on its inner screens (e.g. Add expense)
                        selected = currentDest?.hierarchy?.any { it.route == tab.route } == true ||
                                (tab.route == "home" && currentDest?.route in setOf("profile", "household_details")),
                        onClick = { goToTab(tab.route) },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(padding)) {
            composable("home") {
                val user = userResult?.getOrNull()
                HomeScreen(
                    householdId = user?.householdId,
                    myUid = user?.uid,
                    onOpenProfile = { nav.navigate("profile") },
                    onOpenHouseholdDetails = { nav.navigate("household_details") },
                    onOpenLedger = { goToTab("ledger_tab") },
                    onLeftHousehold = onLeftHousehold,
                    onLogout = onLogout
                )
            }
            navigation(startDestination = "ledger", route = "ledger_tab") {
                composable("ledger") {
                    WithHousehold(userResult) { householdId, myUid ->
                        LedgerScreen(
                            householdId, myUid,
                            onAddExpense = { nav.navigate("ledger/add") },
                            onEditExpense = { id -> nav.navigate("ledger/edit/$id") },
                            onOpenBalances = { nav.navigate("ledger/balances") }
                        )
                    }
                }
                // balances with each member + settle up
                composable("ledger/balances") {
                    WithHousehold(userResult) { householdId, myUid ->
                        SettleExpenseScreen(householdId, myUid, onClose = { nav.popBackStack() })
                    }
                }
                composable("ledger/add") {
                    WithHousehold(userResult) { householdId, myUid ->
                        AddExpenseScreen(householdId, myUid, onDone = { nav.popBackStack() })
                    }
                }
                // same form as add, pre-filled with the expense
                composable("ledger/edit/{expenseId}") { entry ->
                    val expenseId = entry.arguments?.getString("expenseId")
                    WithHousehold(userResult) { householdId, myUid ->
                        AddExpenseScreen(householdId, myUid, onDone = { nav.popBackStack() }, expenseId = expenseId)
                    }
                }
            }
            composable("profile") { ProfileScreen(onBack = { nav.popBackStack() }) }
            composable("household_details") {
                val user = userResult?.getOrNull()
                HouseholdDetailsScreen(myUid = user?.uid, onBack = { nav.popBackStack() })
            }
            composable("chores") { Placeholder("Chores") }    // replace with ChoresScreen
            composable("pantry") { Placeholder("Pantry") }    // replace with GroceryScreen
        }
    }
}

// Shows a spinner while the user loads, an error if it failed, otherwise the content with the ids
@Composable
private fun WithHousehold(
    userResult: Result<User?>?,
    content: @Composable (householdId: String, myUid: String) -> Unit
) {
    val user = userResult?.getOrNull()
    val householdId = user?.householdId
    when {
        userResult == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        user == null || householdId == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Couldn't load your household", color = MaterialTheme.colorScheme.error)
        }
        else -> content(householdId, user.uid)
    }
}

@Composable
private fun Placeholder(title: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("$title coming soon", style = MaterialTheme.typography.titleMedium)
    }
}