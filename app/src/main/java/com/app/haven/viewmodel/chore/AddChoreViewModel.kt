package com.app.haven.viewmodel.chore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.app.haven.actions.ChoreActions
import com.app.haven.actions.ChoreDraftActions
import com.app.haven.actions.HouseholdActions
import com.app.haven.actions.friendlyMessage
import com.app.haven.appContainer
import com.app.haven.data.model.Member
import com.app.haven.domain.chore.Chore
import com.app.haven.domain.chore.ChoreCategory
import com.app.haven.domain.chore.ChoreDraft
import com.app.haven.domain.chore.isAllowedOccurrence
import com.app.haven.domain.chore.nextDueDay
import com.app.haven.domain.chore.normalizeNotes
import com.app.haven.domain.chore.resolveEndEpochDay
import com.app.haven.domain.chore.validateChore
import com.app.haven.domain.chore.validateEndDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// Used for both "Add chore" (choreId = null) and "Edit chore".
// Only the add form keeps a draft on the phone, same as the add-expense form.
class AddChoreViewModel(
    private val chores: ChoreActions,
    households: HouseholdActions,
    private val householdId: String,
    private val choreId: String?,
    private val drafts: ChoreDraftActions? = null
) : ViewModel() {

    val members: StateFlow<List<Member>> = households.observeMembers(householdId)
        .map { list -> list.sortedBy { it.displayName.lowercase() } }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // the chore being edited (null when adding, or when it was deleted meanwhile)
    private val _existing = MutableStateFlow<Chore?>(null)
    val existing: StateFlow<Chore?> = _existing.asStateFlow()

    // add mode: the unsaved draft found on this phone, if there is one
    private val _restoredDraft = MutableStateFlow<ChoreDraft?>(null)
    val restoredDraft: StateFlow<ChoreDraft?> = _restoredDraft.asStateFlow()

    // true until the chore (edit) or the saved draft (add) has been read
    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // true while saving, and after a successful save until the screen closes,
    // so repeated taps can't create duplicate chores
    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    // true after a successful save, so a last pending auto-save can't bring the draft back
    private var draftFinished = false

    init {
        viewModelScope.launch {
            if (choreId != null) {
                _existing.value = runCatching {
                    chores.getChores(householdId).first().firstOrNull { it.id == choreId }
                }.getOrNull()
            } else {
                _restoredDraft.value = runCatching { drafts?.loadDraft(householdId) }.getOrNull()
            }
            _loading.value = false
        }
    }

    // add mode only: called a moment after the user stops typing
    fun autoSaveDraft(draft: ChoreDraft) {
        if (choreId != null || drafts == null || draftFinished) return
        viewModelScope.launch {
            if (draft.isBlank()) drafts.deleteDraft(householdId) else drafts.saveDraft(householdId, draft)
        }
    }

    // the Clear button: the form is reset by the screen, this removes the saved draft
    fun clearDraft() {
        viewModelScope.launch { drafts?.deleteDraft(householdId) }
    }

    // endEpochDay: the picked end date, 0 = no end date
    fun save(
        title: String,
        category: String?,
        assignee: Member?,
        dueEpochDay: Long,
        repeatDays: Int,
        rotate: Boolean,
        endEpochDay: Long,
        notes: String,
        onDone: () -> Unit
    ) {
        validateChore(title, assignee?.uid, category)?.let { _error.value = it; return }
        validateEndDate(dueEpochDay, repeatDays, endEpochDay)?.let { _error.value = it; return }
        if (!_saving.compareAndSet(false, true)) return
        val base = _existing.value
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
            result
                .onSuccess {
                    // the chore is saved now, so its draft isn't needed anymore
                    if (base == null) {
                        draftFinished = true
                        drafts?.deleteDraft(householdId)
                    }
                    onDone()
                }
                .onFailure {
                    _saving.value = false   // allow another try, entered values are untouched
                    _error.value = it.friendlyMessage()
                }
        }
    }

    companion object {
        fun factory(householdId: String, choreId: String?) = viewModelFactory {
            initializer {
                val c = appContainer()
                AddChoreViewModel(c.choreRepository, c.householdActions, householdId, choreId, c.choreDraftRepository)
            }
        }
    }
}