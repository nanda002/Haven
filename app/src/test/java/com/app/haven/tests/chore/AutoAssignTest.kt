package com.app.haven.tests.chore

import com.app.haven.domain.chore.Chore
import com.app.haven.domain.chore.ChoreCompletion
import com.app.haven.domain.chore.HouseholdMember
import com.app.haven.domain.chore.VacationPeriod
import com.app.haven.domain.chore.automaticPriorityOrder
import com.app.haven.domain.chore.pickAutomaticAssignee
import com.app.haven.domain.chore.contributionPercentages
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoAssignTest {
    private val today = 20_000L

    private fun member(uid: String, vacation: Boolean = false, active: Boolean = true) =
        HouseholdMember(uid, uid.uppercase(), joinedEpochDay = 0L, active = active, onVacation = vacation)

    private fun completions(by: String, count: Int) = (1..count).map { i ->
        ChoreCompletion("$by-$i", "chore-$by-$i", "Chore", by, by, by, today - 5, today - 5)
    }

    private fun open(assignee: String, count: Int) = (1..count).map { i ->
        Chore(id = "open-$assignee-$i", title = "Chore", assigneeUid = assignee, assigneeName = assignee, dueEpochDay = today + 1)
    }

    @Test fun nobodyEligibleGivesNull() {
        val members = listOf(member("a", vacation = true), member("b", active = false))
        assertNull(pickAutomaticAssignee(members, emptyList(), emptyList(), today))
    }

    @Test fun singleEligibleMemberGetsIt() {
        val members = listOf(member("a"), member("b", vacation = true))
        assertEquals("a", pickAutomaticAssignee(members, emptyList(), emptyList(), today)?.uid)
    }

    @Test fun uniquelyLowestContributionWinsEvenWithMoreWork() {
        val members = listOf(member("a"), member("b"))
        val history = completions("a", 1) + completions("b", 3)
        val picked = pickAutomaticAssignee(members, history, open("a", 4) + open("b", 1), today)
        assertEquals("a", picked?.uid)
    }

    @Test fun tiedContributionGoesToLighterWorkload() {
        val members = listOf(member("a"), member("b"))
        val history = completions("a", 2) + completions("b", 2)
        val picked = pickAutomaticAssignee(members, history, open("a", 4) + open("b", 2), today)
        assertEquals("b", picked?.uid)
    }

    @Test fun noHistoryPicksSomeEligibleMember() {
        val members = listOf(member("a"), member("b"), member("c", vacation = true))
        val picked = pickAutomaticAssignee(members, emptyList(), emptyList(), today)
        assertTrue(picked?.uid == "a" || picked?.uid == "b")
    }

    // ---- the complete algorithm ----

    private fun joined(uid: String, daysAgo: Long, periods: List<VacationPeriod> = emptyList()) =
        HouseholdMember(uid, uid.uppercase(), joinedEpochDay = today - daysAgo, vacationPeriods = periods)

    private fun doneOn(by: String, count: Int, day: Long = today) = (1..count).map { i ->
        ChoreCompletion("$by-$day-$i", "chore-$by-$day-$i", "Chore", by, by, by, day, day)
    }

    // who comes out first over many different random draws
    private fun firstPicks(
        members: List<HouseholdMember>,
        history: List<ChoreCompletion>,
        open: List<Chore>
    ): Set<String> = (1..60).map {
        pickAutomaticAssignee(members, history, open, today, Random(it))!!.uid
    }.toSet()

    // 1. until everyone has completed one chore, it is random among those who have not
    @Test fun peopleWithNoCompletionsComeFirstAndAreChosenRandomly() {
        val members = listOf(member("a"), member("b"), member("c"))
        val history = completions("a", 5)
        assertEquals(setOf("b", "c"), firstPicks(members, history, emptyList()))
    }

    @Test fun theRandomStartIgnoresContributionAndWorkload() {
        val members = listOf(member("a"), member("b"), member("c"))
        val history = completions("a", 1)
        // b has far more work waiting than c, yet both can still be picked
        assertEquals(setOf("b", "c"), firstPicks(members, history, open("b", 5)))
    }

    @Test fun everyoneWithNothingYetIsOrderedBeforeEveryoneWithSomething() {
        val members = listOf(member("a"), member("b"), member("c"), member("d"))
        val history = completions("a", 1) + completions("b", 4)
        (1..30).forEach {
            val order = automaticPriorityOrder(members, history, emptyList(), today, Random(it)).map { m -> m.uid }
            assertEquals(setOf("c", "d"), order.take(2).toSet())
            assertEquals(listOf("a", "b"), order.drop(2))
        }
    }

    @Test fun aNewMemberGetsTheNextChoreAndThenJoinsTheNormalRanking() {
        val veteran1 = joined("v1", daysAgo = 400)
        val veteran2 = joined("v2", daysAgo = 400)
        val newcomer = joined("new", daysAgo = 0)
        val history = doneOn("v1", 30, today - 100) + doneOn("v2", 30, today - 100)
        assertEquals(setOf("new"), firstPicks(listOf(veteran1, veteran2, newcomer), history, emptyList()))
        // once the newcomer has completed one, the random start is over
        val after = history + doneOn("new", 1)
        val order = automaticPriorityOrder(listOf(veteran1, veteran2, newcomer), after, emptyList(), today)
        assertEquals("new", order.last().uid)      // they are the only ones with work in the shared window
    }

    // 2. then the lowest contribution percentage, whatever the workload
    @Test fun afterTheStartTheLowestContributionWinsEvenWithMoreWork() {
        val members = listOf(member("a"), member("b"), member("c"))
        val history = completions("a", 1) + completions("b", 2) + completions("c", 3)
        val order = automaticPriorityOrder(members, history, open("a", 6) + open("c", 0), today)
        assertEquals(listOf("a", "b", "c"), order.map { it.uid })
    }

    // 3. workload only decides when contribution is tied
    @Test fun tiedContributionIsDecidedByWorkload() {
        val members = listOf(member("a"), member("b"), member("c"))
        val history = completions("a", 2) + completions("b", 2) + completions("c", 1)
        val order = automaticPriorityOrder(members, history, open("a", 1) + open("b", 3), today)
        assertEquals(listOf("c", "a", "b"), order.map { it.uid })
    }

    @Test fun workloadNeverOverridesADifferentContribution() {
        val members = listOf(member("a"), member("b"))
        val history = completions("a", 2) + completions("b", 3)
        assertEquals(setOf("a"), firstPicks(members, history, open("a", 9)))
    }

    // 4. random only when both are tied
    @Test fun contributionAndWorkloadBothTiedIsRandom() {
        val members = listOf(member("a"), member("b"), member("c"))
        val history = completions("a", 2) + completions("b", 2) + completions("c", 2)
        assertEquals(setOf("a", "b", "c"), firstPicks(members, history, open("a", 1) + open("b", 1) + open("c", 1)))
    }

    @Test fun randomOnlyAmongThoseStillTied() {
        val members = listOf(member("a"), member("b"), member("c"))
        val history = completions("a", 2) + completions("b", 2) + completions("c", 2)
        // c has more work, so only a and b can come first
        assertEquals(setOf("a", "b"), firstPicks(members, history, open("c", 2)))
    }

    // eligibility
    @Test fun vacationersAndInactiveMembersAreNeverRanked() {
        val members = listOf(member("a"), member("b", vacation = true), member("c", active = false))
        assertEquals(listOf("a"), automaticPriorityOrder(members, emptyList(), emptyList(), today).map { it.uid })
    }

    // the participation window
    @Test fun aNewMemberIsNotSetAgainstWorkDoneBeforeTheyJoined() {
        val veteran = joined("vet", daysAgo = 400)
        val newcomer = joined("new", daysAgo = 10)
        // the veteran did 50 chores long before the newcomer arrived, then one each since
        val history = doneOn("vet", 50, today - 200) + doneOn("vet", 1, today - 3) + doneOn("new", 1, today - 3)
        val shares = contributionPercentages(listOf(veteran, newcomer), history)
        assertEquals(50.0, shares.getValue("vet"), 1e-9)
        assertEquals(50.0, shares.getValue("new"), 1e-9)
        // tied, so workload decides: the veteran has less waiting
        assertEquals(setOf("vet"), firstPicks(listOf(veteran, newcomer), history, open("new", 2)))
    }

    @Test fun daysAnyoneWasOnVacationAreLeftOutForEveryone() {
        val ann = joined("ann", daysAgo = 400)
        val ben = joined("ben", daysAgo = 400, periods = listOf(VacationPeriod(today - 50, today - 40)))
        // ann did 5 while ben was away, then one each since ben came back
        val history = doneOn("ann", 5, today - 45) + doneOn("ann", 1, today - 5) + doneOn("ben", 1, today - 5)
        val shares = contributionPercentages(listOf(ann, ben), history)
        assertEquals(50.0, shares.getValue("ann"), 1e-9)
        assertEquals(50.0, shares.getValue("ben"), 1e-9)
        assertEquals(setOf("ann"), firstPicks(listOf(ann, ben), history, open("ben", 2)))
    }

    @Test fun contributionCountsAreStillKeptInFull() {
        val ann = joined("ann", daysAgo = 400)
        val history = doneOn("ann", 5, today - 300)
        assertEquals(5, com.app.haven.domain.chore.contributionCount(ann, history))
    }
}
