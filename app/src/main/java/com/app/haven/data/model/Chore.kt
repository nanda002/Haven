package com.app.haven.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date
import com.app.haven.domain.chore.Chore as DomainChore

data class Chore(
    @DocumentId val id: String = "",
    val title: String = "",
    val assigneeUid: String = "",
    val assigneeName: String = "",
    val dueEpochDay: Long = 0L,
    val repeatDays: Int = 0,
    val done: Boolean = false,
    val completedByUid: String? = null,
    @ServerTimestamp val createdAt: Date? = null
)

fun Chore.toDomain() = DomainChore(
    id = id, title = title, assigneeUid = assigneeUid, assigneeName = assigneeName,
    dueEpochDay = dueEpochDay, repeatDays = repeatDays, done = done, completedByUid = completedByUid
)

fun DomainChore.toData() = Chore(
    title = title, assigneeUid = assigneeUid, assigneeName = assigneeName,
    dueEpochDay = dueEpochDay, repeatDays = repeatDays, done = done, completedByUid = completedByUid
)