package com.app.haven.view.homeUI

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.haven.viewmodel.home.HomeBalanceViewModel
import com.app.haven.viewmodel.home.HomeViewModel
import com.app.haven.viewmodel.home.HomeChoresViewModel

@Composable
fun HomeScreen(
    householdId: String?,
    myUid: String?,
    onOpenProfile: () -> Unit,
    onOpenHouseholdDetails: () -> Unit,
    onOpenLedger: () -> Unit,
    onOpenChores: () -> Unit,
    vm: HomeViewModel = viewModel()
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.load() }

    if (ui.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            ui.error != null -> {
                Text(ui.error!!, color = MaterialTheme.colorScheme.error)
                Button({ vm.load() }) { Text("Retry") }
            }
            else -> ui.household?.let { h ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Profile") },
                                onClick = { menuOpen = false; onOpenProfile() }
                            )
                            DropdownMenuItem(
                                text = { Text("Household details") },
                                onClick = { menuOpen = false; onOpenHouseholdDetails() }
                            )
                        }
                    }
                    Text(h.name, style = MaterialTheme.typography.headlineMedium)
                }
                if (householdId != null && myUid != null) {
                    BalanceSection(householdId, myUid, onOpenLedger)
                    ChoresSection(householdId, myUid, onOpenChores)
                }
            }
        }
        // log out lives on the Profile screen
    }
}

// Balance card; tap opens the Ledger tab
@Composable
private fun BalanceSection(
    householdId: String,
    myUid: String,
    onOpenLedger: () -> Unit,
    balanceVm: HomeBalanceViewModel = viewModel(factory = HomeBalanceViewModel.factory(householdId, myUid))
) {
    val state by balanceVm.state.collectAsStateWithLifecycle()
    HomeBalanceCard(state, onClick = onOpenLedger)
}

@Composable
private fun ChoresSection(
    householdId: String,
    myUid: String,
    onOpenChores: () -> Unit,
    choresVm: HomeChoresViewModel = viewModel(factory = HomeChoresViewModel.factory(householdId, myUid))
) {
    val due by choresVm.dueChores.collectAsStateWithLifecycle()
    HomeChoresCard(due, onClick = onOpenChores)
}