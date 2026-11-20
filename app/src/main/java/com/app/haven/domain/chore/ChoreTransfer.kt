package com.app.haven.domain.chore

object TransferStatus {
    const val PENDING = "pending"
    const val ACCEPTED = "accepted"
    const val DECLINED = "declined"
}

// One offer of a chore to another roommate. Nothing moves until the receiver accepts.
// Accepted and declined offers stay on record as the history of who handed what to whom.
data class ChoreTransferRequest(
    val id: String,
    val choreId: String,
    val choreTitle: String,
    val dueEpochDay: Long,          // the occurrence being handed over
    val fromUid: String,            // who is handing it over
    val fromName: String,
    val toUid: String,              // who it is offered to
    val toName: String,
    val status: String = TransferStatus.PENDING,
    val createdAtMillis: Long? = null,
    val respondedAtMillis: Long? = null   // when it was accepted or declined
)

// one request per occurrence and receiver, so offering the same chore to the same person again replaces the old one
fun transferId(choreId: String, dueEpochDay: Long, toUid: String): String = "${choreId}_${dueEpochDay}_$toUid"
