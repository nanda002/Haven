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
import com.app.haven.domain.chore.nextRecipientUid
import com.app.haven.domain.chore.offerFor
import com.app.haven.domain.chore.offersExhausted
import com.app.haven.domain.chore.pendingForVacation
import com.app.haven.domain.chore.todayEpochDay
import com.app.haven.domain.chore.transferId
import com.app.haven.domain.chore.vacationBlockedMessage
import com.app.haven.actions.chores.ChoreActions
import com.app.haven.actions.chores.ChoreTransferActions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// Vacation Mode starts only when nothing unfinished is left in the shared chores that are with this person.
// Every such chore is handed over automatically, one roommate at a time:
//   1. The chores are spread round-robin over the available roommates and each gets an offer.
//   2. On Accept the chore moves to that roommate (done in the transfer transaction, not here).
//   3. On Decline the same chore is offered to the next roommate who has not been asked yet.
//      Neither the roommate who declined nor the person going on vacation is asked again.
//   4. If every eligible roommate declines, the chore stays with this person and keeps blocking vacation.
// A decline never changes the chore's assignee; only an accept does.
// Note: the next offer after a decline is sent by this screen, so it only advances while the screen is open.
class VacationSetupViewModel(
    private val chores: ChoreActions,
    private val transfers: ChoreTransferActions,
    private val households: HouseholdActions,
    private val householdId: String,
    private val myUid: String
) : ViewModel() {

    // Live household members (one Firestore listener for the whole screen).
    private val members: StateFlow<List<Member>> = households.observeMembers(householdId)
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Who a chore can be offered to: still here, not on vacation, and not me. Sorted by name so the
    // round-robin order is stable.
    val roommates: StateFlow<List<Member>> = members
        .map { list -> list.filter { it.active && !it.vacation && it.uid != myUid }.sortedBy { it.displayName.lowercase() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // The chores that still hold vacation back. Built from the live chores collection, so it updates by
    // itself as chores are finished or accepted by someone else; it is never fetched again to refresh.
    // null while loading.
    val pending: StateFlow<List<Chore>?> = chores.getChores(householdId, myUid)
        .map<List<Chore>, List<Chore>?> { pendingForVacation(it, myUid) }
        .catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // Every offer I sent (waiting, declined or accepted). Accepted ones are kept so that a chore that was
    // just taken over is never offered to someone else in the moment before the chore list catches up.
    val offers: StateFlow<List<ChoreTransferRequest>> = transfers.observeTransfers(householdId, myUid)
        .map { list -> list.filter { it.fromUid == myUid } }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Ids of chores that every eligible roommate has declined. They stay with me and block vacation.
    val exhausted: StateFlow<Set<String>> = combine(pending, offers, roommates) { list, sent, mates ->
        val uids = mates.map { it.uid }
        list.orEmpty().filter { offersExhausted(uids, sent, it) }.map { it.id }.toSet()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())


    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun onErrorShown() { _error.value = null }

    // True once the user has pressed the send button: from then on a decline automatically triggers the next offer.
    private var distributing = false

    // Makes sure only one hand-out round runs at a time.
    private val offerLock = Mutex()

    // Ids of requests already sent in this session, so a slow snapshot can never cause the same offer twice
    // (writing the same request id again would overwrite its answer).
    private val sentIds = mutableSetOf<String>()

    init {
        // Whenever an answer comes in (or the roommate list changes), hand out whatever can be handed out.
        viewModelScope.launch {
            combine(offers, roommates) { _, _ -> Unit }.collect {
                if (distributing) offerPendingChores()
            }
        }
    }

    // Starts the hand-over: offers every pending chore to a roommate (round-robin) and switches on the
    // automatic follow-up offers after a decline. Sending only records offers; each chore stays with me
    // until a roommate accepts.
    fun sendRequests() {
        if (_busy.value || pending.value == null) return
        viewModelScope.launch {
            _busy.value = true
            distributing = true
            offerPendingChores()
            _busy.value = false
        }
    }

    // One pass over the pending chores. For each chore that has no offer waiting for an answer, finds the
    // next roommate who has not been asked about it yet and sends them an offer. Chores nobody is left to
    // take are skipped and stay with me. Safe to call any number of times: it only ever sends what is missing.
    private suspend fun offerPendingChores() = offerLock.withLock {
        val list = pending.value ?: return@withLock
        val candidateUids = roommates.value.map { it.uid }
        val me = members.value.firstOrNull { it.uid == myUid }

        list.forEachIndexed { index, chore ->
            // an offer for this chore is already waiting for an answer: don't send a second one
            if (offerFor(offers.value, chore)?.status == TransferStatus.PENDING) return@forEachIndexed

            val toUid = nextRecipientUid(candidateUids, offers.value, chore, index) ?: return@forEachIndexed
            val to = roommates.value.firstOrNull { it.uid == toUid } ?: return@forEachIndexed

            val id = transferId(chore.id, chore.dueEpochDay, to.uid)
            if (!sentIds.add(id)) return@forEachIndexed

            val request = ChoreTransferRequest(
                id = id,
                choreId = chore.id,
                choreTitle = chore.title,
                dueEpochDay = chore.dueEpochDay,
                fromUid = myUid,
                fromName = me?.displayName ?: chore.assigneeName,
                toUid = to.uid,
                toName = to.displayName
            )
            transfers.sendTransfer(householdId, request).onFailure {
                sentIds.remove(id)
                _error.value = it.friendlyMessage()
            }
        }
    }

    // Starts Vacation Mode only if nothing is left. Only the open chores are fetched fresh for this
    // final check (no members, no completions), so an out-of-date screen can never start it too early.
    // Because a chore moves only when a roommate accepts, every unfinished chore must have been accepted
    // by someone else or finished before this passes.
    fun startVacation(onDone: () -> Unit) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            val blocked = chores.loadOpenChores(householdId)
                .map { pendingForVacation(it, myUid).size }
                .fold(
                    onSuccess = { vacationBlockedMessage(it) },
                    onFailure = { it.friendlyMessage() }
                )
            if (blocked != null) {
                _error.value = blocked
                _busy.value = false
                return@launch
            }
            distributing = false
            // Counted from tomorrow, so the chores finished today to clear the list still count as contribution.
            // The member record says "on vacation" straight away, so no new automatic chores are given to them.
            runCatching { households.startVacation(householdId, today = todayEpochDay() + 1) }
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
