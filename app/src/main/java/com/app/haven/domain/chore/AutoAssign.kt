package com.app.haven.domain.chore

import kotlin.random.Random

const val WORKLOAD_WINDOW_DAYS = 7L

const val NO_ELIGIBLE_MEMBER_MESSAGE =
    "No roommate is available to take this chore: everyone has left or is on vacation."

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
    // A day counts only if they were in the household that day and not away on vacation.
    // Work done before they joined is never theirs, and vacation days are not days they were expected to help.
    fun participatedOn(day: Long): Boolean =
        inHousehold(day) &&
                vacationPeriods.none { day >= it.startEpochDay && day <= it.endEpochDay }

    private fun inHousehold(day: Long): Boolean =
        day >= joinedEpochDay ||
                pastMemberships.any { day >= it.joinedEpochDay && day <= it.leftEpochDay }
}

// Only pass shared household chore completions into these functions.
// Personal chore completions must be excluded by the repository.
fun contributionCount(
    member: HouseholdMember,
    completions: List<ChoreCompletion>
): Int =
    completions.count {
        it.completedByUid == member.uid &&
                member.participatedOn(it.completedEpochDay)
    }

// Calculate contribution percentages from actual completion history.
// Percentages are calculated among the supplied active, eligible members, so they add up to 100.
fun contributionPercentages(
    members: List<HouseholdMember>,
    completions: List<ChoreCompletion>
): Map<String, Double> {
    val counts = members.associate {
        it.uid to contributionCount(it, completions)
    }

    val total = counts.values.sum()

    return counts.mapValues { (_, count) ->
        if (total == 0) 0.0
        else count * 100.0 / total
    }
}

// Members who are currently active and not on vacation.
fun eligibleMembers(
    members: List<HouseholdMember>
): List<HouseholdMember> =
    members.filter { it.active && !it.onVacation }

// The last day of the rolling window that starts today. Due dates have no time of day, so a chore is
// due for the whole of its day: a window of seven days from this moment always ends inside day today + 7,
// which makes today and the six days after it the days that fall within it (never a Monday-to-Sunday week).
fun workloadWindowEnd(today: Long): Long = today + WORKLOAD_WINDOW_DAYS - 1

// Outstanding household workload: every unfinished occurrence due in the next seven days, plus every
// unfinished overdue occurrence however old. Each occurrence is one unit, whatever the chore is.
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

private fun occurrencesFor(chore: Chore, uid: String, today: Long): Int {
    val windowEnd = workloadWindowEnd(today)
    return when {
        chore.done -> upcomingAfterFinished(chore, uid, windowEnd)
        chore.assigneeUid == uid -> currentAndUpcoming(chore, today, windowEnd)
        else -> 0
    }
}

// The unfinished occurrence in hand, plus the later ones of a same-person chore that fall in the window
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

// A finished occurrence is not counted, but a repeating chore has more coming
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

// first, first + interval, ... up to the end of the window or the chore's end date
private fun datesInWindow(chore: Chore, first: Long, windowEnd: Long): Int {
    var count = 0
    var next = first
    while (next <= windowEnd && isAllowedOccurrence(next, chore.endEpochDay)) {
        count++
        next += chore.repeatDays
    }
    return count
}

// A rate is never worked out over fewer active days than this, so a very recent join date can't make it unstable.
const val MIN_ACTIVE_DAYS = 7L

const val DAYS_PER_WEEK = 7.0

// Days a roommate has been active in the household, from when they joined up to and including today:
// every day of every stay (earlier stays included) except the days they were on vacation.
fun activeDays(member: HouseholdMember, today: Long): Long {
    val firstDay = minOf(
        member.joinedEpochDay,
        member.pastMemberships.minOfOrNull { it.joinedEpochDay } ?: member.joinedEpochDay
    )
    if (today < firstDay) return 0L
    return (firstDay..today).count { member.participatedOn(it) }.toLong()
}

// The primary assignment ranking number: average household chores completed per active week.
// Completions made on vacation days or before joining are not counted (see contributionCount), and the
// active time never drops below MIN_ACTIVE_DAYS.
fun completionRatePerWeek(
    member: HouseholdMember,
    completions: List<ChoreCompletion>,
    today: Long
): Double =
    contributionCount(member, completions) * DAYS_PER_WEEK / maxOf(activeDays(member, today), MIN_ACTIVE_DAYS)

// Every eligible roommate, highest priority first. Ranked by:
//   1. lowest completion rate per active week
//   2. lowest contribution percentage
//   3. lowest upcoming workload
//   4. random, for anyone still tied (the list is shuffled first and the sort keeps that order for ties)
// Inactive and vacationing members are left out. Personal chores never reach this: the repository only
// supplies household chores and completions.
fun automaticPriorityOrder(
    members: List<HouseholdMember>,
    completions: List<ChoreCompletion>,
    openChores: List<Chore>,
    today: Long,
    random: Random = Random.Default
): List<HouseholdMember> {
    val eligible = eligibleMembers(members)

    val rates = eligible.associate { it.uid to completionRatePerWeek(it, completions, today) }
    val shares = contributionPercentages(eligible, completions)
    val workloads = eligible.associate { it.uid to workloadOf(it.uid, openChores, today) }

    return eligible.shuffled(random).sortedWith(
        compareBy<HouseholdMember> { rates.getValue(it.uid) }
            .thenBy { shares.getValue(it.uid) }
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
