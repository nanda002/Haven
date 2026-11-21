package com.app.haven.domain.chore

// One finished occurrence of a shared household chore. Written when someone ticks the chore and
// removed again if the tick is undone. Contribution numbers are always calculated from these records.
data class ChoreCompletion(
    val id: String,
    val choreId: String,
    val choreTitle: String,
    val completedByUid: String,   // who actually did it, which can differ from the assignee
    val completedByName: String,
    val assignedToUid: String,    // who it was assigned to at that time
    val dueEpochDay: Long,        // the scheduled date of the occurrence
    val completedEpochDay: Long,
    val originalAssigneeUid: String = ""   // who it was first given to, which differs from assignedToUid after a hand-over
)

// One record per occurrence, so an occurrence can never be counted twice (repeated taps or syncs)
fun completionId(choreId: String, dueEpochDay: Long): String = "${choreId}_$dueEpochDay"