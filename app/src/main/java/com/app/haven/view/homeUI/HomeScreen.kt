package com.app.haven.view.homeUI

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.haven.viewmodel.home.HomeViewModel

// Temporary home: household + invite code, plus the balance card.
// Becomes the full dashboard (balances + chores due) later.
// householdId/myUid are null until the user has loaded -> card is hidden meanwhile
@Composable
fun HomeScreen(
    householdId: String?,
    myUid: String?,
    onOpenLedger: () -> Unit,
    onLogout: () -> Unit,
    vm: HomeViewModel = viewModel()
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.load() }

    // loading: spinner in the middle of the screen, like the other screens
    if (ui.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            ui.error != null -> {
                Text(ui.error!!, color = MaterialTheme.colorScheme.error)
                Button({ vm.load() }) { Text("Retry") }
            }
            else -> ui.household?.let { h ->
                Text(h.name, style = MaterialTheme.typography.headlineMedium)
                Text("Invite code: ${h.inviteCode}", style = MaterialTheme.typography.titleMedium)
                Text("${h.memberIds.size} member(s)")
                if (householdId != null && myUid != null) {
                    HomeBalanceCard(householdId, myUid, onClick = onOpenLedger)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        OutlinedButton(onLogout) { Text("Log out") }
    }
}