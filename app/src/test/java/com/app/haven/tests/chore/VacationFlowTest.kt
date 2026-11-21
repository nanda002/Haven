package com.app.haven.tests.chore

import com.app.haven.domain.chore.Chore
import com.app.haven.domain.chore.ChoreCompletion
import com.app.haven.domain.chore.ChoreScope
import com.app.haven.domain.chore.HouseholdMember
import com.app.haven.domain.chore.VacationPeriod
import com.app.haven.domain.chore.automaticPriorityOrder
import com.app.haven.domain.chore.contributionCount
import com.app.haven.domain.chore.pendingForVacation
import com.app.haven.domain.chore.vacationBlockedMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VacationFlowTest {
    private val today = 20_000L

    private fun chore(assignee: String, done: Boolean = false, scope: String = ChoreScope.HOUSEHOLD) =
        Chore(id = "$assignee-$done-$scope", title = "Chore", assigneeUid = assignee, assigneeName = assignee,
            dueEpochDay = today, done = done, scope = scope)

    private fun doneOn(day: Long) = ChoreCompletion("a-$day", "chore-$day", "Chore", "a", "A", "a", day, day)

    // ---- starting: blocked until nothing is left ----

    @Test fun startIsBlockedWhileHouseholdChoresRemainAndAllowedWhenNoneDo() {
        val left = pendingForVacation(listOf(chore("a"), chore("a", scope = ChoreScope.PERSONAL), chore("a", done = true)), "a")
        assertEquals(1, left.size)
        assertTrue(vacationBlockedMessage(left.size) != null)
        assertNull(vacationBlockedMessage(pendingForVacation(listOf(chore("a", done = true), chore("b")), "a").size))
    }

    // A vacation switched on today is stored as starting tomorrow, so the chores finished today to clear the list
    // still count as contribution, and the days away start the next day.
    @Test fun workDoneOnTheDayVacationIsSwitchedOnStillCounts() {
        val a = HouseholdMember("a", "A", joinedEpochDay = 0L, onVacation = true,
            vacationPeriods = listOf(VacationPeriod(today + 1)))
        assertTrue(a.participatedOn(today))
        assertFalse(a.participatedOn(today + 1))
        assertEquals(1, contributionCount(a, listOf(doneOn(today))))
    }

    // ---- while away ----

    @Test fun aVacationerIsNotGivenNewAutomaticChores() {
        val away = HouseholdMember("a", "A", joinedEpochDay = 0L, onVacation = true)
        val here = HouseholdMember("b", "B", joinedEpochDay = 0L)
        assertEquals(listOf("b"), automaticPriorityOrder(listOf(away, here), emptyList(), emptyList(), today).map { it.uid })
    }

    // ---- returning ----

    // away from today + 1 to today + 10 (the stored end is the last day away), back on today + 11
    private val back = HouseholdMember("a", "A", joinedEpochDay = 0L, onVacation = false,
        vacationPeriods = listOf(VacationPeriod(today + 1, today + 10)))

    @Test fun contributionResumesOnTheReturnDayAndNotBefore() {
        val history = listOf(doneOn(today), doneOn(today + 5), doneOn(today + 11), doneOn(today + 12))
        assertFalse(back.participatedOn(today + 5))
        assertTrue(back.participatedOn(today + 11))
        assertEquals(3, contributionCount(back, history))   // today, and the two after returning; the one while away is not theirs
    }

    @Test fun backFromVacationIsRankedForAssignmentsAgain() {
        val other = HouseholdMember("b", "B", joinedEpochDay = 0L)
        val order = automaticPriorityOrder(listOf(back, other), emptyList(), emptyList(), today + 11).map { it.uid }
        assertEquals(setOf("a", "b"), order.toSet())
    }

    // a vacation that was switched off before it began has no days away at all
    @Test fun aVacationThatNeverBeganCostsNothing() {
        val never = HouseholdMember("a", "A", joinedEpochDay = 0L)   // endVacation records no period for it
        assertEquals(1, contributionCount(never, listOf(doneOn(today))))
        assertTrue(never.participatedOn(today + 1))
    }
}
