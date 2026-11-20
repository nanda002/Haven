package com.app.haven.domain.chore

data class AssigneeRef(val uid: String, val name: String)

// Who this occurrence was first given to. It stays the same after a hand-over, while assigneeUid
// always means whoever is responsible right now. The stored original only applies to the occurrence
// it was recorded for, so the next occurrence of a repeating chore starts fresh.
fun Chore.originalAssignee(): AssigneeRef =
    if (originalAssigneeUid.isNotBlank() && originalForEpochDay == dueEpochDay)
        AssigneeRef(originalAssigneeUid, originalAssigneeName)
    else
        AssigneeRef(assigneeUid, assigneeName)
