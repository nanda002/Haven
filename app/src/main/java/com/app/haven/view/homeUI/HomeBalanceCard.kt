package com.app.haven.view.homeUI

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.app.haven.view.expenseUI.money
import com.app.haven.viewmodel.home.HomeBalanceState

// Dashboard card: what I'm owed + what I owe. Tap -> Ledger tab
@Composable
fun HomeBalanceCard(state: HomeBalanceState, onClick: () -> Unit){

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