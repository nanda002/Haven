package com.app.haven.view.groceryItemUI

import android.graphics.fonts.FontStyle
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
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
import com.app.haven.domain.InputLimits
import com.app.haven.domain.grocerylist.GroceryItem
import com.app.haven.view.common.LimitedTextField
import com.app.haven.domain.grocerylist.listName
import com.app.haven.viewmodel.groceryitem.MainGroceryItemViewModel
import com.app.haven.viewmodel.groceryitem.ScanState
import com.app.haven.viewmodel.groceryitem.Suggestion

@Composable
fun GroceryListRoute(
    householdId: String,
    myUid: String,
    //bottom bar's "Add to expense": opens the Enter prices screen
    onAddToExpense: () -> Unit = {},
    //"Show Bought Items": opens the bought history
    onShowBought: () -> Unit = {},
    onScan: () -> Unit = {},
    scannedCode: String? = null,
    onScannedCodeHandled: () -> Unit = {},
    vm: MainGroceryItemViewModel = viewModel(factory = MainGroceryItemViewModel.factory(householdId, myUid))
) {
    //if scanned code is not null, pass the value to vm
    LaunchedEffect(scannedCode) {
        scannedCode?.let { code ->
            vm.onBarcodeScanned(code)
            onScannedCodeHandled()
        }
    }
    //viewmodel states, collectAsStateWithLifeCycle() is used for connecting the
    //viewmodel flow to the UI, starts collection while lifecycle is active
    val items by vm.items.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val scan by vm.scan.collectAsStateWithLifecycle()
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    //passing the viewmodel functions as callbacks
    MainGroceryItemScreen(
        myUid = myUid,
        items = items,
        error = error,
        onAdd = vm::onAdd,
        onToggleBought = vm::onToggleBought,
        onRename = vm::onRename,
        onDelete = vm::onDelete,
        onAddToExpense = onAddToExpense,
        onDeleteFromBought = vm::onDeleteFromBought,
        onShowBought = onShowBought,
        onScan = onScan,
        scan = scan,
        onRetryScan = vm::onRetryScan,
        onScanDismissed = vm::onScanDismissed,
        suggestions = suggestions,
        onQueryChanged = vm::onQueryChanged
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
    onRename: (GroceryItem, String, String) -> Unit,
    onDelete: (String) -> Unit,
    //bottom bar's "Add to expense"
    onAddToExpense: () -> Unit = {},
    onDeleteFromBought: () -> Unit = {},
    //"Show Bought Items" button
    onShowBought: () -> Unit = {},
    onScan: () -> Unit = {},
    scan: ScanState = ScanState.Hidden,
    onRetryScan: () -> Unit = {},
    onScanDismissed: () -> Unit = {},
    suggestions: List<Suggestion> = emptyList(),
    onQueryChanged: (String) -> Unit = {}
) {
    //what the user is typing in the add bar (survives rotation)
    var text by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<GroceryItem?>(null) }
    var pendingDelete by remember { mutableStateOf<GroceryItem?>(null) }

    //bottom bar: items this user bought that are still on the list
    val boughtByMeCount = items.count { it.bought && it.paidByUid == myUid }
    var dismissedAtCount by rememberSaveable { mutableIntStateOf(0) }
    //unchecking items lowers the count, so lower the dismissed mark too
    LaunchedEffect(boughtByMeCount) {
        if (boughtByMeCount < dismissedAtCount) dismissedAtCount = boughtByMeCount
    }
    val showBar = boughtByMeCount > 0 && boughtByMeCount > dismissedAtCount

    val submit = { if (text.isNotBlank()) onAdd(text) { text = ""; onQueryChanged("") } }

    //pop up if user decided to edit an entry
    editing?.let { item ->
        var newName by remember(item.id) { mutableStateOf(item.description) }
        var newNote by remember(item.id) { mutableStateOf(item.note) }

        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Edit item") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    //for the item name
                    LimitedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        maxLength = InputLimits.ITEM,
                        label = "Item",
                        shape = RoundedCornerShape(24.dp)
                    )
                    //for the item note
                    LimitedTextField(
                        value = newNote,
                        onValueChange = { newNote = it },
                        maxLength = InputLimits.ITEM_NOTE,
                        label = "Note (Optional)",
                        shape = RoundedCornerShape(24.dp),
                        singleLine = false
                    )
                }
            },
            confirmButton = {
                TextButton({ onRename(item, newName, newNote); editing = null }, enabled = newName.isNotBlank()) { Text("Save") }
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

    ScanProductDialog(
        scan = scan,
        onAddToList = { item -> onAdd(item.listName()) { onScanDismissed() } },
        onRetry = onRetryScan,
        onDismiss = onScanDismissed
    )

    //Building the screen
    Column(Modifier
        .fillMaxSize()
        .padding(horizontal = 16.dp)) {
        //title + button to the bought history (items grouped by the day they were bought)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, top = 24.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Groceries", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            //show bought items button to show bought items history
            OutlinedButton(onClick = onShowBought) { Text("Show Bought Items") }
        }

        //add bar: type an item, then tap +
        Row(verticalAlignment = Alignment.CenterVertically) {
            LimitedTextField(
                value = text,
                onValueChange = { text = it; onQueryChanged(it) },
                maxLength = InputLimits.ITEM,
                label = "Add an item",
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp)
            )
            //barcode scanner icon
            IconButton(onClick = onScan) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan a barcode")
            }
            //'+' button icon
            IconButton(onClick = submit, enabled = text.isNotBlank()) {
                Icon(Icons.Default.Add, contentDescription = "Add item")
            }
        }
        //showing the suggestions below the add bar
        if (suggestions.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 96.dp),
                shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp),
                tonalElevation = 3.dp,
                shadowElevation = 3.dp
            ) {
                Column {
                    suggestions.forEach { suggestion ->
                        ListItem(
                            headlineContent = { Text(suggestion.name) },
                            leadingContent = {
                                Icon(
                                    if (suggestion.fromHistory) Icons.Default.History else Icons.Default.Search,
                                    contentDescription = null
                                )
                            },
                            modifier = Modifier.clickable { onAdd(suggestion.name) { text = ""; onQueryChanged("") } }
                        )
                    }
                }
            }
        }
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(8.dp))

        //the list of bought items takes the space between the add bar and the bottom bar
        Box(Modifier
            .weight(1f)
            .fillMaxWidth()) {
            if (items.isEmpty()) {
                Text("Nothing on the list yet", Modifier.align(Alignment.Center))
            } else {
                //items not checked off
                //partition separates the items in the list
                //into two groups based on the condition !it.bought
                val (toBuy, bought) = items.partition { !it.bought }

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    item {
                        GrocerySectionCard(
                            title = "To Buy",
                            color = MaterialTheme.colorScheme.surfaceContainerLow
                        ) {
                            if (toBuy.isEmpty()) {
                                Text(
                                    "No pending items to buy!",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
                                )
                            } else {
                                toBuy.forEach { item ->
                                    key(item.id) {
                                        GroceryRow(item, myUid, onToggleBought, onEdit = { editing = it }, onDelete = { pendingDelete = it })
                                    }
                                }
                            }
                        }
                    }

                    //list items that are bought
                    if (bought.isNotEmpty()) {
                        item {
                            GrocerySectionCard(
                                title = "Bought",
                                color = MaterialTheme.colorScheme.surfaceContainerHighest
                            ) {
                                bought.forEach { item ->
                                    key(item.id) {
                                        //for bought section, the last three parameters are not used
                                        //but kept for format of grocery row
                                        GroceryRow(item, myUid, onToggleBought, onEdit = {}, onDelete = {})
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        //bar shown at the bottom of the screen
        //using animated visibility for smooth in n out transitions
        AnimatedVisibility(visible = showBar) {
            AddToExpenseBar(
                count = boughtByMeCount,
                onAddToExpense = onAddToExpense,
                onClear = onDeleteFromBought,
                onDismiss = { dismissedAtCount = boughtByMeCount }
            )
        }
    }
}

@Composable
private fun GrocerySectionCard(
    title: String,
    color: androidx.compose.ui.graphics.Color,
    content: @Composable ColumnScope.() -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = color),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp)
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 12.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
            )
            content()
        }
    }
}

