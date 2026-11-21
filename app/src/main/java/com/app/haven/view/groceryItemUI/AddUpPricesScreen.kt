package com.app.haven.view.groceryItemUI

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.haven.view.expenseUI.money
import com.app.haven.viewmodel.groceryitem.AddUpPricesViewModel
import com.app.haven.viewmodel.groceryitem.PriceRow

//"Enter prices" step: one price field per item the user bought, a live total,
//and Continue, which hands the total + description to the add expense form
@Composable
fun AddUpPricesScreen(
    householdId: String,
    myUid: String,
    onClose: () -> Unit,
    //called with the total (cents), e.g. "Groceries: Milk, Eggs", and the ticked item ids;
    //the add expense form opens from here
    onContinue: (amountCents: Long, description: String, itemIds: List<String>) -> Unit,
    vm: AddUpPricesViewModel = viewModel(factory = AddUpPricesViewModel.factory(householdId, myUid))
) {
    val s by vm.uiState.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().imePadding().padding(16.dp)) {
        //header: close + title
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClose) { Icon(Icons.Default.Close, contentDescription = "Close") }
            Text("Enter prices", style = MaterialTheme.typography.headlineSmall)
        }
        Text(
            "Untick anything that wasn't on this receipt.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)
        )

        //the rows take the space between the header and the total, so the total stays visible
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                s.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                s.rows.isEmpty() -> Text("No bought items to add", Modifier.align(Alignment.Center))
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(s.rows, key = { it.item.id }) { row ->
                        PriceRowItem(
                            row = row,
                            onIncludedChanged = { vm.onIncludedChanged(row.item.id, it) },
                            onPriceChanged = { vm.onPriceChanged(row.item.id, it) }
                        )
                    }
                }
            }
        }

        //total + continue
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Total", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(s.totalCents.money(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { vm.onContinue(onContinue) },
            enabled = s.canContinue,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Continue to expense") }
    }
}

@Composable
private fun PriceRowItem(
    row: PriceRow,
    onIncludedChanged: (Boolean) -> Unit,
    onPriceChanged: (String) -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = row.included, onCheckedChange = onIncludedChanged)
        Text(
            row.item.description,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = row.priceText,
            onValueChange = onPriceChanged,
            enabled = row.included,
            singleLine = true,
            prefix = { Text("$") },
            placeholder = { Text("0.00") },
            isError = row.priceError != null,
            supportingText = row.priceError?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.width(130.dp)
        )
    }
}
