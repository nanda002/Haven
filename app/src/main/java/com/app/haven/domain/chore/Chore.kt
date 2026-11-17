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
    val lastCompletedEpochDay: Long = 0L,
    val category: String = "other",         // stable key, see ChoreCategory
    val notes: String = "",
    val nextAssigneeUid: String = "",   // set when a repeating occurrence is completed: who gets the next scheduled date
    val nextAssigneeName: String = ""   // blank = nothing scheduled after this one (one-off or final occurrence)
)

data class Assignee(val uid: String, val name: String, val joinedAtMillis: Long = Long.MAX_VALUE)

// Stored in Firestore as `key`. Never store or rename the label, keys must stay stable.
enum class ChoreCategory(val key: String, val label: String) {
    KITCHEN("kitchen", "Kitchen"),
    BATHROOM("bathroom", "Bathroom"),
    CLEANING("cleaning", "Cleaning"),
    LAUNDRY("laundry", "Laundry"),
    GROCERIES("groceries", "Groceries"),
    PERSONAL("personal", "Personal"),
    OTHER("other", "Other");

    companion object {
        // null, blank or unknown key -> OTHER (old docs, newer app versions)
        fun fromKey(key: String?): ChoreCategory = entries.firstOrNull { it.key == key } ?: OTHER
        fun isValid(key: String?): Boolean = entries.any { it.key == key }
    }
}

// The only recurrence choices on the form. repeatDays is what gets stored.
enum class Recurrence(val repeatDays: Int, val label: String) {
    ONCE(0, "Once"),
    DAILY(1, "Daily"),
    WEEKLY(7, "Weekly"),
    EVERY_2_WEEKS(14, "Every 2 weeks");

    companion object {
        // null = a value that isn't one of the four (older or hand-edited data)
        fun fromRepeatDays(days: Int): Recurrence? = entries.firstOrNull { it.repeatDays == days }
    }
}

const val CATEGORY_REQUIRED_MESSAGE = "Pick a category"

const val CHORE_NOTES_MAX = 300

// trim, then hard-cap at the limit. Used before saving, so storage never exceeds it
fun normalizeNotes(raw: String): String = raw.trim().take(CHORE_NOTES_MAX)
enum class ChoreStatus { DONE, OVERDUE, DUE_TODAY, UPCOMING }

fun todayEpochDay(nowMillis: Long = System.currentTimeMillis(), tz: TimeZone = TimeZone.getDefault()): Long =
    (nowMillis + tz.getOffset(nowMillis)) / 86_400_000L

// "Thu, Nov 20". Epoch days are calendar days, so format in UTC (no timezone shift)
fun formatEpochDay(epochDay: Long): String {
    val f = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
    f.timeZone = TimeZone.getTimeZone("UTC")
    return f.format(Date(epochDay * 86_400_000L))
}

// Epoch days are calendar days, so format in UTC
fun formatFullEpochDay(epochDay: Long, locale: Locale = Locale.getDefault()): String {
    val f = SimpleDateFormat("EEEE, MMMM d, yyyy", locale)
    f.timeZone = TimeZone.getTimeZone("UTC")
    return f.format(Date(epochDay * 86_400_000L))
}

