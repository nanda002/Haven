package com.app.haven.view.homeUI

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.app.haven.domain.chore.Chore
import com.app.haven.domain.chore.dueLabel
import com.app.haven.domain.chore.todayEpochDay
import com.app.haven.view.choreUI.amberTextColor
import androidx.compose.foundation.background

// Dashboard card: my chores due now or within the week (only shown when there are some). Tap -> Chores tab
@Composable
fun HomeChoresCard(chores: List<Chore>, onClick: () -> Unit) {
    val today = todayEpochDay()
    val dueCount = chores.count { it.dueEpochDay <= today }
    val subtitle = when {
        dueCount == 1 -> "1 chore to do"
        dueCount > 1 -> "$dueCount chores to do"
        else -> "Coming up this week"
    }

    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // header: small icon in a rounded square + title
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.CleaningServices,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Text("Chores", style = MaterialTheme.typography.titleMedium)
            }

            if (chores.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text("You’re all caught up, no chores for now!", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                Text(subtitle, style = MaterialTheme.typography.bodyLarge)
                chores.take(3).forEach { c ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(c.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(
                            c.dueLabel(today),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (c.dueEpochDay < today) amberTextColor()
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (chores.size > 3) Text("+${chores.size - 3} more", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}