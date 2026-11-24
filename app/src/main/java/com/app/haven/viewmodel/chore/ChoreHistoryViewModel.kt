package com.app.haven.viewmodel.chore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.app.haven.actions.chores.ChoreActions
import com.app.haven.appContainer
import com.app.haven.domain.chore.ChoreCompletion
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface ChoreHistoryState {
    data object Loading : ChoreHistoryState
    data object Error : ChoreHistoryState
    data class Loaded(val items: List<ChoreCompletion>) : ChoreHistoryState
}

// Past chores for the household, newest first. Reads the same records that contribution is calculated from.
class ChoreHistoryViewModel(chores: ChoreActions, householdId: String) : ViewModel() {

    val state: StateFlow<ChoreHistoryState> = chores.observeCompletions(householdId)
        .map<List<ChoreCompletion>, ChoreHistoryState> { ChoreHistoryState.Loaded(it) }
        .catch { emit(ChoreHistoryState.Error) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChoreHistoryState.Loading)

    companion object {
        fun factory(householdId: String) = viewModelFactory {
            initializer { ChoreHistoryViewModel(appContainer().choreRepository, householdId) }
        }
    }
}