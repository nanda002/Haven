package com.app.haven.view.homeUI

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.haven.data.model.Member
import com.app.haven.view.common.PageHeader
import com.app.haven.viewmodel.home.HomeViewModel
import com.app.haven.viewmodel.home.HouseholdMembersViewModel

// Page opened from the Home menu: household name, invite code, members
@Composable
fun HouseholdDetailsScreen(
    myUid: String?,
    onBack: () -> Unit,
    vm: HomeViewModel = viewModel()
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val regenerating by vm.regenerating.collectAsStateWithLifecycle()
    val inviteError by vm.inviteError.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.load() }

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${h.memberIds.size} member(s)")
                    MembersButton(h.id, myUid)
                }
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

// ---------- Members: icon that opens a popup with current + past members ----------

private val CaretStart = 20.dp
private val CaretWidth = 16.dp
private val CaretHeight = 8.dp

@Composable
private fun MembersButton(
    householdId: String,
    myUid: String?,
    vm: HouseholdMembersViewModel = viewModel(factory = HouseholdMembersViewModel.factory(householdId))
) {
    val members by vm.members.collectAsStateWithLifecycle()
    val past by vm.pastMembers.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Default.Group, contentDescription = "Show members")
        }
        if (open) {
            MembersPopup(members, past, myUid, onDismiss = { open = false })
        }
    }
}

@Composable
private fun MembersPopup(
    members: List<Member>,
    past: List<Member>,
    myUid: String?,
    onDismiss: () -> Unit
) {
    val density = LocalDensity.current
    val caretCenterPx = with(density) { (CaretStart + CaretWidth / 2).roundToPx() }
    val gapPx = with(density) { 2.dp.roundToPx() }

    // places the popup right under the icon, with the pointer centered on it
    val positionProvider = remember(caretCenterPx, gapPx) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset {
                val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
                val x = (anchorBounds.center.x - caretCenterPx).coerceIn(0, maxX)
                return IntOffset(x, anchorBounds.bottom + gapPx)
            }
        }
    }

    val bubbleColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val faded = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
    var showPast by remember { mutableStateOf(false) }

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Column {
            // pointer attached to the bubble
            Canvas(Modifier.padding(start = CaretStart).size(CaretWidth, CaretHeight)) {
                val path = Path().apply {
                    moveTo(0f, size.height)
                    lineTo(size.width / 2f, 0f)
                    lineTo(size.width, size.height)
                    close()
                }
                drawPath(path, bubbleColor)
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = bubbleColor,
                shadowElevation = 8.dp,
                modifier = Modifier.widthIn(min = 220.dp, max = 280.dp)
            ) {
                Column(
                    Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    Text("Members (${members.size})", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    members.forEach { m ->
                        val suffix = if (m.uid == myUid) " (you)" else ""
                        Text(
                            m.displayName.ifBlank { "Unnamed" } + suffix,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    // past members: faded, collapsed until tapped
                    if (past.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider()
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { showPast = !showPast }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Past members (${past.size})",
                                style = MaterialTheme.typography.labelLarge,
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
                            Column {
                                past.forEach { m ->
                                    Text(
                                        m.displayName.ifBlank { "Unnamed" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = faded,
                                        modifier = Modifier.padding(vertical = 4.dp)
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