package com.app.haven.viewmodel.chore

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
import com.app.haven.domain.chore.transferId
import com.app.haven.actions.chores.ChoreActions
import com.app.haven.actions.chores.ChoreTransferActions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// What the notices at the top of the app show:
// - chores a roommate wants to hand to me, with Accept and Decline (the chore moves only on Accept)
// - ONE notice per chore that could not be handed over because every eligible roommate declined.
//   That chore stays with me and is still my responsibility.
// A single decline shows nothing: if another roommate can still be asked, the next offer is sent quietly.
// Every individual transfer record is kept; only what the UI displays is simplified.
class TransferNoticesViewModel(
    chores: ChoreActions,
    households: HouseholdActions,
    private val transfers: ChoreTransferActions,
    private val householdId: String,
    private val myUid: String
) : ViewModel() {

    private val requests: StateFlow<List<ChoreTransferRequest>> = transfers.observeTransfers(householdId, myUid)
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val myChores = chores.getChores(householdId, myUid)
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Household members, null until they have loaded. Without this, "nobody left to ask" could be
    // concluded from an empty list that simply hasn't arrived yet.
    private val members: StateFlow<List<Member>?> = households.observeMembers(householdId)
        .map<List<Member>, List<Member>?> { it }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // notices put away on this phone: dismissed, or a request that can't be answered any more
    private val hidden = MutableStateFlow<Set<String>>(emptySet())

    val incoming: StateFlow<List<ChoreTransferRequest>> = combine(requests, hidden) { list, hide ->
        list.filter { it.toUid == myUid && it.status == TransferStatus.PENDING && it.id !in hide }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // A declined offer of mine that has nowhere to go yet: the chore is still mine and unfinished, it is the
    // same occurrence, and no other offer for it is waiting. One entry per chore occurrence, paired with
    // the chore itself.
    private fun stuckAfterDecline(
        list: List<ChoreTransferRequest>,
        mine: List<Chore>
    ): List<Pair<ChoreTransferRequest, Chore>> =
        list.filter { it.fromUid == myUid && it.status == TransferStatus.DECLINED }
            .sortedBy { it.id }
            .mapNotNull { r ->
                val chore = mine.firstOrNull {
                    it.id == r.choreId && !it.done && it.assigneeUid == myUid && it.dueEpochDay == r.dueEpochDay
                } ?: return@mapNotNull null
                val otherWaiting = list.any {
                    it.choreId == r.choreId && it.dueEpochDay == r.dueEpochDay && it.status == TransferStatus.PENDING
                }
                if (otherWaiting) null else r to chore
            }
            .distinctBy { (r, _) -> r.choreId to r.dueEpochDay }

    // Roommates a chore can be offered to: still here, not on vacation, not me. Fixed order.
    private fun candidateUids(list: List<Member>): List<String> =
        list.filter { it.active && !it.vacation && it.uid != myUid }
            .sortedBy { it.displayName.lowercase() }
            .map { it.uid }

    // Chores where every eligible roommate has declined. Shown as one notice each. The notice is
    // represented by one of the declined requests so that dismissing it has an id to hide.
    val declined: StateFlow<List<ChoreTransferRequest>> =
        combine(requests, myChores, members, hidden) { list, mine, people, hide ->
            if (people == null) return@combine emptyList()
            val uids = candidateUids(people)
            val sent = list.filter { it.fromUid == myUid }
            stuckAfterDecline(list, mine)
                .filter { (_, chore) -> nextRecipientUid(uids, sent, chore, 0) == null }
                .map { (r, _) -> r }
                .filter { it.id !in hide }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    fun onErrorShown() { _error.value = null }

    private val answering = mutableSetOf<String>()

    // Makes sure only one follow-up round runs at a time, and remembers what was already sent, so a slow
    // snapshot can never send the same offer twice (writing an existing request id would reset its answer).
    private val sendLock = Mutex()
    private val sentIds = mutableSetOf<String>()

    init {
        // Quietly passes a declined chore on to the next roommate who has not been asked about it yet.
        viewModelScope.launch {
            combine(requests, myChores, members) { list, mine, people -> Triple(list, mine, people) }
                .collect { (list, mine, people) ->
                    if (people != null) sendNextOffers(list, mine, people)
                }
        }
    }

    private suspend fun sendNextOffers(
        list: List<ChoreTransferRequest>,
        mine: List<Chore>,
        people: List<Member>
    ) = sendLock.withLock {
        val uids = candidateUids(people)
        val sent = list.filter { it.fromUid == myUid }
        val me = people.firstOrNull { it.uid == myUid }

        stuckAfterDecline(list, mine).forEachIndexed { index, (_, chore) ->
            val toUid = nextRecipientUid(uids, sent, chore, index) ?: return@forEachIndexed
            val to = people.firstOrNull { it.uid == toUid } ?: return@forEachIndexed

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

    fun accept(request: ChoreTransferRequest) = answer(request, accept = true)
    fun decline(request: ChoreTransferRequest) = answer(request, accept = false)

    // Accepting moves the chore to me and keeps who it was first given to. Declining leaves it with them.
    private fun answer(request: ChoreTransferRequest, accept: Boolean) {
        if (!answering.add(request.id)) return
        viewModelScope.launch {
            _error.value = null
            transfers.respondToTransfer(householdId, request, accept).onFailure { e ->
                _error.value = e.friendlyMessage()
                // chore finished, moved on or already handed elsewhere: this request can't be answered any more
                if (e is IllegalStateException) hidden.value = hidden.value + request.id
            }
            answering.remove(request.id)
        }
    }

    fun dismiss(request: ChoreTransferRequest) {
        hidden.value = hidden.value + request.id
    }

    companion object {
        fun factory(householdId: String, myUid: String) = viewModelFactory {
            initializer {
                val c = appContainer()
                TransferNoticesViewModel(
                    c.choreRepository, c.householdActions, c.choreTransferRepository, householdId, myUid
                )
            }
        }
    }
}