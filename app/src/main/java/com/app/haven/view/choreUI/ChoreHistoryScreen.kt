package com.app.haven.view.choreUI

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.haven.domain.chore.ChoreCompletion
import com.app.haven.domain.chore.formatEpochDay
import com.app.haven.view.common.PageHeader
import com.app.haven.viewmodel.chore.ChoreHistoryState
import com.app.haven.viewmodel.chore.ChoreHistoryViewModel

// Every finished household chore, grouped by the day it was completed
@Composable
fun ChoreHistoryScreen(
    householdId: String,
    onBack: () -> Unit,
    vm: ChoreHistoryViewModel = viewModel(factory = ChoreHistoryViewModel.factory(householdId))
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        PageHeader("Chore history", onBack)
        when (val s = state) {
            ChoreHistoryState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            ChoreHistoryState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("Couldn't load the history. Check your connection and try again.", color = MaterialTheme.colorScheme.error)
            }
            is ChoreHistoryState.Loaded ->
                if (s.items.isEmpty()) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) { Text("No finished chores yet") }
                } else {
                    val byDay = s.items.groupBy { it.completedEpochDay }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
                        byDay.forEach { (day, list) ->
                            item(key = "day_$day") {
                                Text(formatEpochDay(day), style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.padding(top = 8.dp))
                            }
                            items(list, key = { it.id }) { HistoryRow(it) }
                        }
                    }
                }
        }
    }
}

@Composable
private fun HistoryRow(c: ChoreCompletion) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(c.choreTitle, style = MaterialTheme.typography.titleMedium)
            Text("Done by ${c.completedByName}", style = MaterialTheme.typography.bodyMedium)
            if (c.completedByUid != c.assignedToUid) {
                Text("It was assigned to someone else, picked up when overdue",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (c.originalAssigneeUid.isNotBlank() && c.originalAssigneeUid != c.assignedToUid) {
                Text("Handed over before it was done",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}