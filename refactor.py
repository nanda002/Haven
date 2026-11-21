import os, shutil, sys

root = sys.argv[1] if len(sys.argv) > 1 else "."
SKIP = {"build", ".git", ".gradle", ".idea"}


def find(name):
    hits = []
    for d, dirs, files in os.walk(root):
        dirs[:] = [x for x in dirs if x not in SKIP]
        if name in files:
            hits.append(os.path.join(d, name))
    return hits


def write(name, content):
    hits = find(name)
    if len(hits) != 1:
        print(f"SKIPPED {name}: found {len(hits)} matches, expected 1")
        return
    path = hits[0]
    shutil.copy(path, path + ".bak")
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)
    print(f"wrote {path} (backup: {path}.bak)")


def patch(name, old, new):
    hits = find(name)
    if len(hits) != 1:
        print(f"SKIPPED {name}: found {len(hits)} matches, expected 1")
        return
    path = hits[0]
    text = open(path, encoding="utf-8").read()
    if text.count(old) != 1:
        print(f"SKIPPED patch in {name}: expected text not found exactly once")
        return
    shutil.copy(path, path + ".bak")
    open(path, "w", encoding="utf-8").write(text.replace(old, new))
    print(f"patched {path} (backup: {path}.bak)")


# ---------------------------------------------------------------------------
# AutoAssign.kt
# ---------------------------------------------------------------------------
AUTO_ASSIGN = r'''package com.app.haven.domain.chore

import kotlin.random.Random

const val WORKLOAD_WINDOW_DAYS = 7L

const val NO_ELIGIBLE_MEMBER_MESSAGE = "No roommate is available to take this chore: everyone has left or is on vacation."

// A stretch of days a member was on vacation. The end is the last day away.
data class VacationPeriod(
    val startEpochDay: Long,
    val endEpochDay: Long = Long.MAX_VALUE
)

// An earlier stay in the household. Kept when someone leaves, so their old work still counts for them
// if they ever come back.
data class MembershipPeriod(
    val joinedEpochDay: Long,
    val leftEpochDay: Long
)

// A roommate as the assignment logic sees them.
data class HouseholdMember(
    val uid: String,
    val name: String,
    val joinedEpochDay: Long,                  // start of the current stay
    val active: Boolean = true,
    val onVacation: Boolean = false,
    val vacationPeriods: List<VacationPeriod> = emptyList(),
    val pastMemberships: List<MembershipPeriod> = emptyList()
) {
    // True if the member was in the household on this day and not away on vacation.
    // Work done before they joined is never theirs, and vacation days are not days they were expected to help.
    fun participatedOn(day: Long): Boolean =
        inHousehold(day) &&
                vacationPeriods.none { day >= it.startEpochDay && day <= it.endEpochDay }

    // True if the day falls in the current stay or in any earlier stay.
    private fun inHousehold(day: Long): Boolean =
        day >= joinedEpochDay ||
                pastMemberships.any { day >= it.joinedEpochDay && day <= it.leftEpochDay }

    // The first day this member ever belonged to the household (earliest past stay or current stay).
    fun firstDayInHousehold(): Long =
        minOf(joinedEpochDay, pastMemberships.minOfOrNull { it.joinedEpochDay } ?: Long.MAX_VALUE)
}

// How many of the given completions belong to this member.
// Only pass shared household chore completions into this.
// A completion counts only if this member did it AND it happened on a day they participated.
// The second check protects against wrong values in the database (work dated before they joined
// or during a vacation) skewing the numbers.
fun contributionCount(
    member: HouseholdMember,
    completions: List<ChoreCompletion>
): Int =
    completions.count {
        it.completedByUid == member.uid &&
                member.participatedOn(it.completedEpochDay)
    }

// Each roommate's percentage of all household chores completed, compared over the one period in which
// every listed roommate was present. This is a plain "who did how much" view and does NOT account for
// how long each person has lived there. Automatic assignment uses contributionShortfalls instead.
fun contributionPercentages(
    members: List<HouseholdMember>,
    completions: List<ChoreCompletion>
): Map<String, Double> {
    val shared = completions.filter { done -> members.all { it.participatedOn(done.completedEpochDay) } }
    val counts = members.associate { it.uid to contributionCount(it, shared) }

    val total = counts.values.sum()

    return counts.mapValues { (_, count) ->
        if (total == 0) 0.0
        else count * 100.0 / total
    }
}

// Members who are currently active and not on vacation. Only these can receive a new automatic chore.
fun eligibleMembers(
    members: List<HouseholdMember>
): List<HouseholdMember> =
    members.filter { it.active && !it.onVacation }

// Number of days from fromDay to toDay (both included) on which this member took part in the household,
// meaning they had joined and were not on vacation. This is the member's "eligible time".
fun eligibleDays(member: HouseholdMember, fromDay: Long, toDay: Long): Int {
    if (toDay < fromDay) return 0
    var count = 0
    for (day in fromDay..toDay) {
        if (member.participatedOn(day)) count++
    }
    return count
}

// How far behind (positive) or ahead (negative) each member is compared with a fair share of the work.
//   expected share = the member's eligible days / everyone's eligible days combined
//   actual share   = the member's completed chores / everyone's completed chores combined
//   shortfall      = expected share - actual share
// Time is measured from the earliest join date among the given members up to today, so someone who joined
// yesterday has a tiny expected share and is not treated as equally behind as someone who has lived there
// for months. When nobody has completed anything yet, the shortfall is just the expected share,
// so the person with the most eligible time is the one most "owed" a chore.
fun contributionShortfalls(
    members: List<HouseholdMember>,
    completions: List<ChoreCompletion>,
    today: Long
): Map<String, Double> {
    if (members.isEmpty()) return emptyMap()

    val windowStart = members.minOf { it.firstDayInHousehold() }
    val days = members.associate { it.uid to eligibleDays(it, windowStart, today) }
    val totalDays = days.values.sum()

    val counts = members.associate { it.uid to contributionCount(it, completions) }
    val totalCount = counts.values.sum()

    return members.associate { member ->
        val expected =
            if (totalDays == 0) 1.0 / members.size
            else days.getValue(member.uid).toDouble() / totalDays
        val actual =
            if (totalCount == 0) 0.0
            else counts.getValue(member.uid).toDouble() / totalCount
        member.uid to (expected - actual)
    }
}

// The last day of the rolling window that starts today. Due dates have no time of day, so a chore is
// due for the whole of its day: a window of seven days from this moment always ends inside day today + 7,
// which makes today and the six days after it the days that fall within it (never a Monday-to-Sunday week).
fun workloadWindowEnd(today: Long): Long = today + WORKLOAD_WINDOW_DAYS - 1

// Outstanding household workload of one person: every unfinished occurrence due in the next seven days,
// plus every unfinished overdue occurrence however old. Each occurrence is one unit, whatever the chore is.
// A chore counts for whoever is responsible for it right now, so a handed-over chore counts for the new person.
// Personal chores never count.
fun workloadOf(
    uid: String,
    openChores: List<Chore>,
    today: Long
): Int =
    openChores
        .filter { it.scope == ChoreScope.HOUSEHOLD }
        .sumOf { occurrencesFor(it, uid, today) }

// How many occurrences of one chore count towards this person's workload.
// A finished chore only counts through its coming repetitions. An unfinished chore counts only for its
// current assignee (current occurrence plus later ones that fall inside the window). Anyone else gets 0.
private fun occurrencesFor(chore: Chore, uid: String, today: Long): Int {
    val windowEnd = workloadWindowEnd(today)
    return when {
        chore.done -> upcomingAfterFinished(chore, uid, windowEnd)
        chore.assigneeUid == uid -> currentAndUpcoming(chore, today, windowEnd)
        else -> 0
    }
}

// Workload for an unfinished chore held by the person: the occurrence in hand, plus the later ones
// of a same-person (non-rotating) repeating chore that fall inside the window.
private fun currentAndUpcoming(chore: Chore, today: Long, windowEnd: Long): Int {
    // not overdue and too far away
    if (chore.dueEpochDay > windowEnd) return 0

    // an overdue occurrence counts once, however long ago it was due. Later occurrences are not counted
    // until it has been dealt with, so it is never counted twice
    if (chore.dueEpochDay < today) return 1

    var count = 1
    if (chore.repeatDays > 0 && !chore.rotate) {
        count += datesInWindow(chore, chore.dueEpochDay + chore.repeatDays, windowEnd)
    }
    return count
}

// Workload for a finished chore: the finished occurrence itself is not counted, but a repeating chore
// still has more coming. A rotating chore only has its very next turn handed out; a same-person chore
// has every remaining occurrence in the window.
private fun upcomingAfterFinished(chore: Chore, uid: String, windowEnd: Long): Int {
    if (chore.repeatDays <= 0) return 0
    val first = nextDueDay(chore.dueEpochDay, chore.repeatDays)
    if (first > windowEnd || !isAllowedOccurrence(first, chore.endEpochDay)) return 0

    return if (chore.rotate) {
        // only the very next turn has been handed out: it belongs to whoever completing the chore chose
        val nextUid = chore.nextAssigneeUid.ifBlank { chore.assigneeUid }
        if (nextUid == uid) 1 else 0
    } else {
        // same person: every remaining occurrence in the window is theirs
        if (chore.assigneeUid == uid) datesInWindow(chore, first, windowEnd) else 0
    }
}

// Counts the due dates first, first + interval, first + 2 * interval, ... that are inside the window
// and not past the chore's end date.
private fun datesInWindow(chore: Chore, first: Long, windowEnd: Long): Int {
    var count = 0
    var next = first
    while (next <= windowEnd && isAllowedOccurrence(next, chore.endEpochDay)) {
        count++
        next += chore.repeatDays
    }
    return count
}

// Every eligible roommate, highest priority first:
//   1. Largest shortfall first: the person furthest below their fair share of the work, where a fair share
//      is based on the days they were actually in the household and not on vacation (see contributionShortfalls).
//      Having no completed chores gives no special priority on its own; a brand new roommate only
//      ranks high if their short time in the household still leaves them behind.
//   2. Shortfall tied: lowest upcoming workload.
//   3. Both tied: random (the list is shuffled first and the sorts keep that order for ties).
// Inactive and vacationing members are left out. Personal chores never reach this: the repository only
// supplies household chores and completions.
// Everything is calculated locally from the lists passed in; nothing is fetched here, so the caller
// loads membership, completions and open chores once per decision.
fun automaticPriorityOrder(
    members: List<HouseholdMember>,
    completions: List<ChoreCompletion>,
    openChores: List<Chore>,
    today: Long,
    random: Random = Random.Default
): List<HouseholdMember> {
    val eligible = eligibleMembers(members)
    if (eligible.isEmpty()) return emptyList()

    val shortfalls = contributionShortfalls(eligible, completions, today)
    val workloads = eligible.associate { it.uid to workloadOf(it.uid, openChores, today) }

    return eligible.shuffled(random).sortedWith(
        // rounded so that shortfalls differing only in the last decimal places still count as tied
        compareByDescending<HouseholdMember> { Math.round(shortfalls.getValue(it.uid) * 1_000_000.0) }
            .thenBy { workloads.getValue(it.uid) }
    )
}

// The highest-priority roommate for a new automatically assigned household chore, or null if nobody is eligible.
fun pickAutomaticAssignee(
    members: List<HouseholdMember>,
    completions: List<ChoreCompletion>,
    openChores: List<Chore>,
    today: Long,
    random: Random = Random.Default
): HouseholdMember? =
    automaticPriorityOrder(members, completions, openChores, today, random).firstOrNull()
'''

