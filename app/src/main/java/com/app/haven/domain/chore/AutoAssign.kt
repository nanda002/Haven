package com.app.haven.domain.chore

import kotlin.random.Random

const val WORKLOAD_WINDOW_DAYS = 7L

const val NO_ELIGIBLE_MEMBER_MESSAGE = "No roommate is available to take this chore: everyone has left or is on vacation."

// A stretch of days a member was on vacation. The end is the last day away.
data class VacationPeriod(
    val startEpochDay: Long,
    val endEpochDay: Long = Long.MAX_VALUE
)

// An earlier stay in the household. Kept when someone leaves, so their old work still counts for them
// if they ever come back.
data class MembershipPeriod(
    val joinedEpochDay: Long,
    val leftEpochDay: Long
)

// A roommate as the assignment logic sees them.
data class HouseholdMember(
    val uid: String,
    val name: String,
    val joinedEpochDay: Long,                  // start of the current stay
    val active: Boolean = true,
    val onVacation: Boolean = false,
    val vacationPeriods: List<VacationPeriod> = emptyList(),
    val pastMemberships: List<MembershipPeriod> = emptyList()
) {
    // True if the member was in the household on this day and not away on vacation.
    // Work done before they joined is never theirs, and vacation days are not days they were expected to help.
    fun participatedOn(day: Long): Boolean =
        inHousehold(day) &&
                vacationPeriods.none { day >= it.startEpochDay && day <= it.endEpochDay }

    // True if the day falls in the current stay or in any earlier stay.
    private fun inHousehold(day: Long): Boolean =
        day >= joinedEpochDay ||
                pastMemberships.any { day >= it.joinedEpochDay && day <= it.leftEpochDay }

    // The first day this member ever belonged to the household (earliest past stay or current stay).
    fun firstDayInHousehold(): Long =
        minOf(joinedEpochDay, pastMemberships.minOfOrNull { it.joinedEpochDay } ?: Long.MAX_VALUE)
}

// How many of the given completions belong to this member.
// Only pass shared household chore completions into this.
// A completion counts only if this member did it AND it happened on a day they participated.
// The second check protects against wrong values in the database (work dated before they joined
// or during a vacation) skewing the numbers.
fun contributionCount(
    member: HouseholdMember,
    completions: List<ChoreCompletion>
): Int =
    completions.count {
        it.completedByUid == member.uid &&
                member.participatedOn(it.completedEpochDay)
    }

// Each roommate's percentage of all household chores completed, compared over the one period in which
// every listed roommate was present. This is a plain "who did how much" view and does NOT account for
// how long each person has lived there. Automatic assignment uses contributionShortfalls instead.
fun contributionPercentages(
    members: List<HouseholdMember>,
    completions: List<ChoreCompletion>
): Map<String, Double> {
    val shared = completions.filter { done -> members.all { it.participatedOn(done.completedEpochDay) } }
    val counts = members.associate { it.uid to contributionCount(it, shared) }

    val total = counts.values.sum()

    return counts.mapValues { (_, count) ->
        if (total == 0) 0.0
        else count * 100.0 / total
    }
}

// Members who are currently active and not on vacation. Only these can receive a new automatic chore.
fun eligibleMembers(
    members: List<HouseholdMember>
): List<HouseholdMember> =
    members.filter { it.active && !it.onVacation }

// Number of days from fromDay to toDay (both included) on which this member took part in the household,
// meaning they had joined and were not on vacation. This is the member's "eligible time".
fun eligibleDays(member: HouseholdMember, fromDay: Long, toDay: Long): Int {
    if (toDay < fromDay) return 0
    var count = 0
    for (day in fromDay..toDay) {
        if (member.participatedOn(day)) count++
    }
    return count
}

// How far behind (positive) or ahead (negative) each member is compared with a fair share of the work.
//   expected share = the member's eligible days / everyone's eligible days combined
//   actual share   = the member's completed chores / everyone's completed chores combined
//   shortfall      = expected share - actual share
// Time is measured from the earliest join date among the given members up to today, so someone who joined
// yesterday has a tiny expected share and is not treated as equally behind as someone who has lived there
// for months. When nobody has completed anything yet, the shortfall is just the expected share,
// so the person with the most eligible time is the one most "owed" a chore.
fun contributionShortfalls(
    members: List<HouseholdMember>,
    completions: List<ChoreCompletion>,
    today: Long
): Map<String, Double> {
    if (members.isEmpty()) return emptyMap()

    val windowStart = members.minOf { it.firstDayInHousehold() }
    val days = members.associate { it.uid to eligibleDays(it, windowStart, today) }
    val totalDays = days.values.sum()

    val counts = members.associate { it.uid to contributionCount(it, completions) }
    val totalCount = counts.values.sum()

    return members.associate { member ->
        val expected =
            if (totalDays == 0) 1.0 / members.size
            else days.getValue(member.uid).toDouble() / totalDays
        val actual =
            if (totalCount == 0) 0.0
            else counts.getValue(member.uid).toDouble() / totalCount
        member.uid to (expected - actual)
    }
}

