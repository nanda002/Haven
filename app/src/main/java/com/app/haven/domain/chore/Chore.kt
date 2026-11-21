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
    val nextAssigneeName: String = "",
    val scope: String = ChoreScope.HOUSEHOLD,   // "household" or "personal", stored explicitly so it can be filtered
    val ownerUid: String = "",                    // personal chores only: the one person who can see it
    val originalAssigneeUid: String = "",        // who this occurrence was first given to, before any hand-over
    val originalAssigneeName: String = "",
    val originalForEpochDay: Long = 0L,          // the occurrence those two belong to, so a new occurrence starts fresh
    val rotationOrder: List<String> = emptyList()   // automatic repeating chores: roommate uids in priority order, saved once and never recalculated
)

data class Assignee(val uid: String, val name: String)

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

const val END_BEFORE_START_MESSAGE = "The end date can't be before the first scheduled date"

// first = the chore's first (current) scheduled date. An end date ON that date is allowed.
fun validateEndDate(firstEpochDay: Long, repeatDays: Int, endEpochDay: Long): String? =
    if (repeatDays > 0 && endEpochDay > 0L && endEpochDay < firstEpochDay) END_BEFORE_START_MESSAGE else null

const val CHORE_NOTES_MAX = 300

// "laundry cleaning" -> "Laundry Cleaning". Every word starts with a capital, the rest of each word
// stays as typed (so "TV room" becomes "TV Room"), and extra spaces are tidied.
fun titleCase(raw: String): String =
    raw.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        .joinToString(" ") { word -> word.replaceFirstChar { it.uppercaseChar() } }

// Notes: only the first character is capitalized, everything else stays exactly as typed
fun capitalizeFirst(raw: String): String = raw.trim().replaceFirstChar { it.uppercaseChar() }

// trim, capitalize the first character, then hard-cap at the limit
fun normalizeNotes(raw: String): String = capitalizeFirst(raw).take(CHORE_NOTES_MAX)
enum class ChoreStatus { DONE, OVERDUE, DUE_TODAY, UPCOMING }

object ChoreScope {
    const val HOUSEHOLD = "household"
    const val PERSONAL = "personal"
}

fun todayEpochDay(nowMillis: Long = System.currentTimeMillis(), tz: TimeZone = TimeZone.getDefault()): Long =
    (nowMillis + tz.getOffset(nowMillis)) / 86_400_000L


// The local calendar day a moment falls on.
fun epochDayOfMillis(
    millis: Long,
    tz: TimeZone = TimeZone.getDefault()
): Long =
    (millis + tz.getOffset(millis)) / 86_400_000L


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

// "Wednesday". Epoch days are calendar days, so format in UTC
fun weekdayName(epochDay: Long, locale: Locale = Locale.getDefault()): String {
    val f = SimpleDateFormat("EEEE", locale)
    f.timeZone = TimeZone.getTimeZone("UTC")
    return f.format(Date(epochDay * 86_400_000L))
}

fun Chore.dueLabel(today: Long): String {
    val days = dueEpochDay - today
    return when (status(today)) {
        ChoreStatus.DONE -> when {
            today - lastCompletedEpochDay <= 0L -> "Done today"
            today - lastCompletedEpochDay == 1L -> "Done yesterday"
            else -> "Done"
        }
        ChoreStatus.OVERDUE -> if (-days == 1L) "Overdue by 1 day" else "Overdue by ${-days} days"
        ChoreStatus.DUE_TODAY -> "Due today"
        ChoreStatus.UPCOMING -> when {
            days == 1L -> "Due tomorrow"
            days < 7L -> "Due ${weekdayName(dueEpochDay)}"
            else -> "Due ${formatEpochDay(dueEpochDay)}"
        }
    }
}

fun repeatLabel(repeatDays: Int): String? = when {
    repeatDays <= 0 -> null
    repeatDays == 1 -> "Repeats daily"
    repeatDays == 7 -> "Repeats weekly"
    repeatDays % 7 == 0 -> "Repeats every ${repeatDays / 7} weeks"   // 14 -> "every 2 weeks"
    else -> "Repeats every $repeatDays days"
}

// "Completed by Sam" (finished) / "Last done by Sam yesterday" (repeating, still going)
fun Chore.lastDoneLabel(today: Long): String? {
    val by = completedByName ?: return null
    if (done) return "Completed by $by"
    val ago = today - lastCompletedEpochDay
    val whenText = when {
        ago <= 0L -> "today"
        ago == 1L -> "yesterday"
        else -> "$ago days ago"
    }
    return "Last done by $by $whenText"
}