# ---------------------------------------------------------------------------
# VacationRules.kt
# ---------------------------------------------------------------------------
VACATION_RULES = r'''package com.app.haven.domain.chore

// Unfinished shared chores that are with this person right now. All of them have to be finished or
// handed over before Vacation Mode can start. Finished chores and personal chores are never in the list.
// Sorted by due date so the most urgent chore comes first.
fun pendingForVacation(chores: List<Chore>, uid: String): List<Chore> =
    chores
        .filter { !it.done && it.scope == ChoreScope.HOUSEHOLD && it.assigneeUid == uid }
        .sortedBy { it.dueEpochDay }

// The message shown when Vacation Mode cannot start yet, or null when nothing is blocking it.
fun vacationBlockedMessage(pendingCount: Int): String? = when (pendingCount) {
    0 -> null
    1 -> "1 chore still needs to be finished or handed over"
    else -> "$pendingCount chores still need to be finished or handed over"
}

// The offer to show next to a chore: one that is waiting for an answer first, otherwise one that was declined.
// Only offers for this exact occurrence (same chore and same due date) are looked at.
fun offerFor(requests: List<ChoreTransferRequest>, chore: Chore): ChoreTransferRequest? {
    val forThisOccurrence = requests.filter { it.choreId == chore.id && it.dueEpochDay == chore.dueEpochDay }
    return forThisOccurrence.firstOrNull { it.status == TransferStatus.PENDING }
        ?: forThisOccurrence.firstOrNull { it.status == TransferStatus.DECLINED }
}

// Everyone this occurrence has already been offered to by its current holder, whatever the answer was.
// Those people are never asked about it again.
fun askedUidsFor(requests: List<ChoreTransferRequest>, chore: Chore): Set<String> =
    requests
        .filter {
            it.choreId == chore.id &&
                    it.dueEpochDay == chore.dueEpochDay &&
                    it.fromUid == chore.assigneeUid
        }
        .map { it.toUid }
        .toSet()

// Who a chore should be offered to next, or null when nobody is left to ask.
// candidateUids: the roommates who are still here and not on vacation, in a fixed order.
// Skips the person holding the chore (the one going on vacation) and everyone who was already asked.
// choreIndex is the chore's position in the list being handed out. It picks which remaining roommate is
// next, so the first round is spread round-robin (chore 0 -> roommate 0, chore 1 -> roommate 1, ...,
// wrapping around) and after a decline the chore moves on to another roommate who has not been asked.
fun nextRecipientUid(
    candidateUids: List<String>,
    requests: List<ChoreTransferRequest>,
    chore: Chore,
    choreIndex: Int
): String? {
    val asked = askedUidsFor(requests, chore)
    val available = candidateUids.filter { it != chore.assigneeUid && it !in asked }
    if (available.isEmpty()) return null
    return available[Math.floorMod(choreIndex, available.size)]
}

// True when this chore cannot be handed over any more: no offer is waiting for an answer and no eligible
// roommate is left who has not been asked. The chore then stays with the person going on vacation,
// who is still responsible for it, so Vacation Mode stays blocked.
fun offersExhausted(
    candidateUids: List<String>,
    requests: List<ChoreTransferRequest>,
    chore: Chore
): Boolean {
    val waiting = offerFor(requests, chore)?.status == TransferStatus.PENDING
    return !waiting && nextRecipientUid(candidateUids, requests, chore, 0) == null
}
'''

