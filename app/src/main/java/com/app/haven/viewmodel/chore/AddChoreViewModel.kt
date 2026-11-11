package com.app.haven.viewmodel.chore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.app.haven.actions.ChoreActions
import com.app.haven.actions.HouseholdActions
import com.app.haven.actions.friendlyMessage
import com.app.haven.appContainer
import com.app.haven.data.model.Member
import com.app.haven.domain.chore.Chore
import com.app.haven.domain.chore.todayEpochDay
import com.app.haven.domain.chore.validateChore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AddChoreViewModel(
    private val chores: ChoreActions,
    households: HouseholdActions,
    private val householdId: String
) : ViewModel() {

    val members: StateFlow<List<Member>> = households.observeMembers(householdId)
        .map { list -> list.sortedBy { it.displayName.lowercase() } }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun save(title: String, assignee: Member?, dueInDays: Int, repeatDays: Int, onDone: () -> Unit) {
        validateChore(title, assignee?.uid)?.let { _error.value = it; return }
        val chore = Chore(
            title = title.trim(),
            assigneeUid = assignee!!.uid,
            assigneeName = assignee.displayName,
            dueEpochDay = todayEpochDay() + dueInDays,
            repeatDays = repeatDays
        )
        viewModelScope.launch {
            chores.addChore(householdId, chore)
                .onSuccess { onDone() }
                .onFailure { _error.value = it.friendlyMessage() }
        }
    }

    companion object {
        fun factory(householdId: String) = viewModelFactory {
            initializer {
                val c = appContainer()
                AddChoreViewModel(c.choreRepository, c.householdActions, householdId)
            }
        }
    }
}