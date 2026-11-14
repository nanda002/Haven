package com.app.haven.view.groceryItemUI

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.haven.domain.grocerylist.GroceryItem
import com.app.haven.viewmodel.groceryitem.MainGroceryItemViewModel

//Gets the ViewModel and passes its state and events into the screen below.
//This is what the "pantry" route in MainScaffold should call.
@Composable
fun GroceryListRoute(
    householdId: String,
    myUid: String,
    vm: MainGroceryItemViewModel = viewModel(factory = MainGroceryItemViewModel.factory(householdId, myUid))
) {
    val items by vm.items.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    MainGroceryItemScreen(
        myUid = myUid,
        items = items,
        error = error,
        onAdd = vm::onAdd,
        onToggleBought = vm::onToggleBought,
        onRename = vm::onRename,
        onDelete = vm::onDelete
    )
}

@Composable
fun MainGroceryItemScreen(
    myUid: String,
    items: List<GroceryItem>,
    error: String?,
    //called with the typed name; call onAdded() once it was added so the field is cleared for the next item
    onAdd: (name: String, onAdded: () -> Unit) -> Unit,
    //checkbox: mark an item bought / not bought
    onToggleBought: (GroceryItem) -> Unit,
    onRename: (GroceryItem, String) -> Unit,
    onDelete: (String) -> Unit,
    //bottom bar's "Add to expense"; does nothing until the conversion screen exists
    onAddToExpense: () -> Unit = {}
) {
    //what the user is typing in the add bar (survives rotation)
    var text by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<GroceryItem?>(null) }
    var pendingDelete by remember { mutableStateOf<GroceryItem?>(null) }

    //bottom bar: items this user bought that are still on the list
    val boughtByMeCount = items.count { it.bought && it.paidByUid == myUid }
    //count when the user tapped ✕; the bar comes back once they buy more than that
    var dismissedAtCount by rememberSaveable { mutableIntStateOf(0) }
    //unchecking items lowers the count, so lower the dismissed mark too
    LaunchedEffect(boughtByMeCount) {
        if (boughtByMeCount < dismissedAtCount) dismissedAtCount = boughtByMeCount
    }
    val showBar = boughtByMeCount > 0 && boughtByMeCount > dismissedAtCount

    val submit = { if (text.isNotBlank()) onAdd(text) { text = "" } }

    //pop up if user decided to edit an entry
    editing?.let { item ->
        var newName by remember(item.id) { mutableStateOf(item.description) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Edit item") },
            text = {
                OutlinedTextField(newName, { newName = it }, singleLine = true, label = { Text("Item") })
            },
            confirmButton = {
                TextButton({ onRename(item, newName); editing = null }, enabled = newName.isNotBlank()) { Text("Save") }
            },
            dismissButton = { TextButton({ editing = null }) { Text("Cancel") } }
        )
    }

    //delete confirmation
    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete item?") },
            text = { Text("\"${item.description}\" will be removed for everyone in the household.") },
            confirmButton = {
                TextButton({ onDelete(item.id); pendingDelete = null }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton({ pendingDelete = null }) { Text("Cancel") } }
        )
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text(
            "Groceries",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 8.dp, top = 24.dp, bottom = 8.dp)
        )

        //add bar: type an item, then tap + or the keyboard's Done to add it; the keyboard stays open for the next one
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Add an item") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = submit, enabled = text.isNotBlank()) {
                Icon(Icons.Default.Add, contentDescription = "Add item")
            }
        }
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(8.dp))

        //the list takes the space between the add bar and the bottom bar
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (items.isEmpty()) {
                Text("Nothing on the list yet", Modifier.align(Alignment.Center))
            } else {
                //items not checked off
                //partition separates the items in the list
                //into two groups based on the condition !it.bought
                val (toBuy, bought) = items.partition { !it.bought }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(toBuy, key = { it.id }) { item ->
                        GroceryRow(item, myUid, onToggleBought, onEdit = { editing = it }, onDelete = { pendingDelete = it })
                    }
                    //list items that are bought
                    if (bought.isNotEmpty()) {
                        item {
                            Text(
                                "Bought",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 4.dp)
                            )
                        }
                        items(bought, key = { it.id }) { item ->
                            GroceryRow(item, myUid, onToggleBought, onEdit = { editing = it }, onDelete = { pendingDelete = it })
                        }
                    }
                }
            }
        }
        AnimatedVisibility(visible = showBar) {
            AddToExpenseBar(
                count = boughtByMeCount,
                onAddToExpense = onAddToExpense,
                onDismiss = { dismissedAtCount = boughtByMeCount }
            )
        }
    }
}

@Composable
private fun GroceryRow(
    item: GroceryItem,
    myUid: String,
    onToggleBought: (GroceryItem) -> Unit,
    onEdit: (GroceryItem) -> Unit,
    onDelete: (GroceryItem) -> Unit
) {
    //anyone can check an item off; only the person who bought it can uncheck it
    val canToggle = !item.bought || item.paidByUid == myUid
    //only the person who added it can edit/delete; editing only before it's bought
    val isMine = item.addedByUid == myUid
    val canEdit = isMine && !item.bought

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = item.bought, onCheckedChange = { onToggleBought(item) }, enabled = canToggle)
        Column(Modifier.weight(1f)) {
            Text(
                item.description,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (item.bought) TextDecoration.LineThrough else null
            )
            val subtitle = if (item.bought) "Bought by ${item.paidByName}" else "Added by ${item.addedByName}"
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        if (canEdit) {
            IconButton({ onEdit(item) }) { Icon(Icons.Default.Edit, contentDescription = "Edit ${item.description}") }
        }
        if (isMine) {
            IconButton({ onDelete(item) }) { Icon(Icons.Default.Delete, contentDescription = "Delete ${item.description}") }
        }
    }
}

@Composable
private fun AddToExpenseBar(
    count: Int,
    onAddToExpense: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 3.dp
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (count == 1) "1 bought item" else "$count bought items",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Button(onClick = onAddToExpense) { Text("Add to expense") }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Dismiss")
            }
        }
    }
}
