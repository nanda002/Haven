package com.app.haven.domain.chore

import java.util.TimeZone

data class Chore(
    val id: String = "",
    val title: String,
    val assigneeUid: String,
    val assigneeName: String,
    val dueEpochDay: Long,
    val repeatDays: Int = 0,          // 0 = one-off, 1 = daily, 7 = weekly
    val done: Boolean = false,
    val completedByUid: String? = null
)

data class Assignee(val uid: String, val name: String)

enum class ChoreStatus { DONE, OVERDUE, DUE_TODAY, UPCOMING }

fun todayEpochDay(nowMillis: Long = System.currentTimeMillis(), tz: TimeZone = TimeZone.getDefault()): Long =
    (nowMillis + tz.getOffset(nowMillis)) / 86_400_000L

fun Chore.status(today: Long): ChoreStatus = when {
    done -> ChoreStatus.DONE
    dueEpochDay < today -> ChoreStatus.OVERDUE
    dueEpochDay == today -> ChoreStatus.DUE_TODAY
    else -> ChoreStatus.UPCOMING
}

fun Chore.dueLabel(today: Long): String {
    val days = dueEpochDay - today
    return when (status(today)) {
        ChoreStatus.DONE -> "Done"
        ChoreStatus.OVERDUE -> if (-days == 1L) "Overdue by 1 day" else "Overdue by ${-days} days"
        ChoreStatus.DUE_TODAY -> "Due today"
        ChoreStatus.UPCOMING -> if (days == 1L) "Due tomorrow" else "Due in $days days"
    }
}

fun repeatLabel(repeatDays: Int): String? = when (repeatDays) {
    0 -> null
    1 -> "Repeats daily"
    7 -> "Repeats weekly"
    else -> "Repeats every $repeatDays days"
}

// ROUND ROBIN: fixed order (sorted by uid), next person, wrap around.
// If the current person left, start from the top.
fun nextAssignee(members: List<Assignee>, currentUid: String?): Assignee? {
    if (members.isEmpty()) return null
    val sorted = members.sortedBy { it.uid }
    val i = sorted.indexOfFirst { it.uid == currentUid }
    return if (i == -1) sorted.first() else sorted[(i + 1) % sorted.size]
}

fun nextDueDay(dueEpochDay: Long, repeatDays: Int, today: Long): Long {
    require(repeatDays > 0) { "Chore doesn't repeat" }
    var next = dueEpochDay + repeatDays
    while (next < today) next += repeatDays
    return next
}

fun completeChore(chore: Chore, members: List<Assignee>, completedByUid: String, today: Long): Chore {
    if (chore.repeatDays <= 0) return chore.copy(done = true, completedByUid = completedByUid)
    val next = nextAssignee(members, chore.assigneeUid) ?: Assignee(chore.assigneeUid, chore.assigneeName)
    return chore.copy(
        assigneeUid = next.uid,
        assigneeName = next.name,
        dueEpochDay = nextDueDay(chore.dueEpochDay, chore.repeatDays, today),
        done = false,
        completedByUid = completedByUid
    )
}

fun validateChore(title: String, assigneeUid: String?): String? = when {
    title.isBlank() -> "Enter a chore name"
    assigneeUid.isNullOrBlank() -> "Pick who it's for"
    else -> null
}