package com.app.haven.view.homeUI

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.app.haven.domain.chore.Chore
import com.app.haven.domain.chore.dueLabel
import com.app.haven.domain.chore.todayEpochDay

// Dashboard card: only appears when I have chores to do. Tap -> Chores tab
@Composable
fun HomeChoresCard(chores: List<Chore>, onClick: () -> Unit) {
    if (chores.isEmpty()) return
    val today = todayEpochDay()

    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                if (chores.size == 1) "1 chore to do" else "${chores.size} chores to do",
                style = MaterialTheme.typography.titleMedium
            )
            chores.take(3).forEach { c ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(c.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(
                        c.dueLabel(today),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (c.dueEpochDay < today) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (chores.size > 3) Text("+${chores.size - 3} more", style = MaterialTheme.typography.bodySmall)
            Text("Tap to open Chores", style = MaterialTheme.typography.bodySmall)
        }
    }
}