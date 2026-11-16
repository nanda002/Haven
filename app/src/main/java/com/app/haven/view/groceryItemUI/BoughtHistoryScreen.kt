package com.app.haven.view.groceryItemUI

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.haven.domain.grocerylist.GroceryItem
import com.app.haven.domain.grocerylist.boughtDayLabel
import com.app.haven.viewmodel.groceryitem.BoughtHistoryViewModel

//opened from "Show Bought Items": a date heading ("Nov 3rd") with the items bought that day underneath
@Composable
fun BoughtHistoryScreen(
    householdId: String,
    onBack: () -> Unit,
    vm: BoughtHistoryViewModel = viewModel(factory = BoughtHistoryViewModel.factory(householdId))
) {
    val s by vm.uiState.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("Bought items", style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(Modifier.height(8.dp))

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                s.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                s.error != null -> Text(s.error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.align(Alignment.Center))
                s.days.isEmpty() -> Text("Nothing bought yet", Modifier.align(Alignment.Center))
                else -> LazyColumn {
                    s.days.forEach { day ->
                        //date heading
                        item(key = "day-${day.dayKey}") {
                            Text(
                                boughtDayLabel(day.dayKey),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 4.dp)
                            )
                            HorizontalDivider()
                        }
                        //items bought that day
                        items(day.items, key = { it.id }) { item -> BoughtRow(item) }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoughtRow(item: GroceryItem) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
        Text(item.description, style = MaterialTheme.typography.bodyLarge)
        val subtitle = "Bought by ${item.paidByName}" + if (item.addedToExpense) " · added to an expense" else ""
        Text(subtitle, style = MaterialTheme.typography.bodySmall)
    }
}
