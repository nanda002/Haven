package com.app.haven.viewmodel.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.app.haven.actions.HouseholdActions
import com.app.haven.appContainer
import com.app.haven.data.model.Member
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

// Current + past members for the Home members popup
class HouseholdMembersViewModel(
    households: HouseholdActions,
    householdId: String
) : ViewModel() {

    val members: StateFlow<List<Member>> = households.observeMembers(householdId)
        .map { list -> list.sortedBy { it.displayName.lowercase() } }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pastMembers: StateFlow<List<Member>> = households.observePastMembers(householdId)
        .map { list -> list.sortedBy { it.displayName.lowercase() } }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    companion object {
        fun factory(householdId: String) = viewModelFactory {
            initializer {
                HouseholdMembersViewModel(appContainer().householdActions, householdId)
            }
        }
    }
}