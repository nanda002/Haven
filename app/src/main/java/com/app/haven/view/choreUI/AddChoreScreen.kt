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

private val repeatOptions = listOf("Once" to 0, "Daily" to 1, "Weekly" to 7)
private val durationOptions = listOf("Forever" to 0, "2 weeks" to 2, "4 weeks" to 4, "8 weeks" to 8)

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
    val today = remember { todayEpochDay() }
    val key = existing?.id

    var title by rememberSaveable(key) { mutableStateOf(existing?.title ?: "") }
    var assigneeUid by rememberSaveable(key) { mutableStateOf(existing?.assigneeUid) }
    var dueDay by rememberSaveable(key) { mutableStateOf(existing?.dueEpochDay ?: today) }
    var repeatDays by rememberSaveable(key) { mutableStateOf(existing?.repeatDays ?: 0) }
    var rotate by rememberSaveable(key) { mutableStateOf(existing?.rotate ?: true) }
    // null while editing = keep the current end date untouched
    var durationWeeks by rememberSaveable(key) { mutableStateOf<Int?>(if (existing != null) null else 0) }
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
                    pickerState.selectedDateMillis?.let { dueDay = it / 86_400_000L }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton({ showPicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = pickerState) }
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

        Text("Due", style = MaterialTheme.typography.labelLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = dueDay == today, onClick = { dueDay = today }, label = { Text("Today") })
            FilterChip(selected = dueDay == today + 1, onClick = { dueDay = today + 1 }, label = { Text("Tomorrow") })
            FilterChip(
                selected = dueDay != today && dueDay != today + 1,
                onClick = { showPicker = true },
                label = { Text(if (dueDay != today && dueDay != today + 1) formatEpochDay(dueDay) else "Pick a date") }
            )
        }

        Text("Repeat", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeatOptions.forEach { (label, days) ->
                FilterChip(selected = repeatDays == days, onClick = { repeatDays = days }, label = { Text(label) })
            }
        }

        if (repeatDays > 0) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Rotate between roommates", style = MaterialTheme.typography.labelLarge)
                    Text(
                        if (rotate) "Each time it's done, it moves to the next roommate."
                        else "Stays with ${assignee?.displayName ?: "the same person"} every time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = rotate, onCheckedChange = { rotate = it })
            }

            Text("Keep repeating for", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                durationOptions.forEach { (label, weeks) ->
                    FilterChip(selected = durationWeeks == weeks, onClick = { durationWeeks = weeks }, label = { Text(label) })
                }
            }
            if (durationWeeks == null && existing != null) {
                Text(
                    if (existing.endEpochDay > 0L) "Currently ends ${formatEpochDay(existing.endEpochDay)}"
                    else "Currently repeats forever",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

        Button(
            onClick = { vm.save(title, assignee, dueDay, repeatDays, rotate, durationWeeks, onDone) },
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (existing == null) "Save chore" else "Save changes") }
    }
}