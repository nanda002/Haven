package com.app.haven.data.model.chore

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date
import com.app.haven.domain.chore.ChoreTransferRequest as DomainTransfer

// households/{householdId}/choreTransfers/{choreId_dueEpochDay_toUid}
data class ChoreTransferDoc(
    @DocumentId val id: String = "",
    val choreId: String = "",
    val choreTitle: String = "",
    val dueEpochDay: Long = 0L,
    val fromUid: String = "",
    val fromName: String = "",
    val toUid: String = "",
    val toName: String = "",
    val status: String = "pending",
    @ServerTimestamp val createdAt: Date? = null,
    val respondedAt: Date? = null
)

fun ChoreTransferDoc.toDomain() = DomainTransfer(
    id = id, choreId = choreId, choreTitle = choreTitle, dueEpochDay = dueEpochDay,
    fromUid = fromUid, fromName = fromName, toUid = toUid, toName = toName, status = status,
    createdAtMillis = createdAt?.time, respondedAtMillis = respondedAt?.time
)

// the document id is the request id, so it isn't a stored field
fun DomainTransfer.toData() = ChoreTransferDoc(
    choreId = choreId, choreTitle = choreTitle, dueEpochDay = dueEpochDay,
    fromUid = fromUid, fromName = fromName, toUid = toUid, toName = toName, status = status
)
