package com.app.haven.view.choreUI

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.haven.data.model.Member
import com.app.haven.domain.chore.Chore
import com.app.haven.domain.chore.formatEpochDay
import com.app.haven.domain.chore.todayEpochDay
import com.app.haven.view.common.PageHeader
import com.app.haven.viewmodel.chore.AddChoreViewModel
import com.app.haven.domain.chore.CATEGORY_REQUIRED_MESSAGE
import androidx.compose.ui.text.style.TextAlign
import com.app.haven.domain.chore.CHORE_NOTES_MAX
import com.app.haven.domain.chore.formatFullEpochDay
import androidx.compose.foundation.shape.RoundedCornerShape
import com.app.haven.domain.chore.Recurrence
import com.app.haven.domain.chore.resolveEndEpochDay
import com.app.haven.domain.chore.schedulePreview
import com.app.haven.domain.chore.END_BEFORE_START_MESSAGE

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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ChoreForm(vm: AddChoreViewModel, existing: Chore?, myUid: String, onDone: () -> Unit) {
    val members by vm.members.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val today = rememberTodayEpochDay()
    val key = existing?.id

    var title by rememberSaveable(key) { mutableStateOf(existing?.title ?: "") }
    // null on a new chore = nothing selected yet (required). Edit = the chore's stored category.
    var categoryKey by rememberSaveable(key) { mutableStateOf<String?>(existing?.category) }
    var notes by rememberSaveable(key) { mutableStateOf(existing?.notes ?: "") }
    var assigneeUid by rememberSaveable(key) { mutableStateOf(existing?.assigneeUid) }
    // Add: "Today" by default and follows the clock. Edit: the saved date, fixed.
    var dueOffset by rememberSaveable(key) { mutableStateOf<Int?>(if (existing == null) 0 else null) }
    var fixedDueDay by rememberSaveable(key) { mutableStateOf(existing?.dueEpochDay ?: today) }
    val dueDay: Long = dueOffset?.let { today + it } ?: fixedDueDay
    var repeatDays by rememberSaveable(key) { mutableStateOf(existing?.repeatDays ?: 0) }
    var rotate by rememberSaveable(key) { mutableStateOf(existing?.rotate ?: true) }

    // 0 = no end date. Edit starts from the saved cutoff, so unrelated edits never reset it.
    var endDay by rememberSaveable(key) { mutableStateOf(existing?.endEpochDay ?: 0L) }
    var showEndPicker by rememberSaveable { mutableStateOf(false) }
    var showPicker by rememberSaveable { mutableStateOf(false) }

    // keep the saved assignee if they're still in the household, otherwise me, otherwise the first member
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
                        fixedDueDay = it / 86_400_000L   // UTC midnight millis = the calendar date picked
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

    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PageHeader(if (existing == null) "Add chore" else "Edit chore", onBack = onDone)

        OutlinedTextField(
            title, { title = it }, label = { Text("Chore name") },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Text(
            "Category",
            style = MaterialTheme.typography.labelLarge,
            color = if (error == CATEGORY_REQUIRED_MESSAGE) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurface
        )
        CategoryPicker(selectedKey = categoryKey, onSelect = { categoryKey = it })


        Text("Assign to", style = MaterialTheme.typography.labelLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            members.forEach { m ->
                FilterChip(
                    selected = assignee?.uid == m.uid,
                    onClick = { assigneeUid = m.uid },
                    label = { Text(if (m.uid == myUid) "${m.displayName} (you)" else m.displayName) }
                )
            }
        }

        Text("When should this begin?", style = MaterialTheme.typography.labelLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = dueDay == today, onClick = { dueOffset = 0 }, label = { Text("Today") })
            FilterChip(selected = dueDay == today + 1, onClick = { dueOffset = 1 }, label = { Text("Tomorrow") })
            FilterChip(
                selected = dueDay != today && dueDay != today + 1,
                onClick = { showPicker = true },
                label = { Text("Choose date") }
            )
        }
        Text(
            formatFullEpochDay(dueDay),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text("Repeat", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Recurrence.entries.forEach { option ->
                FilterChip(
                    selected = repeatDays == option.repeatDays,
                    onClick = { repeatDays = option.repeatDays },
                    label = { Text(option.label) }
                )
            }
            // a saved chore with some other value: show it selected and leave it unchanged
            if (Recurrence.fromRepeatDays(repeatDays) == null) {
                FilterChip(selected = true, onClick = {}, label = { Text("Every $repeatDays days") })
            }
        }

        if (repeatDays > 0) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Take turns between roommates", style = MaterialTheme.typography.labelLarge)
                    Text(
                        if (rotate) "Roommates take turns according to the chore's schedule. Each scheduled date goes to the next person in the household order."
                        else "Roommates would take turns according to the chore's schedule. Right now it stays with ${assignee?.displayName ?: "the same person"} every time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = rotate, onCheckedChange = { rotate = it })
            }


            Text("When should this chore stop repeating?", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = endDay == 0L, onClick = { endDay = 0L }, label = { Text("No end date") })
                FilterChip(selected = endDay > 0L, onClick = { showEndPicker = true }, label = { Text("Choose an end date") })
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
            modifier = Modifier.fillMaxWidth()
        )

        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                schedulePreview(
                    startEpochDay = dueDay,
                    repeatDays = repeatDays,
                    rotate = rotate,
                    assigneeName = assignee?.displayName,
                    endEpochDay = resolveEndEpochDay(repeatDays, endDay)
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp)
            )
        }

        Button(
            onClick = { vm.save(title, categoryKey, assignee, dueDay, repeatDays, rotate, endDay, notes, onDone) },
        ){ Text(if (existing == null) "Save chore" else "Save changes") }
    }
}