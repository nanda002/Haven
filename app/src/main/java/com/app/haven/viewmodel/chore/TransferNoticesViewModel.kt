package com.app.haven.viewmodel.chore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.app.haven.actions.friendlyMessage
import com.app.haven.appContainer
import com.app.haven.domain.chore.ChoreTransferRequest
import com.app.haven.domain.chore.TransferStatus
import com.app.haven.actions.chores.ChoreActions
import com.app.haven.actions.chores.ChoreTransferActions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// What the notices at the top of the app show:
// - chores a roommate wants to hand to me, with Accept and Decline (the chore moves only on Accept)
// - chores I offered that were declined, which are still mine
class TransferNoticesViewModel(
    chores: ChoreActions,
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

    // notices put away on this phone: dismissed, or a request that can't be answered any more
    private val hidden = MutableStateFlow<Set<String>>(emptySet())

    val incoming: StateFlow<List<ChoreTransferRequest>> = combine(requests, hidden) { list, hide ->
        list.filter { it.toUid == myUid && it.status == TransferStatus.PENDING && it.id !in hide }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // declined offers that still matter: the chore is still mine and unfinished, and no other offer is waiting
    val declined: StateFlow<List<ChoreTransferRequest>> = combine(requests, myChores, hidden) { list, mine, hide ->
        list.filter { r ->
            r.fromUid == myUid && r.status == TransferStatus.DECLINED && r.id !in hide &&
                    mine.any { it.id == r.choreId && !it.done && it.assigneeUid == myUid && it.dueEpochDay == r.dueEpochDay } &&
                    list.none { o ->
                        o.choreId == r.choreId && o.dueEpochDay == r.dueEpochDay && o.status == TransferStatus.PENDING
                    }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    fun onErrorShown() { _error.value = null }

    private val answering = mutableSetOf<String>()

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
                TransferNoticesViewModel(c.choreRepository, c.choreTransferRepository, householdId, myUid)
            }
        }
    }
}
