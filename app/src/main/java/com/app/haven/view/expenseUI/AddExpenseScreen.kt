package com.app.haven.view.expenseUI

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.haven.domain.expense.ExpenseCategory
import com.app.haven.viewmodel.expense.AddExpenseViewModel
import com.app.haven.viewmodel.expense.SaveStatus
import com.app.haven.viewmodel.expense.SplitMode

// Basic add/edit expense form; every field calls the matching AddExpenseViewModel event.
// Pass expenseId to edit an existing expense, leave it null to add a new one.
@Composable
fun AddExpenseScreen(
    householdId: String,
    myUid: String,
    onDone: () -> Unit,
    expenseId: String? = null,
    vm: AddExpenseViewModel = viewModel(factory = AddExpenseViewModel.factory(householdId, myUid, expenseId))
) {
    val s by vm.uiState.collectAsStateWithLifecycle()

    // go back once saved
    LaunchedEffect(s.saveStatus) {
        if (s.saveStatus == SaveStatus.SAVED) { vm.onSavedHandled(); onDone() }
    }

    if (s.loadingExpense) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // close without saving; same as pressing back
            IconButton(onDone) { Icon(Icons.Default.Close, contentDescription = "Cancel") }
            Text(if (s.isEditing) "Edit expense" else "Add expense", style = MaterialTheme.typography.headlineSmall)
        }

        OutlinedTextField(s.description, vm::onDescriptionChanged, label = { Text("Description") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        s.descriptionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        OutlinedTextField(s.amountText, vm::onAmountChanged, label = { Text("Amount ($)") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth())
        s.amountError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Text("Category")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExpenseCategory.entries.forEach { c ->
                FilterChip(s.category == c, { vm.onCategorySelected(c) }, label = { Text(c.name) })
            }
        }

        Text("Paid by")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            s.members.forEach { m ->
                FilterChip(s.paidByUid == m.uid, { vm.onPaidByChanged(m.uid) }, label = { Text(m.name) })
            }
        }

        Text("Split")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SplitMode.entries.forEach { mode ->
                FilterChip(s.splitMode == mode, { vm.onSplitModeChanged(mode) }, label = { Text(mode.name) })
            }
        }
        s.members.forEach { m ->
            if (s.splitMode == SplitMode.EVEN) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(m.uid in s.includedUids, { vm.onMemberToggled(m.uid) })
                    Text(m.name, Modifier.weight(1f))
                    s.evenShares[m.uid]?.let { Text(it.money()) }
                }
            } else {
                OutlinedTextField(s.customAmountTexts[m.uid] ?: "", { vm.onCustomAmountChanged(m.uid, it) },
                    label = { Text(m.name) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth())
            }
        }
        s.remainingCents?.let { Text("Left to assign: ${it.money()}") }
        s.splitError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        s.saveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Button(vm::onSaveClicked, Modifier.fillMaxWidth(), enabled = s.saveStatus != SaveStatus.SAVING) {
            Text(
                when {
                    s.saveStatus == SaveStatus.SAVING -> "Saving…"
                    s.isEditing -> "Save changes"
                    else -> "Save"
                }
            )
        }
    }
}