@Composable
fun GroceryRow(
    item: GroceryItem,
    myUid: String,
    onToggleBought: (GroceryItem) -> Unit,
    onEdit: (GroceryItem) -> Unit,
    onDelete: (GroceryItem) -> Unit
) {
    //anyone can check an item off; only the person who bought it can uncheck it
    val canToggle = !item.bought || item.paidByUid == myUid
    //only the person who added it can edit/delete, and only before it's bought
    //(once someone buys it, it's theirs to uncheck, so the adder can't remove it from under them)
    val canEdit = item.addedByUid == myUid && !item.bought

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = item.bought, onCheckedChange = { onToggleBought(item) }, enabled = canToggle)
        Column(Modifier.weight(1f)) {
            Text(
                item.description,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (item.bought) TextDecoration.LineThrough else null
            )
            if (item.note.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
                ) {
                    Row(
                        Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Notes,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            item.note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
            val subtitle = if (item.bought) "Bought by ${item.paidByName}" else "Added by ${item.addedByName}"
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        if (canEdit) {
            IconButton({ onEdit(item) }) { Icon(Icons.Default.Edit, contentDescription = "Edit ${item.description}") }
            IconButton({ onDelete(item) }) { Icon(Icons.Default.Delete, contentDescription = "Delete ${item.description}") }
        }
    }
}

@Composable
private fun AddToExpenseBar(
    count: Int,
    onAddToExpense: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
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
            TextButton(onClick = onClear) { Text("Clear items") }
            Button(onClick = onAddToExpense) { Text("Add to expense") }
//            IconButton(onClick = onDismiss) {
//                Icon(Icons.Default.Close, contentDescription = "Dismiss")
//            }
        }
    }
}
