package com.app.haven.view.choreUI

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.haven.view.common.PageHeader
import com.app.haven.viewmodel.chore.AddChoreViewModel

private val dueOptions = listOf("Today" to 0, "Tomorrow" to 1, "In 3 days" to 3, "Next week" to 7)
private val repeatOptions = listOf("Once" to 0, "Daily" to 1, "Weekly" to 7)

@Composable
fun AddChoreScreen(
    householdId: String,
    myUid: String,
    onDone: () -> Unit,
    vm: AddChoreViewModel = viewModel(factory = AddChoreViewModel.factory(householdId))
) {
    val members by vm.members.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()

    var title by rememberSaveable { mutableStateOf("") }
    var assigneeUid by rememberSaveable { mutableStateOf<String?>(null) }
    var dueInDays by rememberSaveable { mutableIntStateOf(0) }
    var repeatDays by rememberSaveable { mutableIntStateOf(0) }

    // default to me, otherwise the first member in the list
    val assignee = members.firstOrNull { it.uid == assigneeUid }
        ?: members.firstOrNull { it.uid == myUid }
        ?: members.firstOrNull()

    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PageHeader("Add chore", onBack = onDone)

        OutlinedTextField(
            title, { title = it }, label = { Text("Choregit log --oneline") },
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
            dueOptions.forEach { (label, days) ->
                FilterChip(selected = dueInDays == days, onClick = { dueInDays = days }, label = { Text(label) })
            }
        }

        Text("Repeat", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeatOptions.forEach { (label, days) ->
                FilterChip(selected = repeatDays == days, onClick = { repeatDays = days }, label = { Text(label) })
            }
        }
        if (repeatDays > 0) {
            Text(
                "Each time it's done, it moves to the next roommate.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

        Button(
            onClick = { vm.save(title, assignee, dueInDays, repeatDays, onDone) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Save chore") }
    }
}