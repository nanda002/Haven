package com.app.haven.viewmodel.vacation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.app.haven.actions.HouseholdActions
import com.app.haven.actions.friendlyMessage
import com.app.haven.appContainer
import com.app.haven.data.model.Member
import com.app.haven.domain.chore.Chore
import com.app.haven.domain.chore.ChoreTransferRequest
import com.app.haven.domain.chore.TransferStatus
import com.app.haven.domain.chore.offerFor
import com.app.haven.domain.chore.pendingForVacation
import com.app.haven.domain.chore.transferId
import com.app.haven.domain.chore.vacationBlockedMessage
import com.app.haven.actions.chores.ChoreActions
import com.app.haven.actions.chores.ChoreTransferActions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// Vacation Mode starts only when nothing unfinished is left in the shared chores that are with this person.
// Each chore is either finished (from the Chores tab) or handed over: the chore moves only after the roommate accepts.
class VacationSetupViewModel(
    private val chores: ChoreActions,
    private val transfers: ChoreTransferActions,
    private val households: HouseholdActions,
    private val householdId: String,
    private val myUid: String
) : ViewModel() {

    private val members: StateFlow<List<Member>> = households.observeMembers(householdId)
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // who a chore can be offered to: still here, not on vacation, and not me
    val roommates: StateFlow<List<Member>> = members
        .map { list -> list.filter { it.active && !it.vacation && it.uid != myUid }.sortedBy { it.displayName.lowercase() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // the chores that still hold vacation back, kept up to date as they are finished or handed over. null while loading
    val pending: StateFlow<List<Chore>?> = chores.getChores(householdId, myUid)
        .map<List<Chore>, List<Chore>?> { pendingForVacation(it, myUid) }
        .catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // my offers that are waiting for an answer or were declined
    val offers: StateFlow<List<ChoreTransferRequest>> = transfers.observeTransfers(householdId, myUid)
        .map { list ->
            list.filter {
                it.fromUid == myUid && (it.status == TransferStatus.PENDING || it.status == TransferStatus.DECLINED)
            }
        }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selected = MutableStateFlow<Set<String>>(emptySet())      // chore ids
    val selected: StateFlow<Set<String>> = _selected.asStateFlow()

    private val _recipient = MutableStateFlow<Map<String, String>>(emptyMap())   // chore id -> roommate uid
    val recipient: StateFlow<Map<String, String>> = _recipient.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun onErrorShown() { _error.value = null }

    fun toggle(choreId: String) {
        val now = _selected.value
        _selected.value = if (choreId in now) now - choreId else now + choreId
    }

    // "Select all" on, or off again. Single chores can still be switched afterwards.
    fun setAll(choreIds: List<String>, on: Boolean) {
        _selected.value = if (on) choreIds.toSet() else emptySet()
    }

    fun pickRecipient(choreId: String, uid: String) {
        _recipient.value = _recipient.value + (choreId to uid)
    }

    // One request per selected chore that has a roommate picked. Sending only records the offer:
    // the chore stays with me until that roommate accepts.
    fun sendRequests() {
        val list = pending.value ?: return
        if (_busy.value) return
        val me = members.value.firstOrNull { it.uid == myUid }
        viewModelScope.launch {
            _busy.value = true
            for (chore in list) {
                if (chore.id !in _selected.value) continue
                val toUid = _recipient.value[chore.id] ?: continue
                val to = roommates.value.firstOrNull { it.uid == toUid } ?: continue
                // already waiting for an answer: don't send a second one
                if (offerFor(offers.value, chore)?.status == TransferStatus.PENDING) continue
                val request = ChoreTransferRequest(
                    id = transferId(chore.id, chore.dueEpochDay, to.uid),
                    choreId = chore.id,
                    choreTitle = chore.title,
                    dueEpochDay = chore.dueEpochDay,
                    fromUid = myUid,
                    fromName = me?.displayName ?: chore.assigneeName,
                    toUid = to.uid,
                    toName = to.displayName
                )
                transfers.sendTransfer(householdId, request).onFailure { _error.value = it.friendlyMessage() }
            }
            _selected.value = emptySet()
            _recipient.value = emptyMap()
            _busy.value = false
        }
    }

    // Starts Vacation Mode only if nothing is left. The list is read again from fresh data first,
    // so a screen that is out of date can never start it too early.
    fun startVacation(onDone: () -> Unit) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            val blocked = chores.loadAssignmentInputs(householdId)
                .map { pendingForVacation(it.openChores, myUid).size }
                .fold(
                    onSuccess = { vacationBlockedMessage(it) },
                    onFailure = { it.friendlyMessage() }
                )
            if (blocked != null) {
                _error.value = blocked
                _busy.value = false
                return@launch
            }
            runCatching { households.startVacation(householdId) }
                .onSuccess { onDone() }
                .onFailure { _error.value = it.friendlyMessage() }
            _busy.value = false
        }
    }

    companion object {
        fun factory(householdId: String, myUid: String) = viewModelFactory {
            initializer {
                val c = appContainer()
                VacationSetupViewModel(c.choreRepository, c.choreTransferRepository, c.householdActions, householdId, myUid)
            }
        }
    }
}
