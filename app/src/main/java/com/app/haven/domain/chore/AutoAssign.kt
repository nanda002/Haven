
package com.app.haven.domain.chore

import kotlin.random.Random

const val WORKLOAD_WINDOW_DAYS = 7L

const val NO_ELIGIBLE_MEMBER_MESSAGE =
    "No roommate is available to take this chore: everyone has left or is on vacation."

// A stretch of days a member was on vacation.
data class VacationPeriod(
    val startEpochDay: Long,
    val endEpochDay: Long = Long.MAX_VALUE
)

// A roommate as the assignment logic sees them.
data class HouseholdMember(
    val uid: String,
    val name: String,
    val joinedEpochDay: Long,
    val active: Boolean = true,
    val onVacation: Boolean = false,
    val vacationPeriods: List<VacationPeriod> = emptyList()
) {
    fun participatedOn(day: Long): Boolean =
        day >= joinedEpochDay &&
                vacationPeriods.none {
                    day >= it.startEpochDay && day <= it.endEpochDay
                }
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
// Percentages are calculated among the supplied active, eligible members.
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

// Calculate outstanding household workload.
// Each individual occurrence counts as one unit.
fun workloadOf(
    uid: String,
    openChores: List<Chore>,
    today: Long
): Int =
    openChores
        .filter {
            !it.done &&
                    it.scope == ChoreScope.HOUSEHOLD &&
                    it.assigneeUid == uid
        }
        .sumOf { occurrencesInWindow(it, today) }

// The seven-day window includes today and the following six days.
// Overdue chores count once. Future recurring occurrences are counted
// only for chores that stay with the same person.
private fun occurrencesInWindow(
    chore: Chore,
    today: Long
): Int {
    val windowEnd = today + WORKLOAD_WINDOW_DAYS - 1

    // Not overdue and outside the upcoming seven-day window.
    if (chore.dueEpochDay > windowEnd) return 0

    // Count the current unfinished occurrence once.
    var count = 1

    // An overdue occurrence stays outstanding. Do not count future
    // occurrences until the overdue one has been completed.
    if (chore.dueEpochDay < today) return count

    // Same-person recurring chores can have multiple occurrences
    // within the upcoming window.
    if (chore.repeatDays > 0 && !chore.rotate) {
        var next = chore.dueEpochDay + chore.repeatDays

        while (
            next <= windowEnd &&
            (chore.endEpochDay == 0L || next <= chore.endEpochDay)
        ) {
            count++
            next += chore.repeatDays
        }
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

    // Count actual completed household chores for each eligible member.
    val counts = eligible.associate {
        it.uid to contributionCount(it, completions)
    }

    // Initial assignment handling:
    // Give priority to eligible members who have never completed
    // a household chore. Randomly select among those members.
    val membersWithoutHistory = eligible.filter {
        counts.getValue(it.uid) == 0
    }

    if (membersWithoutHistory.isNotEmpty()) {
        return membersWithoutHistory.random(random)
    }

    // Normal assignment:
    // Lowest actual contribution percentage gets priority.
    val shares = contributionPercentages(eligible, completions)
    val lowest = shares.values.min()

    val tiedMembers = eligible.filter {
        shares.getValue(it.uid) == lowest
    }

    // A uniquely lowest contributor always gets the chore.
    if (tiedMembers.size == 1) {
        return tiedMembers.first()
    }

    // Workload is only used to resolve a contribution tie.
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
