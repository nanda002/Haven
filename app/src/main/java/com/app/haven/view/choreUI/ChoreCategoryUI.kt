package com.app.haven.view.choreUI

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bathtub
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.app.haven.domain.chore.ChoreCategory

// Provides the chore category picker, displaying each category as a selectable card
// with its corresponding icon and highlighting the currently selected category.
val ChoreCategory.icon: ImageVector
    get() = when (this) {
        ChoreCategory.KITCHEN -> Icons.Default.Kitchen
        ChoreCategory.BATHROOM -> Icons.Default.Bathtub
        ChoreCategory.CLEANING -> Icons.Default.CleaningServices
        ChoreCategory.LAUNDRY -> Icons.Default.LocalLaundryService
        ChoreCategory.GROCERIES -> Icons.Default.ShoppingBasket
        ChoreCategory.PERSONAL -> Icons.Default.Person
        ChoreCategory.OTHER -> Icons.Default.MoreHoriz
    }

@Composable
fun CategoryPicker(selectedKey: String?, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ChoreCategory.entries.chunked(4).forEach { rowItems ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowItems.forEach { category ->
                    CategoryCard(
                        category = category,
                        selected = category.key == selectedKey,
                        onClick = { onSelect(category.key) },
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(4 - rowItems.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun CategoryCard(
    category: ChoreCategory,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) scheme.primaryContainer else scheme.surfaceContainerLowest,
        contentColor = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) scheme.primary else scheme.outlineVariant
        )
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(category.icon, contentDescription = null)
            Text(category.label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}