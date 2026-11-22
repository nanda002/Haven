package com.app.haven.view.groceryItemUI

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.app.haven.domain.grocerylist.ScannedItem
import com.app.haven.viewmodel.groceryitem.ScanState

@Composable
fun ScanProductDialog(
    scan: ScanState,
    onAddToList: (ScannedItem) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    when (scan) {
        ScanState.Hidden -> Unit

        is ScanState.Loading -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Looking up product") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Text("Barcode ${scan.code}")
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
        )

        is ScanState.Found -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Add to list?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    scan.item.brand?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
                    Text(scan.item.name, style = MaterialTheme.typography.bodyLarge)
                }
            },
            confirmButton = { TextButton(onClick = { onAddToList(scan.item) }) { Text("Add to list") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
        )

        is ScanState.NotFound -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Product not found") },
            text = { Text("We couldn't find this product in Open Food Facts. You can type the item instead.") },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Type it instead") } }
        )

        is ScanState.Error -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Couldn't look up product") },
            text = { Text(scan.message) },
            confirmButton = { TextButton(onClick = onRetry) { Text("Retry") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
        )
    }
}
