package com.app.haven.view.homeUI

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.haven.view.expenseUI.money
import com.app.haven.viewmodel.home.HomeBalanceState
import com.app.haven.viewmodel.home.HomeBalanceViewModel

// Dashboard card: what I'm owed + what I owe. Tap -> Ledger tab
@Composable
fun HomeBalanceCard(
    householdId: String,
    myUid: String,
    onClick: () -> Unit,
    vm: HomeBalanceViewModel = viewModel(factory = HomeBalanceViewModel.factory(householdId, myUid))
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (val s = state) {
                HomeBalanceState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                is HomeBalanceState.Error -> Text(s.message, color = MaterialTheme.colorScheme.error)
                is HomeBalanceState.Success -> {
                    val b = s.balance
                    if (b.iOweCents == 0L && b.owedToMeCents == 0L) {
                        Text("All settled up", style = MaterialTheme.typography.titleMedium)
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("You're owed", style = MaterialTheme.typography.labelMedium)
                                Text(b.owedToMeCents.money(), style = MaterialTheme.typography.headlineSmall)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("You owe", style = MaterialTheme.typography.labelMedium)
                                Text(b.iOweCents.money(), style = MaterialTheme.typography.headlineSmall)
                            }
                        }
                    }
                    Text("Tap to open Ledger", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}