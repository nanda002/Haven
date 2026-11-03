package com.app.haven.view.expenseUI

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.haven.domain.expense.Expense
import com.app.haven.domain.expense.ExpenseCategory
import com.app.haven.viewmodel.expense.LedgerUiState
import com.app.haven.viewmodel.expense.LedgerViewModel
import java.util.Locale
import kotlin.math.abs

// Basic ledger: balance summary, category filter and the list of expenses
@Composable
fun LedgerScreen(
    householdId: String,
    myUid: String,
    onAddExpense: () -> Unit,
    onEditExpense: (expenseId: String) -> Unit,
    onOpenBalances: () -> Unit,
    vm: LedgerViewModel = viewModel(factory = LedgerViewModel.factory(householdId, myUid))
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val deleteError by vm.deleteError.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var pendingDelete by remember { mutableStateOf<Expense?>(null) }   // expense waiting for confirmation

    LaunchedEffect(deleteError) {
        deleteError?.let { snackbar.showSnackbar(it); vm.onDeleteErrorShown() }
    }

    pendingDelete?.let { e ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete expense?") },
            text = { Text("\"${e.description}\" (${e.amountCents.money()}) will be removed for everyone in the household.") },
            confirmButton = {
                TextButton({ vm.onDelete(e.id); pendingDelete = null }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton({ pendingDelete = null }) { Text("Cancel") } }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),   // outer MainScaffold already handles insets
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(onAddExpense) { Icon(Icons.Default.Add, contentDescription = "Add expense") }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            when (val s = state) {
                LedgerUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                LedgerUiState.Empty -> Text("No expenses yet", Modifier.align(Alignment.Center))
                is LedgerUiState.Error -> Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(s.message, color = MaterialTheme.colorScheme.error)
                    Button(vm::onRetry) { Text("Retry") }
                }
                is LedgerUiState.Success -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // balance card, tap to open the settle up screen
                    item {
                        Card(onClick = onOpenBalances, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    "You owe ${s.balance.iOweCents.money()} · You're owed ${s.balance.owedToMeCents.money()}",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text("Tap to see balances and settle up", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    item {
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(s.selectedCategory == null, { vm.onCategorySelected(null) }, label = { Text("All") })
                            ExpenseCategory.entries.forEach { c ->
                                FilterChip(s.selectedCategory == c, { vm.onCategorySelected(c) }, label = { Text(c.name) })
                            }
                        }
                    }
                    item { Text("Total: ${s.categoryTotalCents.money()}", style = MaterialTheme.typography.bodySmall) }
                    items(s.rows, key = { it.expense.id }) { row ->
                        ListItem(
                            modifier = Modifier.clickable { onEditExpense(row.expense.id) },   // tap a row to edit it
                            headlineContent = { Text(row.expense.description) },
                            supportingContent = {
                                Text("Paid by ${row.expense.paidByName} · your share ${row.userShare.money()}")
                            },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(row.expense.amountCents.money())
                                    IconButton({ pendingDelete = row.expense }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete expense")
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

// cents -> "$12.50" (negatives show as "-$1.50")
fun Long.money(): String {
    val a = abs(this)
    return (if (this < 0) "-$" else "$") + String.format(Locale.US, "%d.%02d", a / 100, a % 100)
}

