package com.app.haven.view.homeUI

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding

import androidx.compose.material3.Card

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.app.haven.domain.grocerylist.GroceryItem

@Composable
fun HomeGroceryCard(items: List<GroceryItem>, onClick: () -> Unit) {

    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        //plain Column instead of LazyColumn: a lazy list inside a scrolling Home screen would crash,
        //and the card only shows a few items anyway (items are already only the unbought ones)
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Grocery list", style = MaterialTheme.typography.titleMedium)
            if (items.isEmpty()) {
                Text("No items to buy!", style = MaterialTheme.typography.bodyMedium)
            }
            items.take(MAX_ITEMS).forEach { GroceryCardRow(it) }
            if (items.size > MAX_ITEMS) {
                Text("and ${items.size - MAX_ITEMS} more", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

//how many items the Home card lists before "and N more"
private const val MAX_ITEMS = 5

@Composable
fun GroceryCardRow(
    item: GroceryItem,
) {

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                item.description,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (item.bought) TextDecoration.LineThrough else null
            )
//            val subtitle = if (item.bought) "Bought by ${item.paidByName}" else "Added by ${item.addedByName}"
//            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}
