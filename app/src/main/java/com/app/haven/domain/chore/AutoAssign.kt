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

// Select the assignee for a new automatically assigned household chore.
fun pickAutomaticAssignee(
    members: List<HouseholdMember>,
    completions: List<ChoreCompletion>,
    openChores: List<Chore>,
    today: Long,
    random: Random = Random.Default
): HouseholdMember? {

    val eligible = eligibleMembers(members)

    if (eligible.isEmpty()) return null

    if (eligible.size == 1) return eligible.first()

    // Nobody has completed a shared chore yet, so there is nothing to compare. Assigned chores are not
    // contributions and no percentages are made up: choose at random.
    if (eligible.all { contributionCount(it, completions) == 0 }) {
        return eligible.random(random)
    }

    // Once chores are being completed, the lowest actual contribution percentage gets the chore.
    // Someone who hasn't completed anything yet has done 0% of the work so far.
    val shares = contributionPercentages(eligible, completions)
    val lowest = shares.values.min()

    val tiedMembers = eligible.filter {
        shares.getValue(it.uid) == lowest
    }

    // A uniquely lowest contributor always gets the chore, whatever their workload.
    if (tiedMembers.size == 1) {
        return tiedMembers.first()
    }

    // Workload is only used to separate a contribution tie.
    val workloads = tiedMembers.associate {
        it.uid to workloadOf(it.uid, openChores, today)
    }

    val lowestWorkload = workloads.values.min()

    val lightestWorkloadMembers = tiedMembers.filter {
        workloads.getValue(it.uid) == lowestWorkload
    }

    // If both contribution and workload are equal, choose randomly.
    return lightestWorkloadMembers.random(random)
}
