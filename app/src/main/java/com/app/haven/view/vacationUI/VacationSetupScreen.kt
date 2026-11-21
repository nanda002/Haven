package com.app.haven.view.vacationUI

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.app.haven.domain.chore.Chore
import com.app.haven.domain.chore.ChoreTransferRequest
import com.app.haven.domain.chore.TransferStatus
import com.app.haven.domain.chore.formatEpochDay
import com.app.haven.domain.chore.offerFor
import com.app.haven.domain.chore.vacationBlockedMessage
import com.app.haven.viewmodel.vacation.VacationSetupViewModel

// Opened when someone switches Vacation Mode on. Every open shared chore has to be finished or accepted
// by a roommate first. Pressing "Hand over my chores" offers each chore to a roommate automatically;
// a decline moves the chore to the next roommate.
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
    val offers by vm.offers.collectAsStateWithLifecycle()
    val roommates by vm.roommates.collectAsStateWithLifecycle()
    val exhausted by vm.exhausted.collectAsStateWithLifecycle()
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
                                    "A hand-over only counts once a roommate accepts it. If one declines, " +
                                    "the chore is offered to the next roommate.",
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
                        items(list, key = { it.id }) { chore ->
                            PendingChoreCard(
                                chore = chore,
                                offer = offerFor(offers, chore),
                                noRoommates = roommates.isEmpty(),
                                allDeclined = chore.id in exhausted
                            )
                        }
                    }
                }

                // chores that have never been offered to anyone yet
                val handOverCount = if (roommates.isEmpty()) 0 else list.count { offerFor(offers, it) == null }
                BottomBar(
                    pendingCount = list.size,
                    handOverCount = handOverCount,
                    busy = busy,
                    onHandOver = vm::sendRequests,
                    onStart = { vm.startVacation(onActivated) },
                    onOpenChores = onOpenChores
                )
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

// One open chore and where its hand-over stands right now.
@Composable
private fun PendingChoreCard(
    chore: Chore,
    offer: ChoreTransferRequest?,
    noRoommates: Boolean,
    allDeclined: Boolean
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(chore.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Due ${formatEpochDay(chore.dueEpochDay)} · Assigned to ${chore.assigneeName}",
                style = MaterialTheme.typography.bodySmall
            )
            when {
                offer?.status == TransferStatus.PENDING -> Text(
                    "Waiting for ${offer.toName} to accept. It stays with you until they do.",
                    style = MaterialTheme.typography.bodySmall
                )
                noRoommates -> Text(
                    "No roommate is available to take it. Finish it yourself in Chores.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                allDeclined -> Text(
                    "Every roommate declined. It is still yours: finish it yourself in Chores.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                offer?.status == TransferStatus.DECLINED -> Text(
                    "${offer.toName} declined. Offering it to the next roommate.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
                else -> Text(
                    "Not handed over yet.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun BottomBar(
    pendingCount: Int,
    handOverCount: Int,
    busy: Boolean,
    onHandOver: () -> Unit,
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
            if (handOverCount > 0) {
                Button(onHandOver, Modifier.fillMaxWidth(), enabled = !busy) {
                    Text(if (handOverCount == 1) "Hand over 1 chore" else "Hand over $handOverCount chores")
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