# ---------------------------------------------------------------------------
# ChoreTransferActions.kt  (behaviour unchanged, comments improved)
# ---------------------------------------------------------------------------
TRANSFER_ACTIONS = r'''package com.app.haven.actions.chores

import android.util.Log
import com.app.haven.data.model.Chore as ChoreDoc
import com.app.haven.data.model.ChoreTransferDoc
import com.app.haven.data.model.toData
import com.app.haven.data.model.toDomain
import com.app.haven.domain.chore.ChoreTransferRequest
import com.app.haven.domain.chore.TransferStatus
import com.app.haven.domain.chore.resolveOccurrence
import com.app.haven.domain.chore.todayEpochDay
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.tasks.await

// Offering a chore to another roommate. Nothing changes on the chore until the receiver accepts.
// A declined offer never touches the chore: it stays with the person who offered it. Choosing who is
// asked next after a decline is decided by the caller (see nextRecipientUid), not here.
interface ChoreTransferActions {
    // Live list of every request this person sent or received.
    fun observeTransfers(householdId: String, myUid: String): Flow<List<ChoreTransferRequest>>

    // Records a new offer. Does not change the chore.
    suspend fun sendTransfer(householdId: String, request: ChoreTransferRequest): Result<Unit>

    // Accepts or declines an offer. Only an accept changes the chore; both record the answer and its time.
    suspend fun respondToTransfer(householdId: String, request: ChoreTransferRequest, accept: Boolean): Result<Unit>
}

class ChoreTransferRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) : ChoreTransferActions {

    private fun household(householdId: String) = db.collection("households").document(householdId)
    private fun transfers(householdId: String) = household(householdId).collection("choreTransfers")
    private fun sharedChores(householdId: String) = household(householdId).collection("chores")

    // Merges two live queries (offers sent to me, offers sent by me) into one list without duplicates.
    // Firestore cannot OR two different fields in one query, hence the two listeners.
    override fun observeTransfers(householdId: String, myUid: String): Flow<List<ChoreTransferRequest>> =
        combine(
            listen(transfers(householdId).whereEqualTo("toUid", myUid)),
            listen(transfers(householdId).whereEqualTo("fromUid", myUid))
        ) { received, sent -> (received + sent).distinctBy { it.id } }

    // Turns a Firestore query into a Flow of requests that updates live and removes its listener when
    // the collector goes away. An error makes the flow emit an empty list instead of crashing.
    private fun listen(query: Query): Flow<List<ChoreTransferRequest>> = callbackFlow {
        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(snapshot?.toObjects(ChoreTransferDoc::class.java)?.map { it.toDomain() } ?: emptyList())
        }
        awaitClose { registration.remove() }
    }.catch { emit(emptyList()) }

    // Writes the offer under an id built from chore, due date and receiver, so one roommate can only ever
    // have one request document per occurrence. The write is not awaited, so it also works offline.
    // Callers must not send an id twice: writing over an existing request would reset its answer.
    // The chore stays with its current assignee.
    override suspend fun sendTransfer(householdId: String, request: ChoreTransferRequest): Result<Unit> =
        runCatching {
            require(request.fromUid != request.toUid) { "Pick a different roommate" }
            transfers(householdId).document(request.id).set(request.toData())
                .addOnFailureListener { Log.e("Chore Transfer", "request failed on the server") }
            Unit
        }

    // Answers an offer inside one transaction (needs a connection, so two phones can't both win).
    // Decline: only the request is marked DECLINED; the chore is not touched.
    // Accept: the request must still be open and the chore must still be exactly what was offered
    //   (not finished, still with the sender, still the same due date). Then the chore moves to the receiver
    //   and the original assignee is recorded once per occurrence. If it fails any check, nothing changes.
    override suspend fun respondToTransfer(
        householdId: String,
        request: ChoreTransferRequest,
        accept: Boolean
    ): Result<Unit> = runCatching {
        val requestRef = transfers(householdId).document(request.id)
        val choreRef = sharedChores(householdId).document(request.choreId)
        db.runTransaction { tx ->
            val requestSnap = tx.get(requestRef)
            val choreSnap = tx.get(choreRef)
            check(requestSnap.getString("status") == TransferStatus.PENDING) { "This request is no longer open" }
            if (accept) {
                val stored = choreSnap.toObject(ChoreDoc::class.java)?.toDomain()
                check(stored != null) { "This chore no longer exists" }
                // The list shows a finished repeating chore as its next occurrence once the date has passed.
                // Check against that same view, not the older saved copy.
                val current = resolveOccurrence(stored, todayEpochDay())
                check(!current.done) { "This chore is already finished" }
                check(current.assigneeUid == request.fromUid) { "This chore is no longer with ${request.fromName}" }
                check(current.dueEpochDay == request.dueEpochDay) { "This chore has moved on to a later date" }

                val changes = mutableMapOf<String, Any>(
                    "assigneeUid" to request.toUid,
                    "assigneeName" to request.toName
                )
                if (current != stored) {
                    // save the rollover together with the hand-over
                    changes["dueEpochDay"] = current.dueEpochDay
                    changes["done"] = false
                    changes["nextAssigneeUid"] = ""
                    changes["nextAssigneeName"] = ""
                }
                // keep who it was first given to, once per occurrence
                val alreadyRecorded = current.originalForEpochDay == request.dueEpochDay &&
                        current.originalAssigneeUid.isNotBlank()
                if (!alreadyRecorded) {
                    changes["originalAssigneeUid"] = request.fromUid
                    changes["originalAssigneeName"] = request.fromName
                    changes["originalForEpochDay"] = request.dueEpochDay
                }
                tx.update(choreRef, changes)
            }
            val answer = if (accept) TransferStatus.ACCEPTED else TransferStatus.DECLINED
            tx.update(requestRef, mapOf("status" to answer, "respondedAt" to FieldValue.serverTimestamp()))
            null
        }.await()
        Unit
    }
}
'''

