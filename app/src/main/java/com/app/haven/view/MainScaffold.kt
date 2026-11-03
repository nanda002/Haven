package com.app.haven.view

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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.app.haven.viewmodel.HomeViewModel

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", "Home", Icons.Default.Home),
    Tab("ledger", "Ledger", Icons.AutoMirrored.Filled.List),
    Tab("chores", "Chores", Icons.Default.Check),
    Tab("pantry", "Pantry", Icons.Default.ShoppingCart)
)

@Composable
fun MainScaffold(onLogout: () -> Unit) {
    val nav = rememberNavController()
    val current = nav.currentBackStackEntryAsState().value?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = current == tab.route,
                        onClick = {
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(padding)) {
            composable("home") { HomeScreen(onLogout) }
            composable("ledger") { Placeholder("Ledger") }    // replace with LedgerScreen
            composable("chores") { Placeholder("Chores") }    // replace with ChoresScreen
            composable("pantry") { Placeholder("Pantry") }    // replace with GroceryScreen
        }
    }
}

@Composable
private fun Placeholder(title: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("$title coming soon", style = MaterialTheme.typography.titleMedium)
    }
}

// Temporary home: shows the household and its invite code so roommates can join.
// This becomes the dashboard (balances + chores due) later.
@Composable
private fun HomeScreen(onLogout: () -> Unit, vm: HomeViewModel = viewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.load() }

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            ui.loading -> CircularProgressIndicator()
            ui.error != null -> {
                Text(ui.error!!, color = MaterialTheme.colorScheme.error)
                Button({ vm.load() }) { Text("Retry") }
            }
            else -> ui.household?.let { h ->
                Text(h.name, style = MaterialTheme.typography.headlineMedium)
                Text("Invite code: ${h.inviteCode}", style = MaterialTheme.typography.titleMedium)
                Text("${h.memberIds.size} member(s)")
            }
        }
        Spacer(Modifier.weight(1f))
        OutlinedButton(onLogout) { Text("Log out") }
    }
}