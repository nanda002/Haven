package com.app.haven.view.choreUI

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.haven.domain.chore.ChoreTransferRequest
import com.app.haven.domain.chore.formatEpochDay
import com.app.haven.viewmodel.chore.TransferNoticesViewModel

// Shown at the top of the app while there is something to answer. Empty (and takes no space) otherwise.
@Composable
fun TransferNotices(
    householdId: String,
    myUid: String,
    onChooseAnother: () -> Unit,
    vm: TransferNoticesViewModel = viewModel(
        key = "transfer-notices-$householdId-$myUid",
        factory = TransferNoticesViewModel.factory(householdId, myUid)
    )
) {
    val incoming by vm.incoming.collectAsStateWithLifecycle()
    val declined by vm.declined.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    if (incoming.isEmpty() && declined.isEmpty() && error == null) return

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        incoming.forEach { request ->
            IncomingCard(request, onAccept = { vm.accept(request) }, onDecline = { vm.decline(request) })
        }
        declined.forEach { request ->
            DeclinedCard(request, onChooseAnother = onChooseAnother, onDismiss = { vm.dismiss(request) })
        }
        error?.let { message ->
            Text(
                message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.clickable { vm.onErrorShown() }
            )
        }
    }
}

// A roommate wants to hand over a chore: which one, and who is handing it over
@Composable
private fun IncomingCard(request: ChoreTransferRequest, onAccept: () -> Unit, onDecline: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${request.fromName} would like to hand you a chore", style = MaterialTheme.typography.labelLarge)
            Text(request.choreTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Due ${formatEpochDay(request.dueEpochDay)}. It stays with ${request.fromName} until you accept.",
                style = MaterialTheme.typography.bodySmall
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onDecline) { Text("Decline") }
                Button(onAccept) { Text("Accept") }
            }
        }
    }
}

// My offer was declined, so the chore is still mine
@Composable
private fun DeclinedCard(request: ChoreTransferRequest, onChooseAnother: () -> Unit, onDismiss: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${request.toName} declined", style = MaterialTheme.typography.labelLarge)
            Text(request.choreTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "It is still yours. Pick another roommate, or finish it yourself.",
                style = MaterialTheme.typography.bodySmall
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onDismiss) { Text("Dismiss") }
                Button(onChooseAnother) { Text("Pick another") }
            }
        }
    }
}
