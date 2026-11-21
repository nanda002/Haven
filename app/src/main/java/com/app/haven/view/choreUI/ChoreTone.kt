package com.app.haven.view.choreUI

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.app.haven.domain.chore.ChoreStatus

// Soft card tints: light blue due today, amber overdue, green done, plain for upcoming
@Composable
fun choreContainerColor(status: ChoreStatus): Color {
    val dark = isSystemInDarkTheme()
    return when (status) {
        ChoreStatus.DUE_TODAY -> if (dark) Color(0xFF1F3347) else Color(0xFFE6F1FB)
        ChoreStatus.OVERDUE -> if (dark) Color(0xFF3A2F1A) else Color(0xFFFFF3DC)
        ChoreStatus.DONE -> if (dark) Color(0xFF1F3326) else Color(0xFFE8F5EA)
        ChoreStatus.UPCOMING -> MaterialTheme.colorScheme.surfaceContainerLowest
    }
}

@Composable
fun amberTextColor(): Color = if (isSystemInDarkTheme()) Color(0xFFFFC46B) else Color(0xFF8A5A00)
