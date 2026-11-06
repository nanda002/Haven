package com.app.haven.viewmodel.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.haven.actions.HouseholdActions
import com.app.haven.actions.friendlyMessage
import com.app.haven.data.model.Household
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.update

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

    private val _leaving = MutableStateFlow(false)
    val leaving: StateFlow<Boolean> = _leaving.asStateFlow()

    private val _leaveError = MutableStateFlow<String?>(null)
    val leaveError: StateFlow<String?> = _leaveError.asStateFlow()

    private val _regenerating = MutableStateFlow(false)
    val regenerating: StateFlow<Boolean> = _regenerating.asStateFlow()

    private val _inviteError = MutableStateFlow<String?>(null)
    val inviteError: StateFlow<String?> = _inviteError.asStateFlow()

    fun leaveHousehold(onLeft: () -> Unit) {
        viewModelScope.launch {
            _leaving.value = true
            _leaveError.value = null
            try {
                households.leaveHousehold()
                onLeft()
            } catch (e: Exception) {
                _leaveError.value = e.friendlyMessage()
            } finally {
                _leaving.value = false
            }
        }
    }

    fun regenerateInviteCode() {
        viewModelScope.launch {
            _regenerating.value = true
            _inviteError.value = null
            try {
                val code = households.regenerateInviteCode()
                // update the code on screen without reloading everything
                _ui.update { s -> s.copy(household = s.household?.copy(inviteCode = code)) }
            } catch (e: Exception) {
                _inviteError.value = e.friendlyMessage()
            } finally {
                _regenerating.value = false
            }
        }
    }
}