// time left until the next local midnight
fun millisUntilNextLocalMidnight(
    nowMillis: Long = System.currentTimeMillis(),
    tz: TimeZone = TimeZone.getDefault()
): Long {
    val localNow = nowMillis + tz.getOffset(nowMillis)
    return 86_400_000L - Math.floorMod(localNow, 86_400_000L)
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

fun repeatLabel(repeatDays: Int): String? = when {
    repeatDays <= 0 -> null
    repeatDays == 1 -> "Repeats daily"
    repeatDays == 7 -> "Repeats weekly"
    repeatDays % 7 == 0 -> "Repeats every ${repeatDays / 7} weeks"   // 14 -> "every 2 weeks"
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

// STABLE ORDER: when each roommate joined the household (joinedAt), uid only as a tie-break.
// Not alphabetical, not random. Someone who leaves drops out and the rest keep their relative
// order. Someone new joins at the end of the cycle.
fun stableOrder(members: List<Assignee>): List<Assignee> =
    members.sortedWith(compareBy<Assignee> { it.joinedAtMillis }.thenBy { it.uid })

// The roommate after `currentUid` in the stable order, wrapping around.
// If currentUid isn't an active member, count from `fallbackUid`. If that is gone too, start at the top.
fun nextAssignee(members: List<Assignee>, currentUid: String?, fallbackUid: String? = null): Assignee? {
    if (members.isEmpty()) return null
    val order = stableOrder(members)
    var i = order.indexOfFirst { it.uid == currentUid }
    if (i == -1) i = order.indexOfFirst { it.uid == fallbackUid }
    return if (i == -1) order.first() else order[(i + 1) % order.size]
}

// ANCHORED to the schedule: previous scheduled date + interval.
// The completion date is never used, so finishing late or early never shifts the schedule.
fun nextDueDay(dueEpochDay: Long, repeatDays: Int): Long {
    require(repeatDays > 0) { "Chore doesn't repeat" }
    return dueEpochDay + repeatDays
}
// "Repeat for N weeks" -> last day a repeat may be due. 0 weeks (or a one-off) = no end.
fun endEpochDayFor(dueEpochDay: Long, repeatDays: Int, weeks: Int): Long =
    if (repeatDays <= 0 || weeks <= 0) 0L else dueEpochDay + weeks * 7L


// The end date a chore will be saved with. durationWeeks == null = keep the existing end date.
fun resolveEndEpochDay(dueEpochDay: Long, repeatDays: Int, durationWeeks: Int?, existingEnd: Long): Long = when {
    repeatDays <= 0 -> 0L
    durationWeeks == null -> existingEnd
    else -> endEpochDayFor(dueEpochDay, repeatDays, durationWeeks)
}

fun cadenceText(repeatDays: Int): String = when {
    repeatDays == 1 -> "every day"
    repeatDays == 7 -> "every week"
    repeatDays > 0 && repeatDays % 7 == 0 -> "every ${repeatDays / 7} weeks"
    else -> "every $repeatDays days"
}

// Plain-English summary shown under the form
fun schedulePreview(
    startEpochDay: Long,
    repeatDays: Int,
    rotate: Boolean,
    assigneeName: String?,
    endEpochDay: Long
): String {
    val start = formatFullEpochDay(startEpochDay)
    if (repeatDays <= 0) return "Happens once, on $start."
    val who = if (rotate) "with roommates taking turns in order"
    else "staying with ${assigneeName ?: "the same person"} each time"
    val end = if (endEpochDay > 0L) "Runs until ${formatFullEpochDay(endEpochDay)}."
    else "No end date, it keeps going until you delete it."
    return "Starts on $start and repeats ${cadenceText(repeatDays)}, $who. $end"
}

fun completeChore(chore: Chore, members: List<Assignee>, completedBy: Assignee, today: Long): Chore {
    // who actually did it is stored separately from who it was assigned to
    val finished = chore.copy(
        done = true,
        completedByUid = completedBy.uid,
        completedByName = completedBy.name,
        lastCompletedEpochDay = today,
        nextAssigneeUid = "",
        nextAssigneeName = ""
    )
    if (chore.repeatDays <= 0) return finished

    // final allowed occurrence (one ON the end date is allowed): retire, nothing is scheduled after it
    val nextDue = nextDueDay(chore.dueEpochDay, chore.repeatDays)
    if (chore.endEpochDay > 0L && nextDue > chore.endEpochDay) return finished

    // The assignee and the due date stay as they are: this occurrence belongs to its assignee until
    // its date passes. The next turn goes to the roommate after whoever actually did it.
    val next = if (chore.rotate) nextAssignee(members, completedBy.uid, fallbackUid = chore.assigneeUid) else null
    return finished.copy(
        nextAssigneeUid = next?.uid ?: chore.assigneeUid,
        nextAssigneeName = next?.name ?: chore.assigneeName
    )
}

// A completed repeating occurrence stays visible (done, still with its assignee) until its scheduled
// date has passed. Then the next one appears: previous date + interval, the next person, not done.
// It never skips ahead: if that next date is already past, it simply shows as overdue.
// Not applied when the end date would be passed (the chore is retired).
fun resolveOccurrence(chore: Chore, today: Long): Chore {
    if (!chore.done || chore.repeatDays <= 0 || chore.nextAssigneeUid.isBlank()) return chore
    if (today <= chore.dueEpochDay) return chore
    val nextDue = nextDueDay(chore.dueEpochDay, chore.repeatDays)
    if (chore.endEpochDay > 0L && nextDue > chore.endEpochDay) return chore
    val who = if (chore.rotate) Assignee(chore.nextAssigneeUid, chore.nextAssigneeName)
    else Assignee(chore.assigneeUid, chore.assigneeName)
    return chore.copy(
        assigneeUid = who.uid,
        assigneeName = who.name,
        dueEpochDay = nextDue,
        done = false,
        nextAssigneeUid = "",
        nextAssigneeName = ""
    )
}

// "Next: Ben, Wed, Nov 25" on a finished repeating occurrence
fun Chore.upNextLabel(): String? {
    if (!done || repeatDays <= 0 || nextAssigneeUid.isBlank()) return null
    val who = if (rotate) nextAssigneeName else assigneeName
    return "Next: $who, ${formatEpochDay(nextDueDay(dueEpochDay, repeatDays))}"
}

// un-ticking a finished chore
fun Chore.reopen(): Chore = copy(
    done = false, completedByUid = null, completedByName = null, lastCompletedEpochDay = 0L,
    nextAssigneeUid = "", nextAssigneeName = ""
)

fun validateChore(title: String, assigneeUid: String?, category: String?): String? = when {
    title.isBlank() -> "Enter a chore name"
    !ChoreCategory.isValid(category) -> CATEGORY_REQUIRED_MESSAGE
    assigneeUid.isNullOrBlank() -> "Pick who it's for"
    else -> null
}