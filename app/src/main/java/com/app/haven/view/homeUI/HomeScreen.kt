package com.app.haven.view.homeUI

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.haven.domain.household.canLeaveHousehold
import com.app.haven.view.expenseUI.money
import com.app.haven.viewmodel.home.HomeBalanceState
import com.app.haven.viewmodel.home.HomeBalanceViewModel
import com.app.haven.viewmodel.home.HomeViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu


@Composable
fun HomeScreen(
    householdId: String?,
    myUid: String?,
    onOpenProfile: () -> Unit,
    onOpenHouseholdDetails: () -> Unit,
    onOpenLedger: () -> Unit,
    onLeftHousehold: () -> Unit,
    onLogout: () -> Unit,
    vm: HomeViewModel = viewModel()
){
    val ui by vm.ui.collectAsStateWithLifecycle()
    val leaving by vm.leaving.collectAsStateWithLifecycle()
    val leaveError by vm.leaveError.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.load() }

    var menuOpen by remember { mutableStateOf(false) }
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
                    BalanceAndLeave(
                        householdId, myUid, leaving,
                        onOpenLedger = onOpenLedger,
                        onConfirmLeave = { vm.leaveHousehold(onLeftHousehold) }
                    )
                }
                leaveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
        Spacer(Modifier.weight(1f))
        OutlinedButton(onLogout) { Text("Log out") }
    }
}

// Balance card + Leave button. Leave is only enabled once everything is settled
@Composable
private fun BalanceAndLeave(
    householdId: String,
    myUid: String,
    leaving: Boolean,
    onOpenLedger: () -> Unit,
    onConfirmLeave: () -> Unit,
    balanceVm: HomeBalanceViewModel = viewModel(factory = HomeBalanceViewModel.factory(householdId, myUid))
) {
    val state by balanceVm.state.collectAsStateWithLifecycle()
    var confirming by remember { mutableStateOf(false) }

    val balance = (state as? HomeBalanceState.Success)?.balance
    val canLeave = balance != null && canLeaveHousehold(balance)

    HomeBalanceCard(state, onClick = onOpenLedger)

    OutlinedButton(
        onClick = { confirming = true },
        enabled = canLeave && !leaving,
        modifier = Modifier.fillMaxWidth()
    ) { Text(if (leaving) "Leaving..." else "Leave household") }

    if (balance != null && !canLeave) {
        Text(
            "Settle up before leaving (you're owed ${balance.owedToMeCents.money()}, you owe ${balance.iOweCents.money()})",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Leave household?") },
            text = { Text("You'll lose access to its expenses. You can rejoin later with the invite code.") },
            confirmButton = {
                TextButton({ confirming = false; onConfirmLeave() }) {
                    Text("Leave", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton({ confirming = false }) { Text("Cancel") } }
        )
    }
}