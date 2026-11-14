package com.app.haven.viewmodel.groceryitem

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.app.haven.actions.GroceryItemActions
import com.app.haven.actions.GroceryItemRepository
import com.app.haven.actions.HouseholdActions
import com.app.haven.actions.friendlyMessage
import com.app.haven.appContainer
import com.app.haven.domain.grocerylist.GroceryItem
import com.app.haven.domain.grocerylist.validateGroceryItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

//state + events for MainGroceryItemScreen
class MainGroceryItemViewModel(
    private val groceries: GroceryItemActions,
    households: HouseholdActions,
    private val householdId: String,
    private val myUid: String
) : ViewModel() {

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val items: StateFlow<List<GroceryItem>> = groceries.getItems(householdId)
        .catch { e ->
            _error.value = e.friendlyMessage()
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val myName: StateFlow<String> = households.observeMembers(householdId)
        .map { list -> list.firstOrNull { it.uid == myUid }?.displayName.orEmpty() }
        .catch { emit("") }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    // ---- events from the screen ----

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

    //only the person who added it can delete it
    fun onDelete(itemId: String) {
        val item = items.value.firstOrNull { it.id == itemId } ?: return
        if (item.addedByUid != myUid) return
        viewModelScope.launch {
            groceries.deleteItem(householdId, itemId)
                .onFailure { _error.value = it.friendlyMessage() }
        }
    }

    //anyone can check an item off; only the buyer can uncheck it
    //checkbox: marks this item bought by me, or unbought again (does not add an expense)
    fun onToggleBought(item: GroceryItem) {
        if (item.bought && item.paidByUid != myUid) return
        viewModelScope.launch {
            //flip it: not bought -> bought by me, bought by me -> not bought
            groceries.setBought(householdId, item.id, bought = !item.bought, buyerUid = myUid, buyerName = myName.value)
                .onFailure { _error.value = it.friendlyMessage() }
        }
    }

    fun onErrorShown() { _error.value = null }

    companion object {
        fun factory(householdId: String, myUid: String) = viewModelFactory {
            initializer {
                val c = appContainer()
                //TODO: move to AppContainer (val groceryItemRepository) like the other repositories
                MainGroceryItemViewModel(GroceryItemRepository(), c.householdActions, householdId, myUid)
            }
        }
    }
}
