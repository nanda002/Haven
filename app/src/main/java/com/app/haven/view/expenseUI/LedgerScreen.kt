package com.app.haven.view.expenseUI

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.haven.domain.expense.Expense
import com.app.haven.domain.expense.ExpenseCategory
import com.app.haven.ui.theme.LedgerBackground
import com.app.haven.ui.theme.LedgerCard
import com.app.haven.ui.theme.LedgerMuted
import com.app.haven.ui.theme.LedgerText
import com.app.haven.viewmodel.expense.LedgerUiState
import com.app.haven.viewmodel.expense.LedgerViewModel
import kotlinx.coroutines.launch
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
    val scope = rememberCoroutineScope()
    var pendingDelete by remember { mutableStateOf<Expense?>(null) }   // expense waiting for confirmation

    LaunchedEffect(deleteError) {
        deleteError?.let { snackbar.showSnackbar(it); vm.onDeleteErrorShown() }
    }

    pendingDelete?.let { e ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete expense?") },
            text = { Text("\"${e.description.capitalized()}\" (${e.amountCents.money()}) will be removed for everyone in the household.") },
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
        containerColor = LedgerBackground,       // pastel blue behind the white cards
        contentColor = LedgerText,
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            // white with a black + so it pops off the blue background
            FloatingActionButton(onAddExpense, containerColor = LedgerCard, contentColor = LedgerText) {
                Icon(Icons.Default.Add, contentDescription = "Add expense")
            }
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
                is LedgerUiState.Success -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(top = 16.dp)   // a bit of space above the balance card
                ) {
                    // balance card, tap to open the settle up screen
                    item {
                        ElevatedCard(
                            onClick = onOpenBalances,
                            // extra space between the balance card and the expenses below
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            colors = CardDefaults.elevatedCardColors(containerColor = LedgerCard, contentColor = LedgerText)
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Row(Modifier.fillMaxWidth()) {
                                    Column(Modifier.weight(1f)) {
                                        Text("You owe", style = MaterialTheme.typography.labelLarge)
                                        Text(
                                            s.balance.iOweCents.money(),
                                            style = MaterialTheme.typography.headlineSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text("You're owed", style = MaterialTheme.typography.labelLarge)
                                        Text(
                                            s.balance.owedToMeCents.money(),
                                            style = MaterialTheme.typography.headlineSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Text("Tap to see balances and settle up ›", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    item {
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CategoryChip("All", s.selectedCategory == null) { vm.onCategorySelected(null) }
                            ExpenseCategory.entries.forEach { c ->
                                CategoryChip(c.name, s.selectedCategory == c) { vm.onCategorySelected(c) }
                            }
                        }
                    }
                    item { Text("Total: ${s.categoryTotalCents.money()}", style = MaterialTheme.typography.bodySmall) }
                    items(s.rows, key = { it.expense.id }) { row ->
                        // each expense in its own raised box; tap it to edit
                        // (locked ones, involving someone who left, just explain why they can't be changed)
                        ElevatedCard(
                            onClick = {
                                if (row.locked) scope.launch { snackbar.showSnackbar(LedgerViewModel.LOCKED_MESSAGE) }
                                else onEditExpense(row.expense.id)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.elevatedCardColors(containerColor = LedgerCard, contentColor = LedgerText)
                        ) {
                            ListItem(
                                // transparent so the card's white and shadow show through; dark text
                                colors = ListItemDefaults.colors(
                                    containerColor = Color.Transparent,
                                    headlineColor = LedgerText,
                                    supportingColor = LedgerText,
                                    trailingIconColor = LedgerText
                                ),
                                headlineContent = { Text(row.expense.description.capitalized(), fontWeight = FontWeight.SemiBold) },
                                supportingContent = {
                                    // payer and the user's share on separate lines
                                    Column {
                                        // "you" instead of your own name, so it's clear who paid
                                        Text("Paid by ${if (row.expense.paidByUid == myUid) "you" else row.expense.paidByName}")
                                        Text("Your share ${row.userShare.money()}")
                                    }
                                },
                                trailingContent = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(row.expense.amountCents.money())
                                        Spacer(Modifier.width(8.dp))
                                        //for showing the data when the expense was created
                                        Text(
                                            row.expense.createdAtMillis?.shortDate() ?: "Pending",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (row.locked) {
                                            //locked: no delete, just a lock (same size as the button so rows line up)
                                            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                                Icon(Icons.Default.Lock, contentDescription = "Locked expense", tint = LedgerMuted)
                                            }
                                        } else {
                                            //for deleting an expense
                                            IconButton({ pendingDelete = row.expense }) {
                                                Icon(Icons.Default.Delete, contentDescription = "Delete expense", tint = LedgerMuted)
                                            }
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
}

// category filter chip: the selected one gets a thick border instead of a different background
@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.Transparent,
            selectedContainerColor = Color.Transparent,
            labelColor = LedgerText,
            selectedLabelColor = LedgerText
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = LedgerText.copy(alpha = 0.4f),
            selectedBorderColor = LedgerText,
            borderWidth = 1.dp,
            selectedBorderWidth = 2.5.dp
        )
    )
}

// "shower curtains" -> "Shower curtains" (display only; the saved text isn't changed)
private fun String.capitalized(): String = replaceFirstChar { it.uppercase() }

// cents -> "$12.50" (negatives show as "-$1.50")
fun Long.money(): String {
    val a = abs(this)
    return (if (this < 0) "-$" else "$") + String.format(Locale.US, "%d.%02d", a / 100, a % 100)
}

fun Long.shortDate(): String {
    val date = java.util.Date(this)
    val thisYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
    val year = java.util.Calendar.getInstance().apply { time = date }.get(java.util.Calendar.YEAR)
    //if expense was created this year, we only show the month and date, else we show all three
    //including the year
    val pattern = if (year == thisYear) "MMM d" else "MMM d, yyyy"
    return java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault()).format(date)
}

