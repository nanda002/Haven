package com.app.haven.view.choreUI

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.haven.data.model.Member
import com.app.haven.domain.chore.CATEGORY_REQUIRED_MESSAGE
import com.app.haven.domain.chore.CHORE_NOTES_MAX
import com.app.haven.domain.chore.Chore
import com.app.haven.domain.chore.ChoreDraft
import com.app.haven.domain.chore.END_BEFORE_START_MESSAGE
import com.app.haven.domain.chore.Recurrence
import com.app.haven.domain.chore.formatFullEpochDay
import com.app.haven.domain.chore.resolveEndEpochDay
import com.app.haven.domain.chore.schedulePreview
import com.app.haven.view.common.PageHeader
import com.app.haven.viewmodel.chore.AddChoreViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged

// Add form (choreId = null) and edit form (choreId = an existing chore) share this screen
@Composable
fun AddChoreScreen(
    householdId: String,
    myUid: String,
    onDone: () -> Unit,
    choreId: String? = null,
    vm: AddChoreViewModel = viewModel(factory = AddChoreViewModel.factory(householdId, choreId))
) {
    val loading by vm.loading.collectAsStateWithLifecycle()
    val existing by vm.existing.collectAsStateWithLifecycle()
    val restoredDraft by vm.restoredDraft.collectAsStateWithLifecycle()
    val isEdit = choreId != null

    when {
        loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        isEdit && existing == null -> Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
            PageHeader("Edit chore", onBack = onDone)
            Text("This chore no longer exists.")
        }
        else -> ChoreForm(vm, existing, restoredDraft, myUid, onDone)
    }
}

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
private fun ChoreForm(
    vm: AddChoreViewModel,
    existing: Chore?,
    restoredDraft: ChoreDraft?,
    myUid: String,
    onDone: () -> Unit
) {
    val members by vm.members.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val today = rememberTodayEpochDay()
    val isAdd = existing == null

    // Edit starts from the saved chore. Add starts from the draft left on this phone, or an empty form.
    val form = rememberSaveable(existing?.id, saver = ChoreFormState.Saver) {
        ChoreFormState(
            existing?.let { ChoreDraft.fromChore(it) } ?: restoredDraft ?: ChoreDraft.newChore(myUid, today)
        )
    }
    var showRestored by rememberSaveable { mutableStateOf(isAdd && restoredDraft != null) }

    // Add mode: keep the draft on the phone up to date shortly after the user stops typing
    if (isAdd) {
        LaunchedEffect(form) {
            snapshotFlow { form.toDraft() }
                .distinctUntilChanged()
                .debounce(500)
                .collect { vm.autoSaveDraft(it) }
        }
    }

    var showPicker by rememberSaveable { mutableStateOf(false) }
    var showEndPicker by rememberSaveable { mutableStateOf(false) }

    // "Today" and "Tomorrow" follow the clock. A picked date stays fixed.
    val dueDay: Long = form.dueOffset?.let { today + it } ?: form.fixedDueDay

    // the saved assignee if still in the household, otherwise me, otherwise the first member
    val assignee: Member? = members.firstOrNull { it.uid == form.assigneeUid }
        ?: members.firstOrNull { it.uid == myUid }
        ?: members.firstOrNull()

    if (showPicker) {
        // the picker works in UTC midnight millis, which is exactly an epoch day
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = dueDay * 86_400_000L)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton({
                    pickerState.selectedDateMillis?.let {
                        form.fixedDueDay = it / 86_400_000L
                        form.dueOffset = null
                    }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton({ showPicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = pickerState) }
    }

    if (showEndPicker) {
        val endPickerState = rememberDatePickerState(
            initialSelectedDateMillis = (if (form.endDay > 0L) form.endDay else dueDay) * 86_400_000L,
            selectableDates = object : SelectableDates {
                // dates before the first scheduled date can't be picked
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    utcTimeMillis / 86_400_000L >= dueDay
            }
        )
        DatePickerDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = {
                TextButton({
                    endPickerState.selectedDateMillis?.let { form.endDay = it / 86_400_000L }
                    showEndPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton({ showEndPicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = endPickerState) }
    }

    // The form scrolls, the Save bar stays pinned at the bottom (and rides above the keyboard)
    Column(Modifier.fillMaxSize().imePadding()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // closing keeps the draft (add mode), same as the add-expense form
                Box(Modifier.weight(1f)) {
                    PageHeader(if (isAdd) "Add chore" else "Edit chore", onBack = onDone)
                }
                // add mode only: empty the form and delete the draft
                if (isAdd) {
                    TextButton(onClick = {
                        form.load(ChoreDraft.newChore(myUid, today))
                        showRestored = false
                        vm.clearDraft()
                    }) { Text("Clear") }
                }
            }
            if (isAdd && showRestored) {
                Text("Restored your unsaved draft", style = MaterialTheme.typography.bodySmall)
            }

            FormSection("What needs doing") {
                OutlinedTextField(
                    value = form.title,
                    onValueChange = { form.title = it },
                    label = { Text("Chore name") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                FieldLabel(
                    "Category",
                    if (error == CATEGORY_REQUIRED_MESSAGE) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                CategoryPicker(selectedKey = form.categoryKey, onSelect = { form.categoryKey = it })
            }

            FormSection("Who and when") {
                FieldLabel("Assign to")
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    members.forEach { m ->
                        FilterChip(
                            selected = assignee?.uid == m.uid,
                            onClick = { form.assigneeUid = m.uid },
                            label = { Text(if (m.uid == myUid) "${m.displayName} (you)" else m.displayName) }
                        )
                    }
                }
                FieldLabel("When should this begin?")
                CardRow {
                    ChoiceCard("Today", dueDay == today, { form.dueOffset = 0 }, Modifier.weight(1f).fillMaxHeight())
                    ChoiceCard("Tomorrow", dueDay == today + 1, { form.dueOffset = 1 }, Modifier.weight(1f).fillMaxHeight())
                    ChoiceCard(
                        "Choose date",
                        dueDay != today && dueDay != today + 1,
                        { showPicker = true },
                        Modifier.weight(1f).fillMaxHeight()
                    )
                }
                Text(
                    formatFullEpochDay(dueDay),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            FormSection("Repeat") {
                // the four choices, plus one extra selected card if the saved chore has some other interval
                val options = Recurrence.entries.map { it.label to it.repeatDays } +
                        if (Recurrence.fromRepeatDays(form.repeatDays) == null) {
                            listOf("Every ${form.repeatDays} days" to form.repeatDays)
                        } else emptyList()
                options.chunked(2).forEach { rowItems ->
                    CardRow {
                        rowItems.forEach { (label, days) ->
                            ChoiceCard(
                                label, form.repeatDays == days, { form.repeatDays = days },
                                Modifier.weight(1f).fillMaxHeight()
                            )
                        }
                        if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            // one-time chores never take turns and have no end date, so these only show for repeating chores
            if (form.repeatDays > 0) {
                FormSection("Take turns between roommates") {
                    CardRow {
                        ChoiceCard(
                            "Take turns", form.rotate, { form.rotate = true },
                            Modifier.weight(1f).fillMaxHeight(),
                            supporting = "Next roommate each time"
                        )
                        ChoiceCard(
                            "Same person", !form.rotate, { form.rotate = false },
                            Modifier.weight(1f).fillMaxHeight(),
                            supporting = assignee?.displayName ?: "Stays with one roommate"
                        )
                    }
                    Text(
                        if (form.rotate) "Roommates take turns according to the chore's schedule. Each scheduled date goes to the next person in the household order."
                        else "Roommates could take turns according to the chore's schedule. Right now every scheduled date stays with ${assignee?.displayName ?: "the same person"}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                FormSection("When should this chore stop repeating?") {
                    CardRow {
                        ChoiceCard("No end date", form.endDay == 0L, { form.endDay = 0L }, Modifier.weight(1f).fillMaxHeight())
                        ChoiceCard(
                            "Choose an end date", form.endDay > 0L, { showEndPicker = true },
                            Modifier.weight(1f).fillMaxHeight()
                        )
                    }
                    if (form.endDay > 0L) {
                        Text(
                            formatFullEpochDay(form.endDay),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (form.endDay < dueDay) {
                            Text(
                                "$END_BEFORE_START_MESSAGE (${formatFullEpochDay(dueDay)}).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // Reads the same state the form saves, so it always matches what will be stored
            FormSection("Schedule preview") {
                val preview = schedulePreview(
                    startEpochDay = dueDay,
                    repeatDays = form.repeatDays,
                    rotate = form.rotate,
                    assigneeName = assignee?.displayName,
                    endEpochDay = resolveEndEpochDay(form.repeatDays, form.endDay)
                )
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        preview.forEach { line ->
                            Row(Modifier.fillMaxWidth()) {
                                Text(
                                    line.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(88.dp)
                                )
                                Text(line.value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            FormSection("Notes") {
                OutlinedTextField(
                    value = form.notes,
                    onValueChange = { form.notes = it.take(CHORE_NOTES_MAX) },
                    label = { Text("Notes (optional)") },
                    placeholder = { Text("Add instructions or details about this chore...") },
                    supportingText = {
                        Text("${form.notes.length}/$CHORE_NOTES_MAX", Modifier.fillMaxWidth(), textAlign = TextAlign.End)
                    },
                    minLines = 3,
                    maxLines = 6,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(8.dp))
        }

        Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Button(
                    onClick = {
                        vm.save(
                            form.title, form.categoryKey, assignee, dueDay,
                            form.repeatDays, form.rotate, form.endDay, form.notes, onDone
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Text(if (isAdd) "Save chore" else "Save changes") }
            }
        }
    }
}

// A titled group of related settings
@Composable
private fun FormSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@Composable
private fun FieldLabel(
    text: String,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = color)
}

// A row of equal-height cards: the tallest card sets the height for all of them
@Composable
private fun CardRow(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}