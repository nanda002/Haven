package com.app.haven.navigation

import com.app.haven.view.vacationUI.VacationSetupScreen
import com.app.haven.view.choreUI.TransferNotices
import android.net.Uri
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
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import com.app.haven.actions.UserActions
import com.app.haven.data.model.User
import com.app.haven.view.choreUI.AddChoreScreen
import com.app.haven.view.choreUI.ChoresScreen
import com.app.haven.view.common.profileUI.ProfileScreen
import com.app.haven.view.expenseUI.AddExpenseScreen
import com.app.haven.view.expenseUI.LedgerScreen
import com.app.haven.view.expenseUI.SettleExpenseScreen
import com.app.haven.view.groceryItemUI.AddUpPricesScreen
import com.app.haven.view.groceryItemUI.BoughtHistoryScreen
import com.app.haven.view.groceryItemUI.GroceryListRoute
import com.app.haven.view.groceryItemUI.ScanProductScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.haven.view.homeUI.HomeScreen
import com.app.haven.view.homeUI.HouseholdDetailsScreen
import com.app.haven.viewmodel.expense.ExpensePrefill

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", "Home", Icons.Default.Home),
    Tab("ledger_tab", "Ledger", Icons.AutoMirrored.Filled.List),
    Tab("chores_tab", "Chores", Icons.Default.Check),
    Tab("grocery_tab", "Grocery", Icons.Default.ShoppingCart)
)

