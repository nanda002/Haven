package com.app.haven.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date
import com.app.haven.domain.chore.ChoreCategory
import com.app.haven.domain.chore.Chore as DomainChore
import com.app.haven.domain.chore.ChoreScope

// Firestore data model for a chore
// This class represents how a chore is stored as a document in Firebase.
// Default values allow Firestore to create the object even if some fields are missing
data class Chore(
    @DocumentId val id: String = "",
    val title: String = "",
    val assigneeUid: String = "",
    val assigneeName: String = "",
    val dueEpochDay: Long = 0L,
    val repeatDays: Int = 0,
    val rotate: Boolean = true,
    val endEpochDay: Long = 0L,
    val done: Boolean = false,
    val completedByUid: String? = null,
    val completedByName: String? = null,
    val lastCompletedEpochDay: Long = 0L,
    val category: String = "other",
    val notes: String = "",
    val nextAssigneeUid: String = "",
    val nextAssigneeName: String = "",
    val scope: String = "household",
    val ownerUid: String = "",
    @ServerTimestamp val createdAt: Date? = null
)

// Converts the Firestore Chore model into the domain Chore model
fun Chore.toDomain() = DomainChore(
    id = id, title = title, assigneeUid = assigneeUid, assigneeName = assigneeName,
    dueEpochDay = dueEpochDay, repeatDays = repeatDays, rotate = rotate, endEpochDay = endEpochDay,
    done = done, completedByUid = completedByUid, completedByName = completedByName,
    lastCompletedEpochDay = lastCompletedEpochDay,
    category = ChoreCategory.fromKey(category).key,   // unknown key -> "other", never crashes
    notes = notes,
    nextAssigneeUid = nextAssigneeUid, nextAssigneeName = nextAssigneeName ,
    scope = if (scope == ChoreScope.PERSONAL) ChoreScope.PERSONAL else ChoreScope.HOUSEHOLD,
    ownerUid = ownerUid
)

// Converts the domain Chore model into the Firestore data model
fun DomainChore.toData() = Chore(
    title = title, assigneeUid = assigneeUid, assigneeName = assigneeName,
    dueEpochDay = dueEpochDay, repeatDays = repeatDays, rotate = rotate, endEpochDay = endEpochDay,
    done = done, completedByUid = completedByUid, completedByName = completedByName,
    lastCompletedEpochDay = lastCompletedEpochDay,
    category = category,
    notes = notes,
    nextAssigneeUid = nextAssigneeUid, nextAssigneeName = nextAssigneeName,
    scope = scope, ownerUid = ownerUid
)