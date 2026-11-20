package com.app.haven.view.choreUI

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.haven.domain.chore.Chore
import com.app.haven.domain.chore.ChoreCategory
import com.app.haven.domain.chore.ChoreScope
import com.app.haven.domain.chore.ChoreStatus
import com.app.haven.domain.chore.dueLabel
import com.app.haven.domain.chore.formatFullEpochDay
import com.app.haven.domain.chore.lastDoneLabel
import com.app.haven.domain.chore.originalAssignee
import com.app.haven.domain.chore.repeatLabel
import com.app.haven.domain.chore.status
import com.app.haven.domain.chore.upNextLabel
import com.app.haven.viewmodel.chore.ChoresUiState
import com.app.haven.viewmodel.chore.ChoresViewModel

private enum class ChoreTab(val label: String) { MINE("Mine"), EVERYONE("Everyone") }

// Where a chore sits in the list. It follows the chore's current occurrence, so an overdue chore stays
// visible under Overdue and a repeating chore appears under the date of its next turn.
private enum class ChoreSection(val title: String) {
    OVERDUE("Overdue"),
    TODAY("Today"),
    TOMORROW("Tomorrow"),
    UPCOMING("Upcoming"),
    COMPLETED("Completed")
}

private fun sectionOf(chore: Chore, today: Long): ChoreSection = when {
    chore.done -> ChoreSection.COMPLETED
    chore.dueEpochDay < today -> ChoreSection.OVERDUE
    chore.dueEpochDay == today -> ChoreSection.TODAY
    chore.dueEpochDay == today + 1 -> ChoreSection.TOMORROW
    else -> ChoreSection.UPCOMING
}