@Composable
fun MainScaffold(
    onLogout: () -> Unit,
    onLeftHousehold: () -> Unit,
    //true after tapping an expense notification
    openLedger: Boolean = false,
    onLedgerOpened: () -> Unit = {}
) {
    //object that remembers the back stack
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

    //opened from an expense notification to show the ledger
    LaunchedEffect(openLedger) {
        if (openLedger) {
            goToTab("ledger_tab")
            onLedgerOpened()
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        //hierarchy keeps the Ledger tab selected on its inner screens (e.g. Add expense)
                        //basically checks if the current selected tab has any other screens, and this will remain highlighted
                        //if the user goes to those screens
                        selected = currentDest?.hierarchy?.any { it.route == tab.route } == true ||
                                (tab.route == "home" && currentDest?.route in setOf("profile", "household_details", "vacation")),
                        onClick = { goToTab(tab.route) },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            // chores a roommate wants to hand over, and my offers that were declined
            userResult?.getOrNull()?.let { user ->
                user.householdId?.let { householdId ->
                    TransferNotices(householdId, user.uid, onChooseAnother = { nav.navigate("vacation") })
                }
            }
            NavHost(nav, startDestination = "home", modifier = Modifier.weight(1f)) {
            composable("home") {
                val user = userResult?.getOrNull()
                HomeScreen(
                    householdId = user?.householdId,
                    myUid = user?.uid,
                    //the different routes from the home screen
                    onOpenProfile = { nav.navigate("profile") },
                    onOpenHouseholdDetails = { nav.navigate("household_details") },
                    onOpenLedger = { goToTab("ledger_tab") },
                    onOpenChores = { goToTab("chores_tab") },
                    onOpenGrocery = { goToTab("grocery_tab") })
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
                // new expense; "ledger/add" alone is a blank form (or the saved draft),
                // amount (cents) + desc come from the grocery list and start the form filled in,
                // items = comma-separated grocery item ids, taken off the list once the expense is saved
                composable(
                    "ledger/add?amount={amount}&desc={desc}&items={items}",
                    arguments = listOf(
                        navArgument("amount") { type = NavType.LongType; defaultValue = -1L },
                        navArgument("desc") { type = NavType.StringType; nullable = true; defaultValue = null },
                        navArgument("items") { type = NavType.StringType; nullable = true; defaultValue = null }
                    )
                ) { entry ->
                    val amount = entry.arguments?.getLong("amount") ?: -1L
                    val desc = entry.arguments?.getString("desc")
                    val itemIds = entry.arguments?.getString("items").orEmpty().split(",").filter { it.isNotBlank() }
                    val prefill = if (amount > 0 && desc != null) ExpensePrefill(amount, desc, groceryItemIds = itemIds) else null
                    WithHousehold(userResult) { householdId, myUid ->
                        AddExpenseScreen(householdId, myUid, onDone = { nav.popBackStack() }, prefill = prefill)
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
            // opened from "Profile" in the Home menu
            composable("profile") { ProfileScreen(onOpenVacationSetup = { nav.navigate("vacation") }, onClose = { nav.popBackStack() }, onLogout = onLogout) }
            // opened from the Vacation Mode switch on the Profile
            composable("vacation") {
                WithHousehold(userResult) { householdId, myUid ->
                    VacationSetupScreen(
                        householdId, myUid,
                        onClose = { nav.popBackStack() },
                        onOpenChores = { goToTab("chores_tab") },
                        onActivated = { nav.popBackStack() }
                    )
                }
            }
            composable("household_details") {
                val user = userResult?.getOrNull()
                HouseholdDetailsScreen(
                    myUid = user?.uid,
                    onLeftHousehold = onLeftHousehold,
                    onBack = { nav.popBackStack() }
                )
            }

// chores tab: list screen + add form, grouped so the Chores tab stays highlighted on inner screens
            navigation(startDestination = "chores", route = "chores_tab") {
                composable("chores") {
                    WithHousehold(userResult) { householdId, myUid ->
                        ChoresScreen(
                            householdId, myUid,
                            onAddChore = { nav.navigate("chores/add") },
                            onEditChore = { id -> nav.navigate("chores/edit/$id") }
                        )
                    }
                }

                composable("chores/add") {
                    WithHousehold(userResult) { householdId, myUid ->
                        AddChoreScreen(householdId, myUid, onDone = { nav.popBackStack() })
                    }
                }

                // same form, filled in with an existing chore
                composable("chores/edit/{choreId}") { entry ->
                    val choreId = entry.arguments?.getString("choreId")
                    WithHousehold(userResult) { householdId, myUid ->
                        AddChoreScreen(householdId, myUid, choreId = choreId, onDone = { nav.popBackStack() })
                    }
                }
            }

            // grocery tab: shared list + Enter prices, grouped so the Grocery tab stays highlighted
            navigation(startDestination = "grocery", route = "grocery_tab") {
                composable("grocery") { entry ->
                    val scannedCode by entry.savedStateHandle
                        .getStateFlow<String?>("scannedCode", null)
                        .collectAsStateWithLifecycle()
                    WithHousehold(userResult) { householdId, myUid ->
                        GroceryListRoute(
                            householdId, myUid,
                            onAddToExpense = { nav.navigate("grocery/prices") },
                            onShowBought = { nav.navigate("grocery/history") },
                            onScan = { nav.navigate("grocery/scan") },
                            scannedCode = scannedCode,
                            onScannedCodeHandled = { entry.savedStateHandle["scannedCode"] = null }
                        )
                    }
                }
                composable("grocery/scan") {
                    ScanProductScreen(
                        onScanned = { code ->
                            nav.previousBackStackEntry?.savedStateHandle?.set("scannedCode", code)
                            nav.popBackStack()
                        },
                        onClose = { nav.popBackStack() }
                    )
                }
                // "Show Bought Items": items grouped under the day they were bought
                composable("grocery/history") {
                    WithHousehold(userResult) { householdId, _ ->
                        BoughtHistoryScreen(householdId, onBack = { nav.popBackStack() })
                    }
                }
                // price per bought item; Continue opens the Add expense form on the Ledger tab, filled in
                composable("grocery/prices") {
                    WithHousehold(userResult) { householdId, myUid ->
                        AddUpPricesScreen(
                            householdId, myUid,
                            onClose = { nav.popBackStack() },
                            onContinue = { amountCents, description, itemIds ->
                                // switch to the Ledger (so Back from the form lands on the ledger), then open the form
                                goToTab("ledger_tab")
                                nav.navigate(
                                    "ledger/add?amount=$amountCents&desc=${Uri.encode(description)}" +
                                        "&items=${Uri.encode(itemIds.joinToString(","))}"
                                )
                            }
                        )
                    }
                }
            }
        }
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