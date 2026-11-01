// HomeViewModel.kt
package com.app.haven.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.haven.actions.HouseholdActions
import com.app.haven.actions.friendlyMessage
import com.app.haven.data.model.Household
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val household: Household? = null,
    val loading: Boolean = true,
    val error: String? = null
)

class HomeViewModel(
    private val households: HouseholdActions = HouseholdActions()
) : ViewModel() {
    private val _ui = MutableStateFlow(HomeUiState())
    val ui: StateFlow<HomeUiState> = _ui.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _ui.value = HomeUiState(loading = true)   // clears any previous user's household
            _ui.value = try {
                HomeUiState(household = households.loadCurrentHousehold(), loading = false)
            } catch (e: Exception) {
                HomeUiState(loading = false, error = e.friendlyMessage())
            }
        }
    }
}