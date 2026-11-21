package com.app.haven.view.vacationUI

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.haven.data.model.Member
import com.app.haven.domain.chore.Chore
import com.app.haven.domain.chore.ChoreTransferRequest
import com.app.haven.domain.chore.TransferStatus
import com.app.haven.domain.chore.formatEpochDay
import com.app.haven.domain.chore.offerFor
import com.app.haven.domain.chore.vacationBlockedMessage
import com.app.haven.viewmodel.vacation.VacationSetupViewModel

// Opened when someone switches Vacation Mode on. Every open shared chore has to be finished or handed over first.
@Composable
fun VacationSetupScreen(
    householdId: String,
    myUid: String,
    onClose: () -> Unit,
    onOpenChores: () -> Unit,
    onActivated: () -> Unit,
    vm: VacationSetupViewModel = viewModel(factory = VacationSetupViewModel.factory(householdId, myUid))
) {
    val pending by vm.pending.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val recipients by vm.recipient.collectAsStateWithLifecycle()
    val offers by vm.offers.collectAsStateWithLifecycle()
    val roommates by vm.roommates.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(error) {
        error?.let {
            snackbar.showSnackbar(it)
            vm.onErrorShown()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClose) { Icon(Icons.Default.Close, contentDescription = "Close") }
                Text("Vacation Mode", style = MaterialTheme.typography.headlineSmall)
            }

            val list = pending
            if (list == null) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            "Before you go, every open household chore has to be finished or handed over. " +
                                    "A hand-over only counts once the roommate accepts it.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    if (list.isEmpty()) {
                        item {
                            Text(
                                "You have no open household chores. You are ready to go.",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    } else {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = list.all { it.id in selected },
                                    onCheckedChange = { vm.setAll(list.map { c -> c.id }, it) }
                                )
                                Text("Select all", style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                        items(list, key = { it.id }) { chore ->
                            PendingChoreCard(
                                chore = chore,
                                selected = chore.id in selected,
                                offer = offerFor(offers, chore),
                                roommates = roommates,
                                chosenUid = recipients[chore.id],
                                onToggle = { vm.toggle(chore.id) },
                                onPick = { uid -> vm.pickRecipient(chore.id, uid) }
                            )
                        }
                    }
                }

                val sendCount = list.count {
                    it.id in selected && recipients[it.id] != null &&
                            offerFor(offers, it)?.status != TransferStatus.PENDING
                }
                BottomBar(
                    pendingCount = list.size,
                    sendCount = sendCount,
                    busy = busy,
                    onSend = vm::sendRequests,
                    onStart = { vm.startVacation(onActivated) },
                    onOpenChores = onOpenChores
                )
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun PendingChoreCard(
    chore: Chore,
    selected: Boolean,
    offer: ChoreTransferRequest?,
    roommates: List<Member>,
    chosenUid: String?,
    onToggle: () -> Unit,
    onPick: (String) -> Unit
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = selected, onCheckedChange = { onToggle() })
                Column(Modifier.weight(1f)) {
                    Text(chore.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Due ${formatEpochDay(chore.dueEpochDay)} · Assigned to ${chore.assigneeName}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            when {
                offer != null && offer.status == TransferStatus.PENDING -> Text(
                    "Waiting for ${offer.toName} to accept. It stays with you until they do.",
                    style = MaterialTheme.typography.bodySmall
                )
                else -> {
                    if (offer != null && offer.status == TransferStatus.DECLINED) {
                        Text(
                            "${offer.toName} declined. It is still yours: pick someone else, or finish it yourself.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    if (selected) {
                        if (roommates.isEmpty()) {
                            Text(
                                "No roommate is available to take it.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            Text("Hand it to", style = MaterialTheme.typography.labelMedium)
                            Row(
                                Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                roommates.forEach { m ->
                                    FilterChip(
                                        selected = chosenUid == m.uid,
                                        onClick = { onPick(m.uid) },
                                        label = { Text(m.displayName) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomBar(
    pendingCount: Int,
    sendCount: Int,
    busy: Boolean,
    onSend: () -> Unit,
    onStart: () -> Unit,
    onOpenChores: () -> Unit
) {
    Surface(tonalElevation = 3.dp) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            vacationBlockedMessage(pendingCount)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            if (sendCount > 0) {
                Button(onSend, Modifier.fillMaxWidth(), enabled = !busy) {
                    Text(if (sendCount == 1) "Send 1 request" else "Send $sendCount requests")
                }
            }
            if (pendingCount > 0) {
                TextButton(onOpenChores, Modifier.fillMaxWidth()) { Text("Finish chores myself in Chores") }
            }
            Button(onStart, Modifier.fillMaxWidth(), enabled = pendingCount == 0 && !busy) {
                Text("Start Vacation Mode")
            }
        }
    }
}
