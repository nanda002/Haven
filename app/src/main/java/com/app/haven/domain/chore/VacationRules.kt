package com.app.haven.domain.chore

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
