package com.app.haven.viewmodel.chore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.app.haven.actions.chores.ChoreActions
import com.app.haven.actions.chores.ChoreDraftActions
import com.app.haven.actions.household.HouseholdActions
import com.app.haven.actions.friendlyMessage
import com.app.haven.appContainer
import com.app.haven.data.model.household.Member
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
import com.app.haven.domain.chore.titleCase
import com.app.haven.domain.chore.ChoreScope
import com.app.haven.domain.chore.NO_ELIGIBLE_MEMBER_MESSAGE
import com.app.haven.domain.chore.automaticPriorityOrder
import com.app.haven.domain.chore.pickAutomaticAssignee
import com.app.haven.domain.chore.todayEpochDay
import kotlin.random.Random
import kotlinx.coroutines.Job

// What the form shows under Automatic: who the algorithm would pick right now
sealed interface AutoAssignPreview {
    data object Loading : AutoAssignPreview
    data class Chosen(val name: String) : AutoAssignPreview
    data class Unavailable(val message: String) : AutoAssignPreview
}

// Used for both "Add chore" (choreId = null) and "Edit chore".
// Only the add form keeps a draft on the phone, same as the add-expense form.
class AddChoreViewModel(
    private val chores: ChoreActions,
    households: HouseholdActions,
    private val householdId: String,
    private val myUid: String,
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
                    chores.getChores(householdId, myUid).first().firstOrNull { it.id == choreId }
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

    private sealed interface AutoChoice {
        // order: the complete priority order, only filled in for a repeating chore
        data class Picked(val uid: String, val name: String, val order: List<String> = emptyList()) : AutoChoice
        data class Unavailable(val message: String) : AutoChoice
    }

    private val _autoPreview = MutableStateFlow<AutoAssignPreview>(AutoAssignPreview.Loading)
    val autoPreview: StateFlow<AutoAssignPreview> = _autoPreview.asStateFlow()
    private var previewJob: Job? = null

    // One random seed per form. The preview and the save both shuffle with it, so when two roommates are
    // completely tied they are put in the same order in both, as long as nothing else has changed.
    private val tieSeed = Random.nextLong()

    // Decides who gets an automatically assigned chore.
    // Loads members, completions and open chores ONCE from Firebase (fresh, never from an earlier result),
    // then works out the whole priority order locally. Nothing is fetched per roommate.
    // A repeating chore gets the complete priority order, which is saved on it and never recalculated.
    // A one-time chore only gets its highest-priority roommate, with no order.
    private suspend fun chooseAutomatically(repeating: Boolean = false): AutoChoice {
        val inputs = chores.loadAssignmentInputs(householdId).getOrElse {
            return AutoChoice.Unavailable(it.friendlyMessage())
        }
        val members = inputs.members.sortedBy { it.uid }
        if (!repeating) {
            val picked = pickAutomaticAssignee(
                members = members,
                completions = inputs.completions,
                openChores = inputs.openChores,
                today = todayEpochDay(),
                random = Random(tieSeed)
            ) ?: return AutoChoice.Unavailable(NO_ELIGIBLE_MEMBER_MESSAGE)
            return AutoChoice.Picked(picked.uid, picked.name)
        }
        val order = automaticPriorityOrder(
            members = members,
            completions = inputs.completions,
            openChores = inputs.openChores,
            today = todayEpochDay(),
            random = Random(tieSeed)
        )
        // the first in the order gets the first occurrence
        val first = order.firstOrNull() ?: return AutoChoice.Unavailable(NO_ELIGIBLE_MEMBER_MESSAGE)
        return AutoChoice.Picked(first.uid, first.name, order.map { it.uid })
    }

    // The form calls this whenever Automatic is shown. Saving works it out again.
    fun refreshAutoPreview() {
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            _autoPreview.value = AutoAssignPreview.Loading
            _autoPreview.value = when (val choice = chooseAutomatically()) {
                is AutoChoice.Picked -> AutoAssignPreview.Chosen(choice.name)
                is AutoChoice.Unavailable -> AutoAssignPreview.Unavailable(choice.message)
            }
        }
    }

    // endEpochDay: the picked end date, 0 = no end date
    // assignee: the manual pick (add) or the current assignee (edit). Ignored for personal and Automatic chores.
    fun save(
        title: String,
        category: String?,
        scope: String,
        autoAssign: Boolean,
        assignee: Member?,
        dueEpochDay: Long,
        repeatDays: Int,
        rotate: Boolean,
        endEpochDay: Long,
        notes: String,
        onDone: () -> Unit
    ) {
        val base = _existing.value
        val personal = (base?.scope ?: scope) == ChoreScope.PERSONAL
        val automatic = !personal && autoAssign
        // personal and Automatic chores get their assignee further down, so only a manual pick is checked here
        val checkedUid = if (personal || automatic) myUid else assignee?.uid
        validateChore(title, checkedUid, category)?.let { _error.value = it; return }
        validateEndDate(dueEpochDay, repeatDays, endEpochDay)?.let { _error.value = it; return }
        if (!_saving.compareAndSet(false, true)) return
        val end = resolveEndEpochDay(repeatDays, endEpochDay)
        // keep a stored next turn only if its date is still allowed by the (possibly new) end date,
        // otherwise a shortened end date would leave a stale "Next: ..." behind
        val keepNext = base != null && repeatDays > 0 && isAllowedOccurrence(nextDueDay(dueEpochDay, repeatDays), end)

        viewModelScope.launch {
            fun fail(message: String) { _saving.value = false; _error.value = message }

            // only an automatically assigned repeating chore gets a saved order
            var savedOrder: List<String> = emptyList()
            val target: Pair<String, String> = when {
                personal -> {
                    val name = members.value.firstOrNull { it.uid == myUid }?.displayName
                        ?: base?.assigneeName.orEmpty()
                    if (name.isBlank()) return@launch fail("Couldn't load your profile, please try again")
                    myUid to name
                }
                automatic -> when (val choice = chooseAutomatically(repeating = repeatDays > 0)) {
                    is AutoChoice.Picked -> {
                        savedOrder = choice.order
                        choice.uid to choice.name
                    }
                    is AutoChoice.Unavailable -> return@launch fail(choice.message)
                }
                else -> assignee!!.uid to assignee.displayName
            }
            val (assigneeUid, assigneeName) = target

            val chore = Chore(
                id = base?.id ?: "",
                title = titleCase(title),
                category = ChoreCategory.fromKey(category).key,
                notes = normalizeNotes(notes),
                nextAssigneeUid = if (keepNext) base?.nextAssigneeUid.orEmpty() else "",
                nextAssigneeName = if (keepNext) base?.nextAssigneeName.orEmpty() else "",
                assigneeUid = assigneeUid,
                assigneeName = assigneeName,
                dueEpochDay = dueEpochDay,
                repeatDays = repeatDays,
                // a personal chore never takes turns: that would hand it to a roommate
                rotate = rotate && !personal,
                endEpochDay = end,
                done = base?.done ?: false,
                completedByUid = base?.completedByUid,
                completedByName = base?.completedByName,
                lastCompletedEpochDay = base?.lastCompletedEpochDay ?: 0L,
                scope = if (personal) ChoreScope.PERSONAL else ChoreScope.HOUSEHOLD,
                ownerUid = if (personal) myUid else "",
                originalAssigneeUid = base?.originalAssigneeUid ?: assigneeUid,
                originalAssigneeName = base?.originalAssigneeName ?: assigneeName,
                originalForEpochDay = base?.originalForEpochDay ?: dueEpochDay,
                // fixed for the life of the chore: an edit keeps what was saved, only a new chore gets one
                rotationOrder = if (automatic) savedOrder else (base?.rotationOrder ?: savedOrder)
            )
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
                .onFailure { fail(it.friendlyMessage()) }   // allow another try, entered values are untouched
        }
    }

    companion object {
        fun factory(householdId: String, myUid: String, choreId: String?) = viewModelFactory {
            initializer {
                val c = appContainer()
                AddChoreViewModel(
                    c.choreRepository, c.householdActions, householdId, myUid, choreId, c.choreDraftRepository
                )
            }
        }
    }
}