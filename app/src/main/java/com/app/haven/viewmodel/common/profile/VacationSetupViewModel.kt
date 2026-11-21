package com.app.haven.viewmodel.common.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.app.haven.actions.HouseholdActions
import com.app.haven.actions.chores.ChoreActions
import com.app.haven.actions.chores.ChoreTransferActions
import com.app.haven.actions.friendlyMessage
import com.app.haven.appContainer
import com.app.haven.data.model.Member
import com.app.haven.domain.chore.Chore
import com.app.haven.domain.chore.ChoreTransferRequest
import com.app.haven.domain.chore.pendingForVacation
import com.app.haven.domain.chore.todayEpochDay
import com.app.haven.domain.chore.transferId
import com.app.haven.domain.chore.vacationBlockedMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// The step before Vacation Mode: every unfinished household chore this person has is finished or handed over.
// Offers go through the existing transfer backend, and the chore only leaves them once the roommate accepts.
class VacationSetupViewModel(
    private val chores: ChoreActions,
    private val transfers: ChoreTransferActions,
    private val households: HouseholdActions,
    private val householdId: String,
    private val myUid: String
) : ViewModel() {

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    fun onErrorShown() { _error.value = null }

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    // My unfinished household chores. Null until loaded, so an empty list is never mistaken for "nothing to do".
    // Accepted offers drop out of it by themselves, because the chore is then someone else's.
    val pending: StateFlow<List<Chore>?> = chores.getChores(householdId, myUid)
        .map<List<Chore>, List<Chore>?> { pendingForVacation(it, myUid) }
        .catch { _error.value = it.friendlyMessage() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // roommates who can be asked: not me, and not away on vacation
    val recipients: StateFlow<List<Member>> = households.observeMembers(householdId)
        .map { list -> list.filter { it.uid != myUid && !it.vacation }.sortedBy { it.displayName.lowercase() } }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // offers I sent or received, with their answers
    val offers: StateFlow<List<ChoreTransferRequest>> = transfers.observeTransfers(householdId, myUid)
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Offer each picked chore to one roommate. Sending again to someone else is how a declined chore is offered on.
    fun askToTake(selected: List<Chore>, to: Member) {
        viewModelScope.launch {
            _error.value = null
            selected.forEach { chore ->
                val request = ChoreTransferRequest(
                    id = transferId(chore.id, chore.dueEpochDay, to.uid),
                    choreId = chore.id, choreTitle = chore.title, dueEpochDay = chore.dueEpochDay,
                    fromUid = myUid, fromName = chore.assigneeName, toUid = to.uid, toName = to.displayName
                )
                transfers.sendTransfer(householdId, request).onFailure { _error.value = it.friendlyMessage() }
            }
        }
    }

    // Turns Vacation Mode on, but only if nothing is left in my hands. Checked again here with fresh data,
    // so the button being enabled is never the only thing protecting the rule.
    fun startVacation(onStarted: () -> Unit) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            try {
                val open = pendingForVacation(chores.getChores(householdId, myUid).first(), myUid)
                val blocked = vacationBlockedMessage(open.size)
                if (blocked != null) {
                    _error.value = blocked
                    return@launch
                }
                // Counted from tomorrow, so the work done today to clear the list stays in the contribution history.
                // Their member record says "on vacation" straight away, so no new automatic chores come their way.
                households.startVacation(householdId, today = todayEpochDay() + 1)
                onStarted()
            } catch (e: Exception) {
                _error.value = e.friendlyMessage()
            } finally {
                _busy.value = false
            }
        }
    }

    companion object {
        fun factory(householdId: String, myUid: String) = viewModelFactory {
            initializer {
                val c = appContainer()
                VacationSetupViewModel(
                    c.choreRepository, c.choreTransferRepository, c.householdActions, householdId, myUid
                )
            }
        }
    }
}
