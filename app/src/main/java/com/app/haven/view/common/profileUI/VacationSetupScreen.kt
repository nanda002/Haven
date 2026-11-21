package com.app.haven.view.common.profileUI

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.haven.domain.chore.TransferStatus
import com.app.haven.domain.chore.formatEpochDay
import com.app.haven.domain.chore.offerFor
import com.app.haven.view.choreUI.amberTextColor
import com.app.haven.viewmodel.chore.ChoresViewModel
import com.app.haven.viewmodel.common.profile.VacationSetupViewModel

// Opened when Vacation Mode is switched on. Lists the person's unfinished household chores (finished and personal
// chores are never in it): name, due date and who it is assigned to. Each one can be completed, or several can be
// picked (one by one or with Select all) and offered to one roommate. A chore stays theirs until the roommate
// accepts, and Start vacation stays off until the list is empty.
// Completing a chore uses the Chores screen's own ViewModel, and the roommate answers on their Accept/Decline cards.
@Composable
fun VacationSetupScreen(
    householdId: String,
    myUid: String,
    onClose: () -> Unit,
    onStarted: () -> Unit,
    vm: VacationSetupViewModel = viewModel(
        key = "vacation-setup-$householdId-$myUid",
        factory = VacationSetupViewModel.factory(householdId, myUid)
    ),
    choresVm: ChoresViewModel = viewModel(
        key = "vacation-setup-chores-$householdId-$myUid",
        factory = ChoresViewModel.factory(householdId, myUid)
    )
) {
    val pending by vm.pending.collectAsStateWithLifecycle()
    val recipients by vm.recipients.collectAsStateWithLifecycle()
    val offers by vm.offers.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val choresError by choresVm.error.collectAsStateWithLifecycle()

    var selected by remember { mutableStateOf(setOf<String>()) }
    var recipientUid by remember { mutableStateOf<String?>(null) }

    val list = pending.orEmpty()
    // a chore whose offer is still waiting can't be picked again. A declined one can, which is how it is offered on.
    val selectable = list.filter { offerFor(offers, it)?.status != TransferStatus.PENDING }
    val chosen = selectable.filter { it.id in selected }
    val allSelected = selectable.isNotEmpty() && chosen.size == selectable.size
    val recipient = recipients.firstOrNull { it.uid == recipientUid }
    val waiting = list.size - selectable.size

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClose) { Icon(Icons.Default.Close, contentDescription = "Close") }
                    Text("Before you go", style = MaterialTheme.typography.headlineSmall)
                }
                Text(
                    "Finish each chore, or ask a roommate to take it. A chore stays yours until they accept. " +
                        "Vacation can start once none are left.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))

                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when {
                        pending == null -> CircularProgressIndicator(Modifier.padding(vertical = 16.dp))
                        list.isEmpty() -> Text(
                            "All set. You have no pending chores.",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                        else -> {
                            if (selectable.isNotEmpty()) {
                                Row(
                                    Modifier.fillMaxWidth().clickable {
                                        selected = if (allSelected) emptySet() else selectable.map { it.id }.toSet()
                                    },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(checked = allSelected, onCheckedChange = null)
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        if (allSelected) "Deselect all" else "Select all",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                            list.forEach { chore ->
                                val offer = offerFor(offers, chore)
                                val open = offer?.status == TransferStatus.PENDING
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = chosen.any { it.id == chore.id },
                                        onCheckedChange = { on ->
                                            selected = if (on) selected + chore.id else selected - chore.id
                                        },
                                        enabled = !open
                                    )
                                    Column(Modifier.weight(1f)) {
                                        Text(chore.title, style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            "Due ${formatEpochDay(chore.dueEpochDay)} · Assigned to ${chore.assigneeName}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        offer?.let {
                                            Text(
                                                if (open) "Waiting for ${it.toName} to answer"
                                                else "${it.toName} declined. Pick someone else, or complete it yourself",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (open) MaterialTheme.colorScheme.onSurfaceVariant
                                                else amberTextColor()
                                            )
                                        }
                                    }
                                    TextButton(onClick = { choresVm.onToggleDone(chore) }) { Text("Mark done") }
                                }
                            }

                            Spacer(Modifier.height(8.dp))
                            Text("Ask who?", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            if (recipients.isEmpty()) {
                                Text(
                                    "No other roommate is available right now.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            recipients.forEach { member ->
                                Row(
                                    Modifier.fillMaxWidth().clickable { recipientUid = member.uid },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(selected = member.uid == recipientUid, onClick = null)
                                    Spacer(Modifier.width(12.dp))
                                    Text(member.displayName, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    }
                }

                (error ?: choresError)?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (waiting > 0) {
                    Text(
                        if (waiting == 1) "Waiting for an answer on 1 chore" else "Waiting for answers on $waiting chores",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (list.isNotEmpty()) {
                    OutlinedButton(
                        onClick = {
                            recipient?.let { vm.askToTake(chosen, it) }
                            selected = emptySet()
                        },
                        enabled = recipient != null && chosen.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (recipient == null || chosen.isEmpty()) "Pick chores and a roommate"
                            else "Ask ${recipient.displayName} to take ${chosen.size}"
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { vm.startVacation(onStarted) },
                    enabled = pending != null && list.isEmpty() && !busy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Start vacation") }
            }
        }
    }
}
