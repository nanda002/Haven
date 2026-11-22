package com.app.haven.viewmodel.groceryitem

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.app.haven.actions.GroceryItemActions
import com.app.haven.actions.HouseholdActions
import com.app.haven.actions.friendlyMessage
import com.app.haven.appContainer
import com.app.haven.data.api.FoodSuggestionActions
import com.app.haven.data.api.ProductLookupActions
import com.app.haven.domain.grocerylist.GroceryItem
import com.app.haven.domain.grocerylist.ScannedItem
import com.app.haven.domain.grocerylist.historySuggestions
import com.app.haven.domain.grocerylist.onList
import com.app.haven.domain.grocerylist.validateGroceryItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class Suggestion(val name: String, val fromHistory: Boolean)

//state + events for MainGroceryItemScreen
class MainGroceryItemViewModel(
    private val groceries: GroceryItemActions,
    households: HouseholdActions,
    private val householdId: String,
    private val myUid: String,
    private val products: ProductLookupActions,
    private val foodSuggestions: FoodSuggestionActions
) : ViewModel() {

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // ---- grocery items ----

    //every item the household has added, including bought/cleared/added to an expense; used for history suggestions
    private val allItems: StateFlow<List<GroceryItem>> = groceries.getItems(householdId)
        .catch { e ->
            _error.value = e.friendlyMessage()
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    //only what's still on the list and are not bought yet
    val items: StateFlow<List<GroceryItem>> = allItems
        .map { it.onList() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    //getting member's name
    private val myName: StateFlow<String> = households.observeMembers(householdId)
        .map { list -> list.firstOrNull { it.uid == myUid }?.displayName.orEmpty() }
        .catch { emit("") }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    // ---- search suggestions ----

    private val query = MutableStateFlow("")

    //the pipeline for when the text from the user changes as they type
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private val usdaResults: Flow<List<String>> = query
        .map { it.trim() }
        //waiting interval for user to finish typing
        .debounce(350)
        .distinctUntilChanged()
        .mapLatest { q ->
            if (q.length < 2) emptyList()
            else foodSuggestions.lookUp(q).getOrDefault(emptyList())
                .filter { name -> name.split(" ").any { it.startsWith(q, ignoreCase = true) } }
                .distinctBy { it.lowercase() }
                .sortedBy { it.length }
        }
        .onStart { emit(emptyList()) }

    //history matches first then USDA results
    val suggestions: StateFlow<List<Suggestion>> = combine(query, allItems, usdaResults)
    { q, history, usda ->
        val past = historySuggestions(history, q)
        val pastLower = past.map { it.lowercase() }.toSet()
        val fromUsda = usda.filter { it.lowercase() !in pastLower }
        (past.map { Suggestion(it, fromHistory = true) } + fromUsda.map { Suggestion(it, fromHistory = false) }).take(5)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ---- barcode scanning ----
    private val _scan = MutableStateFlow<ScanState>(ScanState.Hidden)
    val scan: StateFlow<ScanState> = _scan.asStateFlow()
    private var scanJob: Job? = null

    // ---- events from the screen ----

    //called on every keystroke
    fun onQueryChanged(text: String) { query.value = text }

    fun onAdd(name: String, onAdded: () -> Unit) {
        validateGroceryItem(name)?.let { _error.value = it; return }
        val item = GroceryItem(
            description = name.trim(),
            addedByUid = myUid,
            addedByName = myName.value
        )
        viewModelScope.launch {
            groceries.addItem(householdId, item)
                .onSuccess { _error.value = null; onAdded() }
                .onFailure { _error.value = it.friendlyMessage() }
        }
    }

    //only the person who added it, and only before it's bought (the screen hides Edit otherwise too)
    fun onRename(item: GroceryItem, newName: String) {
        if (item.addedByUid != myUid || item.bought) return
        validateGroceryItem(newName)?.let { _error.value = it; return }
        viewModelScope.launch {
            groceries.updateItem(householdId, item.copy(description = newName.trim()))
                .onFailure { _error.value = it.friendlyMessage() }
        }
    }

    //only the person who added it, and only before it's bought (the screen hides Delete otherwise too)
    fun onDelete(itemId: String) {
        val item = items.value.firstOrNull { it.id == itemId } ?: return
        if (item.addedByUid != myUid || item.bought) return
        viewModelScope.launch {
            groceries.deleteItem(householdId, itemId)
                .onFailure { _error.value = it.friendlyMessage() }
        }
    }

    //anyone can check an item off; only the buyer can uncheck it
    fun onToggleBought(item: GroceryItem) {
        if (item.bought && item.paidByUid != myUid) return
        viewModelScope.launch {
            groceries.setBought(householdId, item.id, bought = !item.bought, buyerUid = myUid, buyerName = myName.value)
                .onFailure { _error.value = it.friendlyMessage() }
        }
    }

    fun onDeleteFromBought() {
        val ids = items.value.filter { it.bought && it.paidByUid == myUid }.map { it.id }
        viewModelScope.launch {
            groceries.markClearedFromList(householdId, ids)
                .onFailure { _error.value = it.friendlyMessage() }
        }
    }

    fun onBarcodeScanned(code: String) {
        scanJob?.cancel()
        _scan.value = ScanState.Loading(code)
        scanJob = viewModelScope.launch {
            products.lookUp(code)
                .onSuccess { item ->
                    _scan.value = if (item == null) ScanState.NotFound(code) else ScanState.Found(item)
                }
                .onFailure { _scan.value = ScanState.Error(code, it.friendlyMessage()) }
        }
    }

    fun onRetryScan() {
        val code = (_scan.value as? ScanState.Error)?.code ?: return
        onBarcodeScanned(code)
    }

    fun onScanDismissed() {
        scanJob?.cancel()
        _scan.value = ScanState.Hidden
    }

    companion object {
        fun factory(householdId: String, myUid: String) = viewModelFactory {
            initializer {
                val c = appContainer()
                MainGroceryItemViewModel(c.groceryItemRepository, c.householdActions, householdId, myUid, c.productLookupRepository, c.foodSuggestionRepository)
            }
        }
    }
}

sealed interface ScanState {
    data object Hidden : ScanState
    data class Loading(val code: String) : ScanState
    data class Found(val item: ScannedItem) : ScanState
    data class NotFound(val code: String) : ScanState
    data class Error(val code: String, val message: String) : ScanState
}
