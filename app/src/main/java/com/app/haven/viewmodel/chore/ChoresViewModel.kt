package com.app.haven.viewmodel.chore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.app.haven.actions.chores.ChoreActions
import com.app.haven.actions.HouseholdActions
import com.app.haven.actions.friendlyMessage
import com.app.haven.appContainer
import com.app.haven.domain.chore.Assignee
import com.app.haven.domain.chore.Chore
import com.app.haven.domain.chore.completeChore
import com.app.haven.domain.chore.reopen
import com.app.haven.domain.chore.todayEpochDay
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.app.haven.domain.chore.ChoreCompletion
import com.app.haven.domain.chore.ChoreScope
import com.app.haven.domain.chore.completionId
import kotlinx.coroutines.delay
import com.app.haven.domain.chore.originalAssignee

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
        .map { list -> list.map { Assignee(it.uid, it.displayName, it.joinedAt?.time ?: Long.MAX_VALUE) } }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // used to flag chores still assigned to someone who left the household
    val memberUids: StateFlow<Set<String>> = members
        .map { list -> list.map { it.uid }.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val uiState: StateFlow<ChoresUiState> = chores.getChores(householdId, myUid)
        .map<List<Chore>, ChoresUiState> { list ->
            if (list.isEmpty()) ChoresUiState.Empty
            else ChoresUiState.Success(list.sortedWith(compareBy({ it.done }, { it.dueEpochDay })), todayEpochDay())
        }
        .catch { emit(ChoresUiState.Error(it.friendlyMessage())) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChoresUiState.Loading)

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    fun onErrorShown() { _error.value = null }

    // the chore exactly as it was before the last "mark done". Memory only, one slot, gone after the window
    private val _undo = MutableStateFlow<Chore?>(null)
    val undo: StateFlow<Chore?> = _undo.asStateFlow()

    private var undoTimer: Job? = null
    private var completionWrite: Job? = null

    fun onToggleDone(chore: Chore) {
        val today = todayEpochDay()
        val me = members.value.firstOrNull { it.uid == myUid } ?: Assignee(myUid, "Someone")
        val reopening = chore.done
        val updated = if (reopening) chore.reopen() else completeChore(chore, members.value, me, today)
        val shared = chore.scope == ChoreScope.HOUSEHOLD

        // Contribution history: only shared household chores count, with one record per occurrence. The record
        // has the same id every time, so a repeated tap or sync can't count an occurrence twice. The credit goes
        // to whoever ticked it, not to the assignee, and the original assignee is kept next to the current one.
        val record = if (shared && !reopening) {
            ChoreCompletion(
                id = completionId(chore.id, chore.dueEpochDay),
                choreId = chore.id,
                choreTitle = chore.title,
                completedByUid = me.uid,
                completedByName = me.name,
                assignedToUid = chore.assigneeUid,
                dueEpochDay = chore.dueEpochDay,
                completedEpochDay = today,
                originalAssigneeUid = chore.originalAssignee().uid
            )
        } else null
        val staleRecordId = if (shared && reopening) completionId(chore.id, chore.dueEpochDay) else null

        // un-ticking the chore that Undo was offered for makes that offer pointless
        if (reopening && _undo.value?.id == chore.id) clearUndo()

        // the chore and its record are saved together
        completionWrite = viewModelScope.launch {
            chores.applyCompletionChange(householdId, updated, record, staleRecordId)
                .onSuccess { if (!reopening) offerUndo(chore) }
                .onFailure { _error.value = it.friendlyMessage() }
        }
    }

    // Undo is offered for the latest completion only, and only for a short while. After that the snapshot is
    // gone, so an old undo can never overwrite newer changes or remove the wrong history record.
    private fun offerUndo(previous: Chore) {
        undoTimer?.cancel()
        _undo.value = previous
        undoTimer = viewModelScope.launch {
            delay(UNDO_WINDOW_MS)
            _undo.value = null
        }
    }

    private fun clearUndo() {
        undoTimer?.cancel()
        _undo.value = null
    }

    // Put the chore back exactly as it was and take the undone completion out of the history, in one batch.
    // It waits for the completion's own write first, so the removal can never run before the record exists.
    fun onUndo() {
        val previous = _undo.value ?: return
        clearUndo()
        val pending = completionWrite
        viewModelScope.launch {
            pending?.join()
            val recordId =
                if (previous.scope == ChoreScope.HOUSEHOLD) completionId(previous.id, previous.dueEpochDay) else null
            chores.applyCompletionChange(householdId, previous, record = null, removeRecordId = recordId)
                .onFailure { _error.value = it.friendlyMessage() }
        }
    }

    fun onDelete(chore: Chore) {
        viewModelScope.launch {
            chores.deleteChore(householdId, chore).onFailure { _error.value = it.friendlyMessage() }
        }
    }

    companion object {
        const val UNDO_WINDOW_MS = 10_000L

        fun factory(householdId: String, myUid: String) = viewModelFactory {
            initializer {
                val c = appContainer()
                ChoresViewModel(c.choreRepository, c.householdActions, householdId, myUid)
            }
        }
    }
}