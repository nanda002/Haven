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
import com.app.haven.domain.chore.validateChore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.app.haven.domain.chore.ChoreCategory
import com.app.haven.domain.chore.normalizeNotes
import com.app.haven.domain.chore.resolveEndEpochDay
import com.app.haven.domain.chore.validateEndDate
import com.app.haven.domain.chore.isAllowedOccurrence
import com.app.haven.domain.chore.nextDueDay


// Used for both "Add chore" (choreId = null) and "Edit chore"
class AddChoreViewModel(
    private val chores: ChoreActions,
    households: HouseholdActions,
    private val householdId: String,
    private val choreId: String?
) : ViewModel() {

    val members: StateFlow<List<Member>> = households.observeMembers(householdId)
        .map { list -> list.sortedBy { it.displayName.lowercase() } }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // the chore being edited (null when adding, or when it was deleted meanwhile)
    private val _existing = MutableStateFlow<Chore?>(null)
    val existing: StateFlow<Chore?> = _existing.asStateFlow()

    private val _loading = MutableStateFlow(choreId != null)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        if (choreId != null) {
            viewModelScope.launch {
                _existing.value = runCatching {
                    chores.getChores(householdId).first().firstOrNull { it.id == choreId }
                }.getOrNull()
                _loading.value = false
            }
        }
    }

    // durationWeeks: how long a repeating chore keeps going (0 = forever).
    // null = leave the existing end date alone (editing without touching that setting).
    fun save(
        title: String,
        category: String?,
        assignee: Member?,
        dueEpochDay: Long,
        repeatDays: Int,
        rotate: Boolean,
        endEpochDay: Long,   // 0 = no end date
        notes: String,
        onDone: () -> Unit
    ) {
        validateChore(title, assignee?.uid, category)?.let { _error.value = it; return }
        val base = _existing.value
        validateEndDate(dueEpochDay, repeatDays, endEpochDay)?.let { _error.value = it; return }
        val end = resolveEndEpochDay(repeatDays, endEpochDay)
        // keep a stored next turn only if its date is still allowed by the (possibly new) end date,
        // otherwise a shortened end date would leave a stale "Next: ..." behind
        val keepNext = base != null && repeatDays > 0 && isAllowedOccurrence(nextDueDay(dueEpochDay, repeatDays), end)
        val chore = Chore(
            id = base?.id ?: "",
            title = title.trim(),
            category = ChoreCategory.fromKey(category).key,
            notes = normalizeNotes(notes),
            nextAssigneeUid = if (keepNext) base?.nextAssigneeUid.orEmpty() else "",
            nextAssigneeName = if (keepNext) base?.nextAssigneeName.orEmpty() else "",
            assigneeUid = assignee!!.uid,
            assigneeName = assignee.displayName,
            dueEpochDay = dueEpochDay,
            repeatDays = repeatDays,
            rotate = rotate,
            endEpochDay = end,
            done = base?.done ?: false,
            completedByUid = base?.completedByUid,
            completedByName = base?.completedByName,
            lastCompletedEpochDay = base?.lastCompletedEpochDay ?: 0L
        )
        viewModelScope.launch {
            val result = if (base == null) chores.addChore(householdId, chore)
            else chores.updateChore(householdId, chore)
            result.onSuccess { onDone() }.onFailure { _error.value = it.friendlyMessage() }
        }
    }

    companion object {
        fun factory(householdId: String, choreId: String?) = viewModelFactory {
            initializer {
                val c = appContainer()
                AddChoreViewModel(c.choreRepository, c.householdActions, householdId, choreId)
            }
        }
    }
}