// TAKE TURNS: the next turn goes to the roommate after whoever actually did the chore in the chore's saved
// rotation order, wrapping around. The order is saved when the chore is created and never recalculated.
// If the person who did it isn't in the order, count from the current assignee instead; if neither is, start
// at the top. Roommates who are no longer in the household are skipped.
// A chore with no saved order (created before saved orders existed, or assigned manually) falls back to the
// current members sorted by uid, so it keeps rotating and never crashes.
fun nextInRotation(chore: Chore, members: List<Assignee>, completedByUid: String): Assignee? {
    val order = chore.rotationOrder.ifEmpty { members.map { it.uid }.sorted() }
    if (order.isEmpty()) return null
    var anchor = order.indexOf(completedByUid)
    if (anchor == -1) anchor = order.indexOf(chore.assigneeUid)
    for (step in 1..order.size) {
        val uid = order[Math.floorMod(anchor + step, order.size)]
        members.firstOrNull { it.uid == uid }?.let { return it }
    }
    return null
}

// ANCHORED to the schedule: previous scheduled date + interval.
// The completion date is never used, so finishing late or early never shifts the schedule.
fun nextDueDay(dueEpochDay: Long, repeatDays: Int): Long {
    require(repeatDays > 0) { "Chore doesn't repeat" }
    return dueEpochDay + repeatDays
}

// a scheduled date is allowed if it is on or before the end date (0 = no end date).
// The only place that decides this. Completing, rolling over and saving all use it.
fun isAllowedOccurrence(scheduledEpochDay: Long, endEpochDay: Long): Boolean =
    endEpochDay <= 0L || scheduledEpochDay <= endEpochDay

// the final allowed occurrence has been completed, so the recurring chore is retired.
fun Chore.isRetired(): Boolean =
    repeatDays > 0 && done && !isAllowedOccurrence(nextDueDay(dueEpochDay, repeatDays), endEpochDay)

// What gets stored: 0 for a one-off or "No end date", otherwise the calendar date the user picked.
// Not snapped to an occurrence: the last repeat is the last scheduled date on or before it.
fun resolveEndEpochDay(repeatDays: Int, selectedEndEpochDay: Long): Long =
    if (repeatDays <= 0 || selectedEndEpochDay <= 0L) 0L else selectedEndEpochDay

fun cadenceText(repeatDays: Int): String = when {
    repeatDays == 1 -> "every day"
    repeatDays == 7 -> "every week"
    repeatDays > 0 && repeatDays % 7 == 0 -> "every ${repeatDays / 7} weeks"
    else -> "every $repeatDays days"
}

data class PreviewLine(val label: String, val value: String)

// Rows for the live preview under the form. It only describes what the scheduling logic does:
// dates are fixed by the schedule, and each scheduled date goes to the next roommate in order.
fun schedulePreview(
    startEpochDay: Long,
    repeatDays: Int,
    rotate: Boolean,
    assigneeName: String?,
    endEpochDay: Long
): List<PreviewLine> {
    val first = PreviewLine("First date", formatFullEpochDay(startEpochDay))
    if (repeatDays <= 0) return listOf(PreviewLine("Repeats", "Once, no repeats"), first)
    return listOf(
        PreviewLine("Repeats", cadenceText(repeatDays).replaceFirstChar { it.uppercase() }),
        first,
        PreviewLine(
            "Turns",
            if (rotate) "Roommates take turns, one scheduled date each"
            else "Stays with ${assigneeName ?: "the same person"}"
        ),
        PreviewLine("Ends", if (endEpochDay > 0L) formatFullEpochDay(endEpochDay) else "No end date")
    )
}

// Assignee only until the due date has passed, then anyone in the household
fun canComplete(chore: Chore, uid: String, today: Long): Boolean =
    chore.assigneeUid == uid || chore.dueEpochDay < today

fun completionBlockedMessage(chore: Chore, uid: String, today: Long): String? =
    if (canComplete(chore, uid, today)) null
    else "Only ${chore.assigneeName} can complete this until it's overdue"

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

    // nothing after the cutoff is ever scheduled. This was the final allowed occurrence.
    val nextDue = nextDueDay(chore.dueEpochDay, chore.repeatDays)
    if (!isAllowedOccurrence(nextDue, chore.endEpochDay)) return finished

    // The assignee and the due date stay as they are: this occurrence belongs to its assignee until
    // its date passes. The next turn goes to the roommate after whoever actually did it.
    val next = if (chore.rotate) nextInRotation(chore, members, completedBy.uid) else null
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
    if (!isAllowedOccurrence(nextDue, chore.endEpochDay)) return chore
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
    if (repeatDays <= 0 || !done) return null
    if (isRetired()) return "Final repeat completed, nothing more scheduled"
    if (nextAssigneeUid.isBlank()) return null
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