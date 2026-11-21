package com.app.haven.tests.chore

import com.app.haven.domain.chore.Chore
import com.app.haven.domain.chore.ChoreCompletion
import com.app.haven.domain.chore.HouseholdMember
import com.app.haven.domain.chore.VacationPeriod
import com.app.haven.domain.chore.activeDays
import com.app.haven.domain.chore.automaticPriorityOrder
import com.app.haven.domain.chore.completionRatePerWeek
import com.app.haven.domain.chore.pickAutomaticAssignee
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

    // ---- normalized completion rate and the full priority order ----

    private fun joined(uid: String, daysAgo: Long, periods: List<VacationPeriod> = emptyList()) =
        HouseholdMember(uid, uid.uppercase(), joinedEpochDay = today - daysAgo, vacationPeriods = periods)

    private fun doneOn(by: String, count: Int, day: Long = today) = (1..count).map { i ->
        ChoreCompletion("$by-$day-$i", "chore-$by-$day-$i", "Chore", by, by, by, day, day)
    }

    @Test fun rateIsPerActiveWeekNotTheRawCount() {
        val veteran = joined("vet", daysAgo = 999)     // 1000 active days
        val newcomer = joined("new", daysAgo = 13)     // 14 active days
        val history = doneOn("vet", 10, today - 5) + doneOn("new", 3, today - 5)
        // vet: about 0.07 a week, new: 1.5 a week. The raw counts (10 and 3) would have said the opposite.
        val order = automaticPriorityOrder(listOf(veteran, newcomer), history, emptyList(), today)
        assertEquals(listOf("vet", "new"), order.map { it.uid })
    }

    @Test fun vacationDaysAreNotActiveDays() {
        val ben = joined("ben", daysAgo = 27, periods = listOf(VacationPeriod(today - 20, today - 7)))
        assertEquals(14L, activeDays(ben, today))      // 28 days since joining, 14 of them on vacation
    }

    @Test fun aVeryRecentJoinerIsRatedOverAtLeastSevenDays() {
        val fresh = joined("fresh", daysAgo = 0)
        assertEquals(1L, activeDays(fresh, today))
        assertEquals(1.0, completionRatePerWeek(fresh, doneOn("fresh", 1), today), 1e-9)   // 1 chore over 7 days, not over 1
    }

    @Test fun equalRateAndShareFallsToLighterWorkload() {
        val a = joined("a", daysAgo = 2)
        val b = joined("b", daysAgo = 4)
        val history = doneOn("a", 1) + doneOn("b", 1)
        val order = automaticPriorityOrder(listOf(a, b), history, open("a", 3) + open("b", 1), today)
        assertEquals(listOf("b", "a"), order.map { it.uid })
    }

    @Test fun priorityOrderListsEveryEligibleRoommateBestFirst() {
        val members = listOf(
            member("a"), member("b"), member("c"),
            member("d", vacation = true), member("e", active = false)
        )
        val history = completions("a", 3) + completions("b", 1) + completions("c", 2)
        val order = automaticPriorityOrder(members, history, emptyList(), today)
        assertEquals(listOf("b", "c", "a"), order.map { it.uid })
    }
}
