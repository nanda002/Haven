package com.app.haven.viewmodel.groceryitem

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.app.haven.actions.GroceryItemActions
import com.app.haven.actions.friendlyMessage
import com.app.haven.appContainer
import com.app.haven.domain.grocerylist.BoughtDay
import com.app.haven.domain.grocerylist.groupBoughtByDay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class BoughtHistoryUiState(
    val days: List<BoughtDay> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null
)

//"Show Bought Items": everything checked off in the household, grouped by the day it was bought
//(includes items already added to an expense, which are no longer on the list)
class BoughtHistoryViewModel(groceries: GroceryItemActions, householdId: String) : ViewModel() {

    val uiState: StateFlow<BoughtHistoryUiState> = groceries.getItems(householdId)
        .map { BoughtHistoryUiState(days = groupBoughtByDay(it), loading = false) }
        .catch { emit(BoughtHistoryUiState(loading = false, error = it.friendlyMessage())) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BoughtHistoryUiState())

    companion object {
        fun factory(householdId: String) = viewModelFactory {
            initializer { BoughtHistoryViewModel(appContainer().groceryItemRepository, householdId) }
        }
    }
}