@Composable
fun ChoresScreen(
    householdId: String,
    myUid: String,
    onAddChore: () -> Unit,
    onEditChore: (String) -> Unit,
    vm: ChoresViewModel = viewModel(factory = ChoresViewModel.factory(householdId, myUid))
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val memberUids by vm.memberUids.collectAsStateWithLifecycle()
    val undo by vm.undo.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val undoSnackbar = remember { SnackbarHostState() }
    var pendingDelete by remember { mutableStateOf<Chore?>(null) }

    // The selected tab lives here, not in the list, so list updates never switch it back.
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    val tab = ChoreTab.entries[tabIndex]
    val today = rememberTodayEpochDay()

    LaunchedEffect(error) {
        error?.let { snackbar.showSnackbar(it); vm.onErrorShown() }
    }

    // after "mark done": shown until the ViewModel's 10 second window ends (it clears undo then)
    LaunchedEffect(undo) {
        undo?.let { previous ->
            val result = undoSnackbar.showSnackbar(
                message = "\"${previous.title}\" marked done",
                actionLabel = "Undo",
                duration = SnackbarDuration.Indefinite
            )
            if (result == SnackbarResult.ActionPerformed) vm.onUndo()
        }
    }

    pendingDelete?.let { c ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete chore?") },
            text = { Text("\"${c.title}\" will be removed for everyone in the household.") },
            confirmButton = {
                TextButton({ vm.onDelete(c); pendingDelete = null }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton({ pendingDelete = null }) { Text("Cancel") } }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onSurface,
        snackbarHost = {
            Column {
                SnackbarHost(undoSnackbar)
                SnackbarHost(snackbar)
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onAddChore,
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) { Icon(Icons.Default.Add, contentDescription = "Add chore") }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(
                "Chores",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 16.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                ChoreTabs(selected = tab, onSelect = { tabIndex = it.ordinal })
            }
            Spacer(Modifier.height(20.dp))

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (val s = state) {
                    ChoresUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    is ChoresUiState.Error -> Text(
                        s.message, Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.error
                    )
                    else -> {
                        val all = (s as? ChoresUiState.Success)?.chores.orEmpty()
                        // Mine: everything assigned to me, personal chores included.
                        // Everyone: shared household chores only. Other people's personal chores never reach this phone.
                        val visible = when (tab) {
                            ChoreTab.MINE -> all.filter { it.assigneeUid == myUid }
                            ChoreTab.EVERYONE -> all.filter { it.scope == ChoreScope.HOUSEHOLD }
                        }
                        if (visible.isEmpty()) {
                            Text(
                                if (tab == ChoreTab.MINE) "You don't have any chores yet." else "No household chores yet.",
                                Modifier.align(Alignment.Center),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            ChoreList(
                                chores = visible,
                                today = today,
                                showAssignee = tab == ChoreTab.EVERYONE,
                                memberUids = memberUids,
                                onEdit = { onEditChore(it.id) },
                                onToggle = vm::onToggleDone,
                                onDelete = { pendingDelete = it }
                            )
                        }
                    }
                }
            }
        }
    }
}

// Small rounded pill with two options. The selected one gets a soft filled highlight.
@Composable
private fun ChoreTabs(selected: ChoreTab, onSelect: (ChoreTab) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .padding(horizontal = 24.dp)
            .clip(RoundedCornerShape(50))
            .background(scheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ChoreTab.entries.forEach { option ->
            val isSelected = option == selected
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (isSelected) scheme.primaryContainer else Color.Transparent)
                    .selectable(selected = isSelected, onClick = { onSelect(option) }, role = Role.Tab)
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    option.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) scheme.onPrimaryContainer else scheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ChoreList(
    chores: List<Chore>,
    today: Long,
    showAssignee: Boolean,
    memberUids: Set<String>,
    onEdit: (Chore) -> Unit,
    onToggle: (Chore) -> Unit,
    onDelete: (Chore) -> Unit
) {
    val grouped = chores.groupBy { sectionOf(it, today) }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        ChoreSection.entries.forEach { section ->
            val inSection = grouped[section].orEmpty().sortedWith(
                if (section == ChoreSection.COMPLETED) compareByDescending<Chore> { it.lastCompletedEpochDay }
                else compareBy<Chore> { it.dueEpochDay }.thenBy { it.title.lowercase() }
            )
            if (inSection.isNotEmpty()) {
                item(key = section.name) {
                    Column {
                        Text(
                            section.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
                        )
                        // one white rounded container per section, rows separated by thin dividers
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                inSection.forEachIndexed { index, chore ->
                                    if (index > 0) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(horizontal = 16.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                        )
                                    }
                                    ChoreItem(
                                        chore = chore,
                                        today = today,
                                        showAssignee = showAssignee,
                                        // assignee left the household -> needs someone new (edit to reassign)
                                        orphaned = chore.scope == ChoreScope.HOUSEHOLD && !chore.done &&
                                            memberUids.isNotEmpty() && chore.assigneeUid !in memberUids,
                                        onEdit = { onEdit(chore) },
                                        onToggle = { onToggle(chore) },
                                        onDelete = { onDelete(chore) }
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

// One row: circle, title with its category underneath. Tapping the row opens the details underneath it.
@Composable
private fun ChoreItem(
    chore: Chore,
    today: Long,
    showAssignee: Boolean,
    orphaned: Boolean,
    onEdit: () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by rememberSaveable(chore.id) { mutableStateOf(false) }
    val category = ChoreCategory.fromKey(chore.category)
    val status = chore.status(today)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    // only dates that need to be read: how late it is, or which day it falls on
    val trailing = when (status) {
        ChoreStatus.OVERDUE, ChoreStatus.UPCOMING -> chore.dueLabel(today).removePrefix("Due ")
        else -> null
    }
    val secondary = listOfNotNull(
        if (orphaned) "Needs a new assignee" else category.label,
        chore.assigneeName.takeIf { showAssignee && !orphaned && chore.scope == ChoreScope.HOUSEHOLD }
    ).joinToString(" · ")

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompletionCircle(done = chore.done, onClick = onToggle)
            Spacer(Modifier.width(4.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    chore.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    secondary,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (orphaned) amberTextColor() else muted
                )
            }
            if (trailing != null) {
                Text(
                    trailing,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (status == ChoreStatus.OVERDUE) amberTextColor() else muted
                )
                Spacer(Modifier.width(8.dp))
            }
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "Hide details" else "Show details",
                tint = muted
            )
        }

        if (expanded) {
            ChoreDetails(chore, today, onEdit, onDelete)
        }
    }
}

// Outlined circle while unfinished, filled with a check once done. A finished chore can't be un-ticked
// here: Undo right after completing is the way back.
@Composable
private fun CompletionCircle(done: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .then(if (done) Modifier else Modifier.clickable(role = Role.Checkbox, onClick = onClick)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .then(
                    if (done) Modifier.background(scheme.primary)
                    else Modifier.border(2.dp, scheme.outline, CircleShape)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (done) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Completed",
                    tint = scheme.onPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// Everything else about the chore, kept out of the collapsed row
@Composable
private fun ChoreDetails(chore: Chore, today: Long, onEdit: () -> Unit, onDelete: () -> Unit) {
    val personal = chore.scope == ChoreScope.PERSONAL
    val original = chore.originalAssignee()

    Column(
        Modifier.fillMaxWidth().padding(start = 60.dp, end = 16.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        DetailLine("Assigned to", if (personal) "You (personal chore)" else chore.assigneeName)
        if (!personal && original.uid != chore.assigneeUid) {
            DetailLine("Originally", original.name)
        }
        DetailLine("Due", formatFullEpochDay(chore.dueEpochDay))
        DetailLine("Repeats", repeatLabel(chore.repeatDays)?.removePrefix("Repeats ") ?: "Does not repeat")
        if (!personal && chore.repeatDays > 0) {
            DetailLine("Rotation", if (chore.rotate) "Take turns" else "Same person")
        }
        if (chore.notes.isNotBlank()) {
            DetailLine("Notes", chore.notes)
        }
        chore.lastDoneLabel(today)?.let { DetailLine("Completion", it) }
        chore.upNextLabel()?.let { DetailLine("Next", it.removePrefix("Next: ")) }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDelete) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
            TextButton(onClick = onEdit) { Text("Edit") }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(92.dp)
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}
