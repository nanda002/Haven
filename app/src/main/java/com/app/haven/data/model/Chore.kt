package com.app.haven.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date
import com.app.haven.domain.chore.ChoreCategory
import com.app.haven.domain.chore.Chore as DomainChore

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
    @ServerTimestamp val createdAt: Date? = null
)

fun Chore.toDomain() = DomainChore(
    id = id, title = title, assigneeUid = assigneeUid, assigneeName = assigneeName,
    dueEpochDay = dueEpochDay, repeatDays = repeatDays, rotate = rotate, endEpochDay = endEpochDay,
    done = done, completedByUid = completedByUid, completedByName = completedByName,
    lastCompletedEpochDay = lastCompletedEpochDay,
    category = ChoreCategory.fromKey(category).key,   // unknown key -> "other", never crashes
    notes = notes,
    nextAssigneeUid = nextAssigneeUid, nextAssigneeName = nextAssigneeName
)

fun DomainChore.toData() = Chore(
    title = title, assigneeUid = assigneeUid, assigneeName = assigneeName,
    dueEpochDay = dueEpochDay, repeatDays = repeatDays, rotate = rotate, endEpochDay = endEpochDay,
    done = done, completedByUid = completedByUid, completedByName = completedByName,
    lastCompletedEpochDay = lastCompletedEpochDay,
    category = category,
    notes = notes,
    nextAssigneeUid = nextAssigneeUid, nextAssigneeName = nextAssigneeName
)