// The last day of the rolling window that starts today. Due dates have no time of day, so a chore is
// due for the whole of its day: a window of seven days from this moment always ends inside day today + 7,
// which makes today and the six days after it the days that fall within it (never a Monday-to-Sunday week).
fun workloadWindowEnd(today: Long): Long = today + WORKLOAD_WINDOW_DAYS - 1

// Outstanding household workload of one person: every unfinished occurrence due in the next seven days,
// plus every unfinished overdue occurrence however old. Each occurrence is one unit, whatever the chore is.
// A chore counts for whoever is responsible for it right now, so a handed-over chore counts for the new person.
// Personal chores never count.
fun workloadOf(
    uid: String,
    openChores: List<Chore>,
    today: Long
): Int =
    openChores
        .filter { it.scope == ChoreScope.HOUSEHOLD }
        .sumOf { occurrencesFor(it, uid, today) }

// How many occurrences of one chore count towards this person's workload.
// A finished chore only counts through its coming repetitions. An unfinished chore counts only for its
// current assignee (current occurrence plus later ones that fall inside the window). Anyone else gets 0.
private fun occurrencesFor(chore: Chore, uid: String, today: Long): Int {
    val windowEnd = workloadWindowEnd(today)
    return when {
        chore.done -> upcomingAfterFinished(chore, uid, windowEnd)
        chore.assigneeUid == uid -> currentAndUpcoming(chore, today, windowEnd)
        else -> 0
    }
}

// Workload for an unfinished chore held by the person: the occurrence in hand, plus the later ones
// of a same-person (non-rotating) repeating chore that fall inside the window.
private fun currentAndUpcoming(chore: Chore, today: Long, windowEnd: Long): Int {
    // not overdue and too far away
    if (chore.dueEpochDay > windowEnd) return 0

    // an overdue occurrence counts once, however long ago it was due. Later occurrences are not counted
    // until it has been dealt with, so it is never counted twice
    if (chore.dueEpochDay < today) return 1

    var count = 1
    if (chore.repeatDays > 0 && !chore.rotate) {
        count += datesInWindow(chore, chore.dueEpochDay + chore.repeatDays, windowEnd)
    }
    return count
}

// Workload for a finished chore: the finished occurrence itself is not counted, but a repeating chore
// still has more coming. A rotating chore only has its very next turn handed out; a same-person chore
// has every remaining occurrence in the window.
private fun upcomingAfterFinished(chore: Chore, uid: String, windowEnd: Long): Int {
    if (chore.repeatDays <= 0) return 0
    val first = nextDueDay(chore.dueEpochDay, chore.repeatDays)
    if (first > windowEnd || !isAllowedOccurrence(first, chore.endEpochDay)) return 0

    return if (chore.rotate) {
        // only the very next turn has been handed out: it belongs to whoever completing the chore chose
        val nextUid = chore.nextAssigneeUid.ifBlank { chore.assigneeUid }
        if (nextUid == uid) 1 else 0
    } else {
        // same person: every remaining occurrence in the window is theirs
        if (chore.assigneeUid == uid) datesInWindow(chore, first, windowEnd) else 0
    }
}

// Counts the due dates first, first + interval, first + 2 * interval, ... that are inside the window
// and not past the chore's end date.
private fun datesInWindow(chore: Chore, first: Long, windowEnd: Long): Int {
    var count = 0
    var next = first
    while (next <= windowEnd && isAllowedOccurrence(next, chore.endEpochDay)) {
        count++
        next += chore.repeatDays
    }
    return count
}

// Every eligible roommate, highest priority first:
//   1. Largest shortfall first: the person furthest below their fair share of the work, where a fair share
//      is based on the days they were actually in the household and not on vacation (see contributionShortfalls).
//      Having no completed chores gives no special priority on its own; a brand new roommate only
//      ranks high if their short time in the household still leaves them behind.
//   2. Shortfall tied: lowest upcoming workload.
//   3. Both tied: random (the list is shuffled first and the sorts keep that order for ties).
// Inactive and vacationing members are left out. Personal chores never reach this: the repository only
// supplies household chores and completions.
// Everything is calculated locally from the lists passed in; nothing is fetched here, so the caller
// loads membership, completions and open chores once per decision.
fun automaticPriorityOrder(
    members: List<HouseholdMember>,
    completions: List<ChoreCompletion>,
    openChores: List<Chore>,
    today: Long,
    random: Random = Random.Default
): List<HouseholdMember> {
    val eligible = eligibleMembers(members)
    if (eligible.isEmpty()) return emptyList()

    val shortfalls = contributionShortfalls(eligible, completions, today)
    val workloads = eligible.associate { it.uid to workloadOf(it.uid, openChores, today) }

    return eligible.shuffled(random).sortedWith(
        // rounded so that shortfalls differing only in the last decimal places still count as tied
        compareByDescending<HouseholdMember> { Math.round(shortfalls.getValue(it.uid) * 1_000_000.0) }
            .thenBy { workloads.getValue(it.uid) }
    )
}

// The highest-priority roommate for a new automatically assigned household chore, or null if nobody is eligible.
fun pickAutomaticAssignee(
    members: List<HouseholdMember>,
    completions: List<ChoreCompletion>,
    openChores: List<Chore>,
    today: Long,
    random: Random = Random.Default
): HouseholdMember? =
    automaticPriorityOrder(members, completions, openChores, today, random).firstOrNull()
