package com.app.haven.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date
import com.app.haven.domain.chore.ChoreCompletion as DomainCompletion

// households/{householdId}/choreCompletions/{choreId_dueEpochDay}
data class ChoreCompletionDoc(
    @DocumentId val id: String = "",
    val choreId: String = "",
    val choreTitle: String = "",
    val completedByUid: String = "",
    val completedByName: String = "",
    val assignedToUid: String = "",
    val dueEpochDay: Long = 0L,
    val completedEpochDay: Long = 0L,
    @ServerTimestamp val completedAt: Date? = null
)

fun ChoreCompletionDoc.toDomain() = DomainCompletion(
    id = id, choreId = choreId, choreTitle = choreTitle,
    completedByUid = completedByUid, completedByName = completedByName,
    assignedToUid = assignedToUid, dueEpochDay = dueEpochDay, completedEpochDay = completedEpochDay
)

// the document id is the occurrence id, so it isn't a stored field
fun DomainCompletion.toData() = ChoreCompletionDoc(
    choreId = choreId, choreTitle = choreTitle,
    completedByUid = completedByUid, completedByName = completedByName,
    assignedToUid = assignedToUid, dueEpochDay = dueEpochDay, completedEpochDay = completedEpochDay
)