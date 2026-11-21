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
import com.app.haven.domain.chore.ChoreScope
import com.app.haven.domain.chore.END_BEFORE_START_MESSAGE
import com.app.haven.domain.chore.Recurrence
import com.app.haven.domain.chore.formatFullEpochDay
import com.app.haven.domain.chore.resolveEndEpochDay
import com.app.haven.domain.chore.schedulePreview
import com.app.haven.view.common.PageHeader
import com.app.haven.viewmodel.chore.AddChoreViewModel
import com.app.haven.viewmodel.chore.AutoAssignPreview
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization

// Add form (choreId = null) and edit form (choreId = an existing chore) share this screen
@Composable
fun AddChoreScreen(
    householdId: String,
    myUid: String,
    onDone: () -> Unit,
    choreId: String? = null,
    vm: AddChoreViewModel = viewModel(factory = AddChoreViewModel.factory(householdId, myUid, choreId))
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
    val saving by vm.saving.collectAsStateWithLifecycle()
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

    // edit mode only: true when the user deliberately chose Automatic again. Always starts off,
    // so saving an ordinary edit never reassigns the chore.
    var reassignAuto by rememberSaveable { mutableStateOf(false) }


    // "Today" and "Tomorrow" follow the clock. A picked date stays fixed.
    val dueDay: Long = form.dueOffset?.let { today + it } ?: form.fixedDueDay

    val isPersonal = if (isAdd) form.scope == ChoreScope.PERSONAL else existing?.scope == ChoreScope.PERSONAL
    val autoAssign = !isPersonal && (if (isAdd) form.autoAssign else reassignAuto)
    val autoPreview by vm.autoPreview.collectAsStateWithLifecycle()

    // The pick is worked out each time Automatic is shown, and again when the chore is saved
    if (autoAssign) {
        LaunchedEffect(Unit) { vm.refreshAutoPreview() }
    }

    // Who can be picked by hand: not on vacation (an existing assignee on vacation stays visible)
    val manualOptions = members.filter { !it.vacation || it.uid == existing?.assigneeUid }
    val chosenMember: Member? = manualOptions.firstOrNull { it.uid == form.assigneeUid }
    val memberLabel = { m: Member -> if (m.uid == myUid) "${m.displayName} (you)" else m.displayName }

    // Edit: the saved assignee has left the household. They stay on the chore (nobody else is
    // swapped in) until the user deliberately picks a roommate.
    val assigneeLeft = existing != null && !isPersonal && members.isNotEmpty() &&
            chosenMember == null && form.assigneeUid == existing.assigneeUid

    val assignee: Member? = when {
        // personal: always me
        isPersonal -> members.firstOrNull { it.uid == myUid }
            ?: existing?.let { Member(uid = it.assigneeUid, displayName = it.assigneeName) }
        chosenMember != null -> chosenMember
        // edit, still on the saved assignee (also covers the moment before the member list loads)
        existing != null && form.assigneeUid == existing.assigneeUid ->
            Member(uid = existing.assigneeUid, displayName = existing.assigneeName, active = false)
        // new chore: Automatic is decided when saving, Manual needs a pick
        else -> null
    }

    // the name shown in the previews
    val assigneeLabel: String? =
        if (autoAssign) (autoPreview as? AutoAssignPreview.Chosen)?.name else assignee?.displayName

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

            FormSection("Chore Details") {
                OutlinedTextField(
                    value = form.title,
                    onValueChange = { form.title = it },
                    label = { Text("Chore name") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
                )
                FieldLabel(
                    "Category",
                    if (error == CATEGORY_REQUIRED_MESSAGE) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                CategoryPicker(selectedKey = form.categoryKey, onSelect = { form.categoryKey = it })
            }

            // set when the chore is created and can't be changed afterwards (the two kinds are stored separately)
            if (isAdd) {
                FormSection("Chore Type") {
                    CardRow {
                        ChoiceCard(
                            "Household", form.scope == ChoreScope.HOUSEHOLD,
                            { form.scope = ChoreScope.HOUSEHOLD },
                            Modifier.weight(1f).fillMaxHeight(),
                            supporting = "Shared with the household"
                        )
                        ChoiceCard(
                            "Personal", form.scope == ChoreScope.PERSONAL,
                            { form.scope = ChoreScope.PERSONAL },
                            Modifier.weight(1f).fillMaxHeight(),
                            supporting = "Only you can see it"
                        )
                    }
                }
            }

            FormSection("Who & When") {
                when {
                    isPersonal -> Text(
                        "This chore is assigned to you. Only you can see it, and it doesn't count toward the household's contributions or workload.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    isAdd -> {
                        FieldLabel("How Should It Be Assigned?")
                        CardRow {
                            ChoiceCard(
                                "Automatic", form.autoAssign, { form.autoAssign = true },
                                Modifier.weight(1f).fillMaxHeight(),
                                supporting = "Whoever has done the least"
                            )
                            ChoiceCard(
                                "Manual", !form.autoAssign, { form.autoAssign = false },
                                Modifier.weight(1f).fillMaxHeight(),
                                supporting = "Pick a roommate"
                            )
                        }
                        if (form.autoAssign) {
                            AutoAssignSummary(autoPreview)
                        } else if (manualOptions.isEmpty()) {
                            Text(
                                "No roommates are available to pick right now.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            MemberChoices(
                                options = manualOptions.map { it.uid to memberLabel(it) },
                                selectedUid = form.assigneeUid,
                                onSelect = { form.assigneeUid = it }
                            )
                        }
                    }
                    else -> {
                        FieldLabel("How Should It Be Assigned?")
                        CardRow {
                            ChoiceCard(
                                "Pick a roommate", !reassignAuto, { reassignAuto = false },
                                Modifier.weight(1f).fillMaxHeight(),
                                supporting = "Keep or change by hand"
                            )
                            ChoiceCard(
                                "Automatic", reassignAuto, { reassignAuto = true },
                                Modifier.weight(1f).fillMaxHeight(),
                                supporting = "Choose fairly again"
                            )
                        }
                        if (reassignAuto) {
                            Text(
                                "This will reassign the chore and rebuild the turn order, so everyone currently in the household is included.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            AutoAssignSummary(autoPreview)
                        } else {
                            MemberChoices(
                                options = listOfNotNull(
                                    if (assigneeLeft && assignee != null) assignee.uid to "${assignee.displayName} (left)" else null
                                ) + manualOptions.map { it.uid to memberLabel(it) },
                                selectedUid = form.assigneeUid,
                                onSelect = { form.assigneeUid = it }
                            )
                            if (assigneeLeft && assignee != null) {
                                Text(
                                    "${assignee.displayName} has left the household. Pick a roommate to take over, or save to keep it with ${assignee.displayName} for now.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                FieldLabel("When Should This Begin?")
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
                if (!isPersonal) FormSection("Take Turns Between Roommates") {
                    CardRow {
                        ChoiceCard(
                            "Take turns", form.rotate, { form.rotate = true },
                            Modifier.weight(1f).fillMaxHeight(),
                            supporting = "Next roommate each time"
                        )
                        ChoiceCard(
                            "Same person", !form.rotate, { form.rotate = false },
                            Modifier.weight(1f).fillMaxHeight(),
                            supporting = assigneeLabel ?: "Stays with one roommate"
                        )
                    }
                    Text(
                        if (form.rotate) "Roommates take turns according to the chore's schedule. Each scheduled date goes to the next person in the household order."
                        else "Roommates could take turns according to the chore's schedule. Right now every scheduled date stays with ${assigneeLabel ?: "the same person"}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                FormSection("When Should This Chore Stop Repeating?") {
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
                    rotate = form.rotate && !isPersonal,
                    assigneeName = assigneeLabel,
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
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
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
                            title = form.title,
                            category = form.categoryKey,
                            scope = if (isAdd) form.scope else existing?.scope ?: ChoreScope.HOUSEHOLD,
                            autoAssign = if (isAdd) form.autoAssign else reassignAuto,
                            assignee = assignee,
                            dueEpochDay = dueDay,
                            repeatDays = form.repeatDays,
                            rotate = form.rotate,
                            endEpochDay = form.endDay,
                            notes = form.notes,
                            onDone = onDone
                        )
                    },
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Text(if (saving) "Saving..." else if (isAdd) "Save chore" else "Save changes") }
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

// One selectable card per roommate, exactly one can be selected
@Composable
private fun MemberChoices(
    options: List<Pair<String, String>>,   // uid to label
    selectedUid: String?,
    onSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (uid, label) ->
            ChoiceCard(label, selectedUid == uid, { onSelect(uid) }, Modifier.fillMaxWidth())
        }
    }
}

// Under Automatic: who would get the chore, or why nobody can
@Composable
private fun AutoAssignSummary(preview: AutoAssignPreview) {
    val unavailable = preview is AutoAssignPreview.Unavailable
    val text = when (preview) {
        AutoAssignPreview.Loading -> "Choosing a roommate..."
        is AutoAssignPreview.Chosen -> "Will be assigned to ${preview.name}"
        is AutoAssignPreview.Unavailable -> preview.message
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (unavailable) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
            if (preview is AutoAssignPreview.Chosen) {
                Text(
                    "Based on who has done the least so far. It is checked again when you save.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
