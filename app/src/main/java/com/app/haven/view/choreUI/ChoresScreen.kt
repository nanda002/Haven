package com.app.haven.view.choreUI

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.haven.domain.chore.Chore
import com.app.haven.domain.chore.ChoreStatus
import com.app.haven.domain.chore.dueLabel
import com.app.haven.domain.chore.repeatLabel
import com.app.haven.domain.chore.status
import com.app.haven.viewmodel.chore.ChoresUiState
import com.app.haven.viewmodel.chore.ChoresViewModel

@Composable
fun ChoresScreen(
    householdId: String,
    myUid: String,
    onAddChore: () -> Unit,
    vm: ChoresViewModel = viewModel(factory = ChoresViewModel.factory(householdId, myUid))
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var pendingDelete by remember { mutableStateOf<Chore?>(null) }

    LaunchedEffect(error) {
        error?.let { snackbar.showSnackbar(it); vm.onErrorShown() }
    }

    pendingDelete?.let { c ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete chore?") },
            text = { Text("\"${c.title}\" will be removed for everyone in the household.") },
            confirmButton = {
                TextButton({ vm.onDelete(c.id); pendingDelete = null }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton({ pendingDelete = null }) { Text("Cancel") } }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onSurface,
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(
                onAddChore,
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) { Icon(Icons.Default.Add, contentDescription = "Add chore") }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(
                "Chores",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 8.dp)
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (val s = state) {
                    ChoresUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    ChoresUiState.Empty -> Text("No chores yet", Modifier.align(Alignment.Center))
                    is ChoresUiState.Error -> Text(
                        s.message, Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.error
                    )
                    is ChoresUiState.Success -> LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(s.chores, key = { it.id }) { chore ->
                            ChoreRow(
                                chore = chore,
                                today = s.today,
                                onToggle = { vm.onToggleDone(chore) },
                                onDelete = { pendingDelete = chore }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoreRow(chore: Chore, today: Long, onToggle: () -> Unit, onDelete: () -> Unit) {
    val overdue = chore.status(today) == ChoreStatus.OVERDUE
    val subtitle = listOfNotNull(
        chore.assigneeName,
        chore.dueLabel(today),
        repeatLabel(chore.repeatDays)
    ).joinToString(" · ")

    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Row(Modifier.fillMaxWidth().padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = chore.done, onCheckedChange = { onToggle() })
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(
                    chore.title,
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (chore.done) TextDecoration.LineThrough else null
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (overdue) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete, contentDescription = "Delete chore",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}