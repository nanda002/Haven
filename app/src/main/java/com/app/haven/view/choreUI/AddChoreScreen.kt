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
import com.app.haven.domain.chore.END_BEFORE_START_MESSAGE
import com.app.haven.domain.chore.Recurrence
import com.app.haven.domain.chore.formatFullEpochDay
import com.app.haven.domain.chore.resolveEndEpochDay
import com.app.haven.domain.chore.schedulePreview
import com.app.haven.view.common.PageHeader
import com.app.haven.viewmodel.chore.AddChoreViewModel

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
    val isEdit = choreId != null

    when {
        isEdit && loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        isEdit && existing == null -> Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
            PageHeader("Edit chore", onBack = onDone)
            Text("This chore no longer exists.")
        }
        else -> ChoreForm(vm, existing, myUid, onDone)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChoreForm(vm: AddChoreViewModel, existing: Chore?, myUid: String, onDone: () -> Unit) {
    val members by vm.members.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val today = rememberTodayEpochDay()
    val key = existing?.id

    var title by rememberSaveable(key) { mutableStateOf(existing?.title ?: "") }
    var categoryKey by rememberSaveable(key) { mutableStateOf<String?>(existing?.category) }
    var notes by rememberSaveable(key) { mutableStateOf(existing?.notes ?: "") }
    var assigneeUid by rememberSaveable(key) { mutableStateOf(existing?.assigneeUid) }

    // Add: "Today" by default and follows the clock. Edit: the saved date, fixed.
    var dueOffset by rememberSaveable(key) { mutableStateOf<Int?>(if (existing == null) 0 else null) }
    var fixedDueDay by rememberSaveable(key) { mutableStateOf(existing?.dueEpochDay ?: today) }
    val dueDay: Long = dueOffset?.let { today + it } ?: fixedDueDay

    var repeatDays by rememberSaveable(key) { mutableStateOf(existing?.repeatDays ?: 0) }
    var rotate by rememberSaveable(key) { mutableStateOf(existing?.rotate ?: true) }
    // 0 = no end date. Edit starts from the saved end date, so unrelated edits never reset it.
    var endDay by rememberSaveable(key) { mutableStateOf(existing?.endEpochDay ?: 0L) }
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var showEndPicker by rememberSaveable { mutableStateOf(false) }

    // the saved assignee if still in the household, otherwise me, otherwise the first member
    val assignee: Member? = members.firstOrNull { it.uid == assigneeUid }
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
                        fixedDueDay = it / 86_400_000L
                        dueOffset = null
                    }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton({ showPicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = pickerState) }
    }

    if (showEndPicker) {
        val endPickerState = rememberDatePickerState(
            initialSelectedDateMillis = (if (endDay > 0L) endDay else dueDay) * 86_400_000L,
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
                    endPickerState.selectedDateMillis?.let { endDay = it / 86_400_000L }
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
            PageHeader(if (existing == null) "Add chore" else "Edit chore", onBack = onDone)

            FormSection("What needs doing") {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
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
                CategoryPicker(selectedKey = categoryKey, onSelect = { categoryKey = it })
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
                            onClick = { assigneeUid = m.uid },
                            label = { Text(if (m.uid == myUid) "${m.displayName} (you)" else m.displayName) }
                        )
                    }
                }
                FieldLabel("When should this begin?")
                CardRow {
                    ChoiceCard("Today", dueDay == today, { dueOffset = 0 }, Modifier.weight(1f).fillMaxHeight())
                    ChoiceCard("Tomorrow", dueDay == today + 1, { dueOffset = 1 }, Modifier.weight(1f).fillMaxHeight())
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
                        if (Recurrence.fromRepeatDays(repeatDays) == null) listOf("Every $repeatDays days" to repeatDays)
                        else emptyList()
                options.chunked(2).forEach { rowItems ->
                    CardRow {
                        rowItems.forEach { (label, days) ->
                            ChoiceCard(
                                label, repeatDays == days, { repeatDays = days },
                                Modifier.weight(1f).fillMaxHeight()
                            )
                        }
                        if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            // one-time chores never take turns and have no end date, so these only show for repeating chores
            if (repeatDays > 0) {
                FormSection("Take turns between roommates") {
                    CardRow {
                        ChoiceCard(
                            "Take turns", rotate, { rotate = true },
                            Modifier.weight(1f).fillMaxHeight(),
                            supporting = "Next roommate each time"
                        )
                        ChoiceCard(
                            "Same person", !rotate, { rotate = false },
                            Modifier.weight(1f).fillMaxHeight(),
                            supporting = assignee?.displayName ?: "Stays with one roommate"
                        )
                    }
                    Text(
                        if (rotate) "Roommates take turns according to the chore's schedule. Each scheduled date goes to the next person in the household order."
                        else "Roommates could take turns according to the chore's schedule. Right now every scheduled date stays with ${assignee?.displayName ?: "the same person"}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                FormSection("When should this chore stop repeating?") {
                    CardRow {
                        ChoiceCard("No end date", endDay == 0L, { endDay = 0L }, Modifier.weight(1f).fillMaxHeight())
                        ChoiceCard(
                            "Choose an end date", endDay > 0L, { showEndPicker = true },
                            Modifier.weight(1f).fillMaxHeight()
                        )
                    }
                    if (endDay > 0L) {
                        Text(
                            formatFullEpochDay(endDay),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (endDay < dueDay) {
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
                    repeatDays = repeatDays,
                    rotate = rotate,
                    assigneeName = assignee?.displayName,
                    endEpochDay = resolveEndEpochDay(repeatDays, endDay)
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
                    value = notes,
                    onValueChange = { notes = it.take(CHORE_NOTES_MAX) },
                    label = { Text("Notes (optional)") },
                    placeholder = { Text("Add instructions or details about this chore...") },
                    supportingText = {
                        Text("${notes.length}/$CHORE_NOTES_MAX", Modifier.fillMaxWidth(), textAlign = TextAlign.End)
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
                    onClick = { vm.save(title, categoryKey, assignee, dueDay, repeatDays, rotate, endDay, notes, onDone) },
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Text(if (existing == null) "Save chore" else "Save changes") }
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