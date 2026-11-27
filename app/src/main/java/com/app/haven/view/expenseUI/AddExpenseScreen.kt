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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.haven.domain.InputLimits
import com.app.haven.domain.chore.formatFullEpochDay
import com.app.haven.domain.expense.ExpenseCategory
import com.app.haven.view.choreUI.ChoiceCard
import com.app.haven.view.choreUI.rememberTodayEpochDay
import com.app.haven.view.common.LimitedTextField
import com.app.haven.viewmodel.expense.AddExpenseViewModel
import com.app.haven.viewmodel.expense.ExpensePrefill
import com.app.haven.viewmodel.expense.SaveStatus
import com.app.haven.viewmodel.expense.SplitMode

// Basic add/edit expense vm.onDateChanged; every field calls the matching AddExpenseViewModel event.
// Pass expenseId to edit an existing expense, leave it null to add a new one.
@Composable
fun AddExpenseScreen(
    householdId: String,
    myUid: String,
    onDone: () -> Unit,
    expenseId: String? = null,
    //add mode only: start with these values (e.g. groceries total) instead of the saved draft
    prefill: ExpensePrefill? = null,
    vm: AddExpenseViewModel = viewModel(factory = AddExpenseViewModel.factory(householdId, myUid, expenseId, prefill))
) {
    val s by vm.uiState.collectAsStateWithLifecycle()
    var showPicker by rememberSaveable { mutableStateOf(false) }
//    var showEndPicker by rememberSaveable { mutableStateOf(false) }
    val today = rememberTodayEpochDay()


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
            // close; in add mode the unsaved draft is kept for next time
            IconButton(onDone) { Icon(Icons.Default.Close, contentDescription = "Close") }
            Text(
                if (s.isEditing) "Edit expense" else "Add expense",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f)
            )
            // add mode only: empty the vm.onDateChanged and delete the draft
            if (!s.isEditing) TextButton(vm::onClearClicked) { Text("Clear") }
        }
        if (s.restoredDraft) {
            Text("Restored your unsaved draft", style = MaterialTheme.typography.bodySmall)
        }

        LimitedTextField(
            value = s.description,
            onValueChange = vm::onDescriptionChanged,
            maxLength = InputLimits.EXPENSE_DESC,
            label = "Description",
            error = s.descriptionError,
            modifier = Modifier.fillMaxWidth()
        )

        LimitedTextField(
            value = s.amountText,
            onValueChange = vm::onAmountChanged,
            maxLength = InputLimits.AMOUNT,
            label = "Amount ($)",
            showCounter = false,
            error = s.amountError,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )

        Text("Category")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExpenseCategory.entries.forEach { c ->
                FilterChip(s.category == c, { vm.onCategorySelected(c) }, label = { Text(c.name) })
            }
        }

        Text("Paid by")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            s.members.forEach { m ->
                // "You" for your own chip, so it's clear who paid
                FilterChip(s.paidByUid == m.uid, { vm.onPaidByChanged(m.uid) },
                    label = { Text(if (m.uid == myUid) "You" else m.name) })
            }
        }

        //choosing a date for the expense
        if (showPicker) {
            val pickerState = rememberDatePickerState(
                initialSelectedDateMillis = s.dateEpochDay * 86_400_000L,
                selectableDates = object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                        utcTimeMillis / 86_400_000L <= today
                }
            )
            DatePickerDialog(
                onDismissRequest = { showPicker = false },
                confirmButton = {
                    TextButton({
                        pickerState.selectedDateMillis?.let { vm.onDateChanged(it / 86_400_000L) }
                        showPicker = false
                    }) { Text("OK") }
                },
                dismissButton = { TextButton({ showPicker = false }) { Text("Cancel") } }
            ) { DatePicker(state = pickerState) }
        }

        Text("Date")
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceCard("Choose date", s.dateEpochDay < today - 1, { showPicker = true }, Modifier.weight(1f).fillMaxHeight())
        }

        Text(
            formatFullEpochDay(s.dateEpochDay),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

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
                LimitedTextField(
                    value = s.customAmountTexts[m.uid] ?: "",
                    onValueChange = { vm.onCustomAmountChanged(m.uid, it) },
                    maxLength = InputLimits.AMOUNT,
                    label = m.name,
                    showCounter = false,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        s.remainingCents?.let { Text("Left to assign: ${it.money()}") }
        s.splitError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        // edit mode: someone in this expense left the household, so it can't be saved
        s.lockedMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        s.saveError?.takeIf { it != s.lockedMessage }?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Button(
            vm::onSaveClicked,
            Modifier.fillMaxWidth(),
            enabled = s.saveStatus != SaveStatus.SAVING && s.lockedMessage == null
        ) {
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