# ---------------------------------------------------------------------------
# VacationSetupViewModel.kt
# ---------------------------------------------------------------------------
VACATION_VM = r'''package com.app.haven.viewmodel.vacation

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

    // Old manual-pick state. sendRequests no longer reads it; delete together with the checkboxes and
    // recipient pickers in the screen.
    private val _selected = MutableStateFlow<Set<String>>(emptySet())      // chore ids
    val selected: StateFlow<Set<String>> = _selected.asStateFlow()

    private val _recipient = MutableStateFlow<Map<String, String>>(emptyMap())   // chore id -> roommate uid
    val recipient: StateFlow<Map<String, String>> = _recipient.asStateFlow()

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

    fun toggle(choreId: String) {
        val now = _selected.value
        _selected.value = if (choreId in now) now - choreId else now + choreId
    }

    // Old "Select all" switch. See the note on _selected.
    fun setAll(choreIds: List<String>, on: Boolean) {
        _selected.value = if (on) choreIds.toSet() else emptySet()
    }

    fun pickRecipient(choreId: String, uid: String) {
        _recipient.value = _recipient.value + (choreId to uid)
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
'''

write("AutoAssign.kt", AUTO_ASSIGN)
write("VacationRules.kt", VACATION_RULES)
write("ChoreTransferActions.kt", TRANSFER_ACTIONS)
write("VacationSetupViewModel.kt", VACATION_VM)

# AddChoreViewModel.kt: comments only, behaviour unchanged
patch(
    "AddChoreViewModel.kt",
    """    // Reads membership, history and workload fresh every time, never from an earlier result.
    // A repeating chore gets the complete priority order, which is saved on it and never recalculated.
    // A one-time chore only gets its highest-priority roommate, with no order.""",
    """    // Decides who gets an automatically assigned chore.
    // Loads members, completions and open chores ONCE from Firebase (fresh, never from an earlier result),
    // then works out the whole priority order locally. Nothing is fetched per roommate.
    // A repeating chore gets the complete priority order, which is saved on it and never recalculated.
    // A one-time chore only gets its highest-priority roommate, with no order.""",
)
patch(
    "AddChoreViewModel.kt",
    "    // One seed per form, so the preview and the save break a tie the same way while nothing has changed",
    "    // One random seed per form. The preview and the save both shuffle with it, so when two roommates are\n"
    "    // completely tied they are put in the same order in both, as long as nothing else has changed.",
)
print("done")
