package com.app.haven.viewmodel.chore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.app.haven.actions.ChoreActions
import com.app.haven.actions.HouseholdActions
import com.app.haven.actions.friendlyMessage
import com.app.haven.appContainer
import com.app.haven.domain.chore.Assignee
import com.app.haven.domain.chore.Chore
import com.app.haven.domain.chore.completeChore
import com.app.haven.domain.chore.todayEpochDay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ChoresUiState {
    data object Loading : ChoresUiState
    data object Empty : ChoresUiState
    data class Success(val chores: List<Chore>, val today: Long) : ChoresUiState
    data class Error(val message: String) : ChoresUiState
}

class ChoresViewModel(
    private val chores: ChoreActions,
    households: HouseholdActions,
    private val householdId: String,
    private val myUid: String
) : ViewModel() {

    // current members only: these are who the round robin rotates through
    private val members: StateFlow<List<Assignee>> = households.observeMembers(householdId)
        .map { list -> list.map { Assignee(it.uid, it.displayName) } }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val uiState: StateFlow<ChoresUiState> = chores.getChores(householdId)
        .map<List<Chore>, ChoresUiState> { list ->
            if (list.isEmpty()) ChoresUiState.Empty
            else ChoresUiState.Success(list.sortedWith(compareBy({ it.done }, { it.dueEpochDay })), todayEpochDay())
        }
        .catch { emit(ChoresUiState.Error(it.friendlyMessage())) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChoresUiState.Loading)

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    fun onErrorShown() { _error.value = null }

    fun onToggleDone(chore: Chore) {
        val updated = if (chore.done) chore.copy(done = false, completedByUid = null)
        else completeChore(chore, members.value, myUid, todayEpochDay())
        viewModelScope.launch {
            chores.updateChore(householdId, updated).onFailure { _error.value = it.friendlyMessage() }
        }
    }

    fun onDelete(choreId: String) {
        viewModelScope.launch {
            chores.deleteChore(householdId, choreId).onFailure { _error.value = it.friendlyMessage() }
        }
    }

    companion object {
        fun factory(householdId: String, myUid: String) = viewModelFactory {
            initializer {
                val c = appContainer()
                ChoresViewModel(c.choreRepository, c.householdActions, householdId, myUid)
            }
        }
    }
}