package com.app.haven.viewmodel.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.app.haven.actions.chores.ChoreActions
import com.app.haven.appContainer
import com.app.haven.domain.chore.Chore
import com.app.haven.domain.chore.todayEpochDay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

// My chores: not done, assigned to me, overdue / due today / due within the next 7 days.
class HomeChoresViewModel(
    chores: ChoreActions,
    householdId: String,
    myUid: String
) : ViewModel() {

    val myChores: StateFlow<List<Chore>> = chores.getChores(householdId, myUid)
        .map { list ->
            val today = todayEpochDay()
            list.filter { !it.done && it.assigneeUid == myUid && it.dueEpochDay <= today + 7 }
                .sortedBy { it.dueEpochDay }
        }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    companion object {
        fun factory(householdId: String, myUid: String) = viewModelFactory {
            initializer {
                HomeChoresViewModel(appContainer().choreRepository, householdId, myUid)
            }
        }
    }
}