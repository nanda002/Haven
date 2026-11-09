package com.app.haven.viewmodel.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.app.haven.actions.ChoreActions
import com.app.haven.appContainer
import com.app.haven.domain.chore.Chore
import com.app.haven.domain.chore.todayEpochDay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

// My chores that need doing now: not done, assigned to me, due today or overdue.
class HomeChoresViewModel(
    chores: ChoreActions,
    householdId: String,
    myUid: String
) : ViewModel() {

    val dueChores: StateFlow<List<Chore>> = chores.getChores(householdId)
        .map { list ->
            val today = todayEpochDay()
            list.filter { !it.done && it.assigneeUid == myUid && it.dueEpochDay <= today }
                .sortedBy { it.dueEpochDay }
        }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    companion object {
        fun factory(householdId: String, myUid: String) = viewModelFactory {
            initializer {
                HomeChoresViewModel(appContainer().choreRepository, householdId, myUid)
            }
        }
    }
}