package com.app.haven.domain.chore

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class Chore(
    val id: String = "",
    val title: String,
    val assigneeUid: String,
    val assigneeName: String,
    val dueEpochDay: Long,
    val repeatDays: Int = 0,               // 0 = one-off, 1 = daily, 7 = weekly
    val rotate: Boolean = true,            // repeating only: pass to the next roommate on completion
    val endEpochDay: Long = 0L,            // repeating only: last day a repeat can be due, 0 = forever
    val done: Boolean = false,
    val completedByUid: String? = null,
    val completedByName: String? = null,   // stored so it still reads right after a member leaves
    val lastCompletedEpochDay: Long = 0L
)

data class Assignee(val uid: String, val name: String)

enum class ChoreStatus { DONE, OVERDUE, DUE_TODAY, UPCOMING }

fun todayEpochDay(nowMillis: Long = System.currentTimeMillis(), tz: TimeZone = TimeZone.getDefault()): Long =
    (nowMillis + tz.getOffset(nowMillis)) / 86_400_000L

// "Thu, Nov 20". Epoch days are calendar days, so format in UTC (no timezone shift)
fun formatEpochDay(epochDay: Long): String {
    val f = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
    f.timeZone = TimeZone.getTimeZone("UTC")
    return f.format(Date(epochDay * 86_400_000L))
}

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

// "Done by Sam today" (finished) / "Last done by Sam yesterday" (repeating, still going)
fun Chore.lastDoneLabel(today: Long): String? {
    val by = completedByName ?: return null
    val ago = today - lastCompletedEpochDay
    val whenText = when {
        ago <= 0L -> "today"
        ago == 1L -> "yesterday"
        else -> "$ago days ago"
    }
    return (if (done) "Done by " else "Last done by ") + "$by $whenText"
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

// "Repeat for N weeks" -> last day a repeat may be due. 0 weeks (or a one-off) = no end.
fun endEpochDayFor(dueEpochDay: Long, repeatDays: Int, weeks: Int): Long =
    if (repeatDays <= 0 || weeks <= 0) 0L else dueEpochDay + weeks * 7L

fun completeChore(chore: Chore, members: List<Assignee>, completedBy: Assignee, today: Long): Chore {
    val stamped = chore.copy(
        completedByUid = completedBy.uid,
        completedByName = completedBy.name,
        lastCompletedEpochDay = today
    )
    if (chore.repeatDays <= 0) return stamped.copy(done = true)

    val next = nextDueDay(chore.dueEpochDay, chore.repeatDays, today)
    // the run is over: the next repeat would land after the end date
    if (chore.endEpochDay > 0L && next > chore.endEpochDay) return stamped.copy(done = true)

    val assignee = if (chore.rotate) nextAssignee(members, chore.assigneeUid) else null
    return stamped.copy(
        assigneeUid = assignee?.uid ?: chore.assigneeUid,
        assigneeName = assignee?.name ?: chore.assigneeName,
        dueEpochDay = next,
        done = false
    )
}

// un-ticking a finished chore
fun Chore.reopen(): Chore = copy(
    done = false, completedByUid = null, completedByName = null, lastCompletedEpochDay = 0L
)

fun validateChore(title: String, assigneeUid: String?): String? = when {
    title.isBlank() -> "Enter a chore name"
    assigneeUid.isNullOrBlank() -> "Pick who it's for"
    else -> null
}