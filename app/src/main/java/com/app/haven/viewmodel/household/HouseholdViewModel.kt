package com.app.haven.viewmodel.household

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.haven.actions.HouseholdActions
import com.app.haven.actions.UserActions
import com.app.haven.actions.friendlyMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HouseholdSetupUiState(val loading: Boolean = false, val error: String? = null)

class HouseholdViewModel(
    private val households: HouseholdActions = HouseholdActions(),
    private val users: UserActions = UserActions()
) : ViewModel() {
    private val _ui = MutableStateFlow(HouseholdSetupUiState())
    val ui: StateFlow<HouseholdSetupUiState> = _ui.asStateFlow()

    //shown as "Logged in as …" on the setup screen; null until loaded (or if it can't be loaded)
    private val _username = MutableStateFlow<String?>(null)
    val username: StateFlow<String?> = _username.asStateFlow()

    //called each time the setup screen appears, not once in init: this ViewModel belongs to the Activity,
    //so after logging out and signing in as someone else it's reused and would still show the old name
    fun loadUsername() {
        //whenever we want something using the repo functions, we call viewModelScope
        viewModelScope.launch {
            _username.value = null   //don't show the previous user's name while loading
            _username.value = runCatching { users.loadUser()?.username }.getOrNull()?.takeIf { it.isNotBlank() }
        }
    }

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