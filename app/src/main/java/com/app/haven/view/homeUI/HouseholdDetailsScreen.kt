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
import com.app.haven.view.common.NotificationsCard
import com.app.haven.domain.household.canLeaveHousehold
import com.app.haven.view.common.PageHeader
import com.app.haven.view.expenseUI.money
import com.app.haven.viewmodel.home.HomeBalanceState
import com.app.haven.viewmodel.home.HomeBalanceViewModel
import com.app.haven.viewmodel.home.HomeViewModel
import com.app.haven.viewmodel.home.HouseholdMembersViewModel
import androidx.compose.ui.draw.alpha
import com.app.haven.view.common.Avatar
import com.app.haven.view.common.CameraCard
import com.app.haven.view.common.QrCodeImage
import com.app.haven.view.common.joinQrPayload
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

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
                HouseholdCard(h.name, h.inviteCode, regenerating, onRegenerate = vm::regenerateInviteCode)
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
private fun HouseholdCard(
    name: String,
    code: String,
    regenerating: Boolean,
    onRegenerate: () -> Unit
) {
    val context = LocalContext.current
    var confirming by remember { mutableStateOf(false) }

    // fixed light colors so the card looks the same in light and dark mode
    val labelColor = Color(0xFF6B7280)
    val textColor = Color(0xFF1F2937)

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFFF1F7FF), Color(0xFFD9E8FB))))
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("HOUSEHOLD NAME", fontSize = 12.sp, letterSpacing = 1.sp, color = labelColor)
        Spacer(Modifier.height(4.dp))
        Text(
            name,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(24.dp))
        Text("QR CODE", fontSize = 12.sp, letterSpacing = 1.sp, color = labelColor)
        Spacer(Modifier.height(10.dp))
        // Redraws by itself whenever the code changes, so regenerating updates it too
        Box(
            Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White)
                .border(1.dp, Color(0xFFC5D5EA), RoundedCornerShape(20.dp))
                .padding(12.dp)
        ) {
            QrCodeImage(joinQrPayload(code), Modifier.size(200.dp))
        }

        Spacer(Modifier.height(20.dp))
        Text("JOIN CODE", fontSize = 12.sp, letterSpacing = 1.sp, color = labelColor)
        Spacer(Modifier.height(4.dp))
        Text(
            code,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 4.sp,
            color = textColor
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { copyToClipboard(context, code) }) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Copy")
            }
            TextButton(onClick = { confirming = true }, enabled = !regenerating) {
                Icon(Icons.Default.Refresh, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (regenerating) "Generating..." else "New code")
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "Roommates can join using this QR code",
            fontSize = 12.sp,
            color = labelColor,
            textAlign = TextAlign.Center
        )
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Generate a new invite code?") },
            text = {
                Text("The current code and QR code will stop working right away. Roommates who haven't joined yet will need the new ones. Current members aren't affected.")
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Avatar(m.displayName, m.photoUrl)
                Spacer(Modifier.width(12.dp))
                Text(m.displayName.ifBlank { "Unnamed" } + suffix, style = MaterialTheme.typography.bodyLarge)
            }
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Avatar(m.displayName, m.photoUrl, modifier = Modifier.alpha(0.55f))
                            Spacer(Modifier.width(12.dp))
                            Text(m.displayName.ifBlank { "Unnamed" }, style = MaterialTheme.typography.bodyLarge, color = faded)
                        }
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

    Button(
        onClick = { confirming = true },
        enabled = canLeave && !leaving,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFFDE8E8),
            contentColor = Color(0xFFB3261E),
            disabledContainerColor = Color(0xFFF5EDED),
            disabledContentColor = Color(0xFFB3261E).copy(alpha = 0.4f)
        ),
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