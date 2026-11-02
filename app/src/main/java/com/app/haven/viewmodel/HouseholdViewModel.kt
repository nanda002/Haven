package com.app.haven.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.haven.actions.HouseholdActions
import com.app.haven.actions.friendlyMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HouseholdSetupUiState(val loading: Boolean = false, val error: String? = null)

class HouseholdViewModel(
    private val households: HouseholdActions = HouseholdActions()
) : ViewModel() {
    private val _ui = MutableStateFlow(HouseholdSetupUiState())
    val ui: StateFlow<HouseholdSetupUiState> = _ui.asStateFlow()

    fun clearError() { _ui.value = HouseholdSetupUiState() }

    fun create(name: String, onDone: () -> Unit) {
        if (name.isBlank()) { _ui.value = HouseholdSetupUiState(error = "Enter a household name"); return }
        run(onDone) { households.createHousehold(name.trim()) }
    }

    fun join(code: String, onDone: () -> Unit) {
        if (code.isBlank()) { _ui.value = HouseholdSetupUiState(error = "Enter an invite code"); return }
        run(onDone) { households.joinHousehold(code) }
    }

    private fun run(onDone: () -> Unit, block: suspend () -> Unit) {
        viewModelScope.launch {
            _ui.value = HouseholdSetupUiState(loading = true)
            try {
                block()
                _ui.value = HouseholdSetupUiState()
                onDone()
            } catch (e: Exception) {
                _ui.value = HouseholdSetupUiState(error = e.friendlyMessage())
            }
        }
    }
}