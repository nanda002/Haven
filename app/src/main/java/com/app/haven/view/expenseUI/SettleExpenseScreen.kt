package com.app.haven.view.expenseUI

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
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
import com.app.haven.actions.ExpenseRepository
import com.app.haven.actions.HouseholdActions
import com.app.haven.actions.SettleExpenseRepository
import com.app.haven.viewmodel.expense.MemberOption
import com.app.haven.viewmodel.expense.SettleExpenseViewModel
import com.app.haven.viewmodel.expense.SettleUiState
import kotlinx.coroutines.flow.map
import kotlin.math.abs

// Basic settle-up screen: the user's balance with each member, with a Settle button per member
@Composable
fun SettleExpenseScreen(
    householdId: String,
    myUid: String,
    onClose: () -> Unit,
    vm: SettleExpenseViewModel = viewModel {
        SettleExpenseViewModel(
            ExpenseRepository(), SettleExpenseRepository(), householdId, myUid,
            HouseholdActions().observeMembers(householdId)
                .map { list -> list.map { MemberOption(it.uid, it.displayName) } }
        )
    }
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val dialog by vm.dialog.collectAsStateWithLifecycle()

    // settle popup, shown while the user is settling with someone
    dialog?.let { d ->
        val theyOweMe = d.member.netCents > 0
        AlertDialog(
            onDismissRequest = { if (!d.saving) vm.onSettleDismissed() },
            title = { Text("Settle with ${d.member.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (theyOweMe) "${d.member.name} owes you ${d.member.netCents.money()}. How much did they pay?"
                        else "You owe ${d.member.name} ${abs(d.member.netCents).money()}. How much did you pay?"
                    )
                    OutlinedTextField(d.amountText, vm::onSettleAmountChanged, label = { Text("Amount ($)") },
                        singleLine = true, enabled = !d.saving,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth())
                    d.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(vm::onSettleConfirmed, enabled = !d.saving) {
                    Text(if (d.saving) "Saving…" else "Settle")
                }
            },
            dismissButton = {
                TextButton(vm::onSettleDismissed, enabled = !d.saving) { Text("Cancel") }
            }
        )
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClose) { Icon(Icons.Default.Close, contentDescription = "Close") }
            Text("Balances", style = MaterialTheme.typography.headlineSmall)
        }

        Box(Modifier.fillMaxSize()) {
            when (val s = state) {
                SettleUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                SettleUiState.AllSettled -> Text("You're all settled up", Modifier.align(Alignment.Center))
                is SettleUiState.Error -> Text(s.message, Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.error)
                is SettleUiState.Success -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(s.people, key = { it.uid }) { m ->
                        ListItem(
                            headlineContent = { Text(m.name) },
                            supportingContent = {
                                if (m.netCents > 0) Text("Owes you ${m.netCents.money()}")
                                else Text("You owe ${abs(m.netCents).money()}", color = MaterialTheme.colorScheme.error)
                            },
                            trailingContent = { Button({ vm.onSettleClicked(m) }) { Text("Settle") } }
                        )
                    }
                }
            }
        }
    }
}
