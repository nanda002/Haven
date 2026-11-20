package com.app.haven.domain.chore

import kotlin.random.Random

const val WORKLOAD_WINDOW_DAYS = 7L

const val NO_ELIGIBLE_MEMBER_MESSAGE =
    "No roommate is available to take this chore: everyone has left or is on vacation."

// A stretch of days a member was on vacation. The end stays open while it is still going.
data class VacationPeriod(val startEpochDay: Long, val endEpochDay: Long = Long.MAX_VALUE)

// A roommate as the assignment logic sees them
data class HouseholdMember(
    val uid: String,
    val name: String,
    val joinedEpochDay: Long,
    val active: Boolean = true,
    val onVacation: Boolean = false,
    val vacationPeriods: List<VacationPeriod> = emptyList()
) {
    // Whether the member was taking part on this day: they had joined and were not on vacation
    fun participatedOn(day: Long): Boolean =
        day >= joinedEpochDay && vacationPeriods.none { day >= it.startEpochDay && day <= it.endEpochDay }
}

// Still in the household and not on vacation
fun eligibleMembers(members: List<HouseholdMember>): List<HouseholdMember> =
    members.filter { it.active && !it.onVacation }

// Finished household chore occurrences credited to this member while they were taking part.
// Work done before they joined, or while on vacation, is not counted for them.
fun contributionCount(member: HouseholdMember, completions: List<ChoreCompletion>): Int =
    completions.count { it.completedByUid == member.uid && member.participatedOn(it.completedEpochDay) }

// Household Contribution Percentage: each member's share of the finished household chores of the given members,
// calculated fresh from the completion history every time, never stored.
// The shares add up to 100. With nothing completed yet nobody gets a percentage (all 0), none is invented.
fun contributionPercentages(
    members: List<HouseholdMember>,
    completions: List<ChoreCompletion>
): Map<String, Double> {
    val counts = members.associate { it.uid to contributionCount(it, completions) }
    val total = counts.values.sum()
    return counts.mapValues { (_, count) -> if (total == 0) 0.0 else count * 100.0 / total }
}

// Workload = unfinished occurrences due within the next 7 days (a rolling window from today) plus
// unfinished overdue ones. Every occurrence counts as one, no weighting. Personal chores never count.
// A chore is counted under whoever is responsible for it now, so a transferred chore moves with it.
fun workloadOf(uid: String, openChores: List<Chore>, today: Long): Int =
    openChores
        .filter { !it.done && it.scope == ChoreScope.HOUSEHOLD && it.assigneeUid == uid }
        .sumOf { occurrencesInWindow(it, today) }

// The current occurrence counts once whether it is overdue or upcoming. If the chore always stays with the
// same person, its later scheduled occurrences inside the window count as well. Rotating chores pass to
// other people, so only the current occurrence is theirs. An overdue chore adds no future occurrences,
// because the next one isn't created until this one is done.
private fun occurrencesInWindow(chore: Chore, today: Long): Int {
    val windowEnd = today + WORKLOAD_WINDOW_DAYS
    if (chore.dueEpochDay > windowEnd) return 0
    var count = 1
    if (chore.repeatDays > 0 && !chore.rotate && chore.dueEpochDay >= today) {
        var next = chore.dueEpochDay + chore.repeatDays
        while (next <= windowEnd && (chore.endEpochDay == 0L || next <= chore.endEpochDay)) {
            count++
            next += chore.repeatDays
        }
    }
    return count
}

// Picks who gets a new household chore. Returns null when nobody is eligible.
// 1. Only one eligible member: that person.
// 2. Nothing completed yet: random among the eligible.
// 3. Otherwise the lowest contribution percentage wins, and workload never overrides a unique lowest.
// 4. Several members share the lowest: the lightest workload among them.
// 5. Still tied: random among those.
// Contribution and workload are recalculated on every call, from the data passed in.
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

    val counts = eligible.associate { it.uid to contributionCount(it, completions) }
    if (counts.values.all { it == 0 }) return eligible.random(random)

    val shares = contributionPercentages(eligible, completions)
    val lowest = shares.values.min()
    val tied = eligible.filter { shares.getValue(it.uid) == lowest }
    if (tied.size == 1) return tied.first()

    val load = tied.associate { it.uid to workloadOf(it.uid, openChores, today) }
    val lightest = load.values.min()
    return tied.filter { load.getValue(it.uid) == lightest }.random(random)
}