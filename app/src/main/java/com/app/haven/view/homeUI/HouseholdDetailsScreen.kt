package com.app.haven.view.homeUI

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.haven.domain.household.canLeaveHousehold
import com.app.haven.view.common.PageHeader
import com.app.haven.view.expenseUI.money
import com.app.haven.viewmodel.home.HomeBalanceState
import com.app.haven.viewmodel.home.HomeBalanceViewModel
import com.app.haven.viewmodel.home.HomeViewModel
import com.app.haven.viewmodel.home.HouseholdMembersViewModel

// Household page: name, invite code, members (current + past), leave household
@Composable
fun HouseholdDetailsScreen(
    myUid: String?,
    onLeftHousehold: () -> Unit,
    onBack: () -> Unit,
    vm: HomeViewModel = viewModel()
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val regenerating by vm.regenerating.collectAsStateWithLifecycle()
    val inviteError by vm.inviteError.collectAsStateWithLifecycle()
    val leaving by vm.leaving.collectAsStateWithLifecycle()
    val leaveError by vm.leaveError.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.load() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PageHeader("Household details", onBack)

        when {
            ui.loading -> CircularProgressIndicator()
            ui.error != null -> {
                Text(ui.error!!, color = MaterialTheme.colorScheme.error)
                Button({ vm.load() }) { Text("Retry") }
            }
            else -> ui.household?.let { h ->
                Text(h.name, style = MaterialTheme.typography.titleLarge)
                InviteCodeRow(h.inviteCode, regenerating, onRegenerate = vm::regenerateInviteCode)
                inviteError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

                HorizontalDivider()
                MembersSection(h.id, myUid)

                if (myUid != null) {
                    HorizontalDivider()
                    LeaveSection(
                        householdId = h.id,
                        myUid = myUid,
                        leaving = leaving,
                        onConfirmLeave = { vm.leaveHousehold(onLeftHousehold) }
                    )
                }
                leaveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

// ---------- Invite code: code + regenerate (with confirm) + copy ----------

@Composable
private fun InviteCodeRow(
    code: String,
    regenerating: Boolean,
    onRegenerate: () -> Unit
) {
    val context = LocalContext.current
    var confirming by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Invite code: $code", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(4.dp))

        IconButton(onClick = { confirming = true }, enabled = !regenerating) {
            Icon(Icons.Default.Refresh, contentDescription = "Generate new invite code")
        }
        IconButton(onClick = { copyToClipboard(context, code) }) {
            Icon(Icons.Default.ContentCopy, contentDescription = "Copy invite code")
        }
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Generate a new invite code?") },
            text = {
                Text("The current code will stop working right away. Roommates who haven't joined yet will need the new one. Current members aren't affected.")
            },
            confirmButton = {
                TextButton({ confirming = false; onRegenerate() }) { Text("Generate") }
            },
            dismissButton = { TextButton({ confirming = false }) { Text("Cancel") } }
        )
    }
}

private fun copyToClipboard(context: Context, code: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Invite code", code))
    // Android 13+ shows its own "copied" popup; older versions need ours
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, "Invite code copied", Toast.LENGTH_SHORT).show()
    }
}

// ---------- Members: current list, past members faded and collapsed ----------

@Composable
private fun MembersSection(
    householdId: String,
    myUid: String?,
    vm: HouseholdMembersViewModel = viewModel(factory = HouseholdMembersViewModel.factory(householdId))
) {
    val members by vm.members.collectAsStateWithLifecycle()
    val past by vm.pastMembers.collectAsStateWithLifecycle()
    var showPast by rememberSaveable { mutableStateOf(false) }
    val faded = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Members (${members.size})", style = MaterialTheme.typography.titleMedium)
        members.forEach { m ->
            val suffix = if (m.uid == myUid) " (you)" else ""
            Text(m.displayName.ifBlank { "Unnamed" } + suffix, style = MaterialTheme.typography.bodyLarge)
        }

        if (past.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { showPast = !showPast }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Past members (${past.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = faded,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    if (showPast) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (showPast) "Hide past members" else "Show past members",
                    tint = faded
                )
            }
            AnimatedVisibility(showPast) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    past.forEach { m ->
                        Text(
                            m.displayName.ifBlank { "Unnamed" },
                            style = MaterialTheme.typography.bodyLarge,
                            color = faded
                        )
                    }
                }
            }
        }
    }
}

// ---------- Leave household: only enabled once everything is settled ----------

@Composable
private fun LeaveSection(
    householdId: String,
    myUid: String,
    leaving: Boolean,
    onConfirmLeave: () -> Unit,
    balanceVm: HomeBalanceViewModel = viewModel(factory = HomeBalanceViewModel.factory(householdId, myUid))
) {
    val state by balanceVm.state.collectAsStateWithLifecycle()
    var confirming by remember { mutableStateOf(false) }

    val balance = (state as? HomeBalanceState.Success)?.balance
    val canLeave = balance != null && canLeaveHousehold(balance)

    OutlinedButton(
        onClick = { confirming = true },
        enabled = canLeave && !leaving,
        modifier = Modifier.fillMaxWidth()
    ) { Text(if (leaving) "Leaving..." else "Leave household") }

    if (balance != null && !canLeave) {
        Text(
            "Settle up before leaving (you're owed ${balance.owedToMeCents.money()}, you owe ${balance.iOweCents.money()})",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Leave household?") },
            text = { Text("You'll lose access to its expenses. You can rejoin later with the invite code.") },
            confirmButton = {
                TextButton({ confirming = false; onConfirmLeave() }) {
                    Text("Leave", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton({ confirming = false }) { Text("Cancel") } }
        )
    }
}