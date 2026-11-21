package com.app.haven.domain.chore

// Unfinished shared chores that are with this person right now. All of them have to be finished or
// handed over before Vacation Mode can start. Finished chores and personal chores are never in the list.
fun pendingForVacation(chores: List<Chore>, uid: String): List<Chore> =
    chores
        .filter { !it.done && it.scope == ChoreScope.HOUSEHOLD && it.assigneeUid == uid }
        .sortedBy { it.dueEpochDay }

fun vacationBlockedMessage(pendingCount: Int): String? = when (pendingCount) {
    0 -> null
    1 -> "1 chore still needs to be finished or handed over"
    else -> "$pendingCount chores still need to be finished or handed over"
}

// The offer to show next to a chore: one that is waiting for an answer first, otherwise one that was declined
fun offerFor(requests: List<ChoreTransferRequest>, chore: Chore): ChoreTransferRequest? {
    val forThisOccurrence = requests.filter { it.choreId == chore.id && it.dueEpochDay == chore.dueEpochDay }
    return forThisOccurrence.firstOrNull { it.status == TransferStatus.PENDING }
        ?: forThisOccurrence.firstOrNull { it.status == TransferStatus.DECLINED }
}
