package com.app.haven.viewmodel.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.app.haven.actions.grocery.GroceryItemActions
import com.app.haven.appContainer
import com.app.haven.domain.grocerylist.GroceryItem
import com.app.haven.domain.grocerylist.onList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class HomeGroceryViewModel(
    groceryitems: GroceryItemActions,
    householdId: String,
) : ViewModel() {
    val unboughtItems: StateFlow<List<GroceryItem>> = groceryitems.getItems(householdId)
        .map { list ->
            list.onList().filter { !it.bought }
//                .sortedBy { it.dueEpochDay }
        }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    companion object {
        fun factory(householdId: String, myUid: String) = viewModelFactory {
            initializer {
                HomeGroceryViewModel(appContainer().groceryItemRepository, householdId)
            }
        }
    }
}