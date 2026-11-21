package com.app.haven.tests.chore

import com.app.haven.domain.chore.Chore
import com.app.haven.domain.chore.ChoreCompletion
import com.app.haven.domain.chore.ChoreScope
import com.app.haven.domain.chore.HouseholdMember
import com.app.haven.domain.chore.MembershipPeriod
import com.app.haven.domain.chore.VacationPeriod
import com.app.haven.domain.chore.contributionCount
import com.app.haven.domain.chore.contributionPercentages
import com.app.haven.domain.chore.pickAutomaticAssignee
import com.app.haven.domain.chore.workloadOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContributionAndWorkloadTest {
    private val today = 20_000L

    private fun member(
        uid: String,
        joined: Long = 0L,
        vacation: Boolean = false,
        past: List<MembershipPeriod> = emptyList(),
        periods: List<VacationPeriod> = emptyList()
    ) = HouseholdMember(
        uid, uid.uppercase(), joinedEpochDay = joined, onVacation = vacation,
        vacationPeriods = periods, pastMemberships = past
    )

    private fun done(by: String, day: Long, count: Int = 1) = (1..count).map { i ->
        ChoreCompletion("$by-$day-$i", "chore-$by-$day-$i", "Chore", by, by, by, day, day)
    }

    private fun chore(
        assignee: String,
        due: Long,
        repeat: Int = 0,
        rotate: Boolean = true,
        done: Boolean = false,
        scope: String = ChoreScope.HOUSEHOLD,
        next: String = ""
    ) = Chore(
        id = "$assignee-$due", title = "Chore", assigneeUid = assignee, assigneeName = assignee,
        dueEpochDay = due, repeatDays = repeat, rotate = rotate, done = done, scope = scope,
        nextAssigneeUid = next, nextAssigneeName = next
    )

    // ---- contribution history ----

    @Test fun workDoneBeforeJoiningIsNotCounted() {
        val ben = member("ben", joined = 100)
        val history = done("ben", 50) + done("ben", 150, count = 2)
        assertEquals(2, contributionCount(ben, history))
    }

    @Test fun anEarlierStayStillCountsWhenSomeoneComesBack() {
        val ben = member("ben", joined = 100, past = listOf(MembershipPeriod(10, 40)))
        val history = done("ben", 20) + done("ben", 60) + done("ben", 120)
        assertEquals(2, contributionCount(ben, history))   // day 60 was in the gap between stays
    }

    @Test fun vacationDaysAreNotCounted() {
        val ben = member("ben", periods = listOf(VacationPeriod(30, 40)))
        val history = done("ben", 35) + done("ben", 41)
        assertEquals(1, contributionCount(ben, history))
    }

    @Test fun percentagesAddUpToOneHundred() {
        val members = listOf(member("a"), member("b"), member("c"))
        val history = done("a", 5) + done("b", 5, count = 2) + done("c", 5)
        val shares = contributionPercentages(members, history)
        assertEquals(100.0, shares.values.sum(), 0.0001)
        assertEquals(50.0, shares.getValue("b"), 0.0001)
    }

    // ---- choosing the assignee ----

    @Test fun nobodyHasCompletedAnythingPicksAnEligibleMember() {
        val members = listOf(member("a"), member("b"), member("c", vacation = true))
        val picked = pickAutomaticAssignee(members, emptyList(), emptyList(), today)
        assertTrue(picked?.uid == "a" || picked?.uid == "b")
    }

    @Test fun aLoneMemberWithNoCompletionsGetsTheChore() {
        val members = listOf(member("a"), member("b"))
        val picked = pickAutomaticAssignee(members, done("a", today - 5, count = 2), emptyList(), today)
        assertEquals("b", picked?.uid)
    }

    // those with no completions are chosen at random, whatever their workload (see AutoAssignTest)
    @Test fun severalMembersWithNoCompletionsAreAllCandidates() {
        val members = listOf(member("a"), member("b"), member("c"))
        val history = done("a", today - 5, count = 3)
        val open = List(3) { chore("b", today + 1) } + chore("c", today + 1)
        val picked = pickAutomaticAssignee(members, history, open, today)?.uid
        assertTrue(picked == "b" || picked == "c")
    }

    // ---- workload ----

    @Test fun anOverdueChoreCountsOnceNoMatterHowOld() {
        val open = listOf(chore("a", today - 30), chore("a", today + 3))
        assertEquals(2, workloadOf("a", open, today))
    }

    @Test fun theWindowIsSevenDaysStartingToday() {
        assertEquals(1, workloadOf("a", listOf(chore("a", today + 6)), today))
        assertEquals(0, workloadOf("a", listOf(chore("a", today + 7)), today))
    }

    @Test fun aSamePersonDailyChoreCountsEveryOccurrenceInTheWindow() {
        val daily = chore("a", today, repeat = 1, rotate = false)
        assertEquals(7, workloadOf("a", listOf(daily), today))
    }

    @Test fun aTakeTurnsChoreCountsOnlyTheCurrentOccurrence() {
        val daily = chore("a", today, repeat = 1, rotate = true)
        assertEquals(1, workloadOf("a", listOf(daily), today))
    }

    @Test fun aFinishedSamePersonChoreStillHasUpcomingOccurrences() {
        val finished = chore("a", today, repeat = 1, rotate = false, done = true)
        assertEquals(6, workloadOf("a", listOf(finished), today))
    }

    @Test fun aFinishedTakeTurnsChoreCountsTheNextTurnForTheNextPerson() {
        val finished = chore("a", today, repeat = 1, rotate = true, done = true, next = "b")
        assertEquals(1, workloadOf("b", listOf(finished), today))
        assertEquals(0, workloadOf("a", listOf(finished), today))
    }

    @Test fun personalAndFinishedOneOffChoresDoNotCount() {
        val personal = chore("a", today, scope = ChoreScope.PERSONAL)
        val finishedOneOff = chore("a", today, done = true)
        assertEquals(0, workloadOf("a", listOf(personal, finishedOneOff), today))
    }

    @Test fun aHandedOverChoreCountsForTheNewPerson() {
        val handedOver = chore("b", today + 1)   // assignee is now b
        assertEquals(0, workloadOf("a", listOf(handedOver), today))
        assertEquals(1, workloadOf("b", listOf(handedOver), today))
    }
}
