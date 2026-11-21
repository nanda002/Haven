package com.app.haven.tests.chore

import com.app.haven.domain.chore.Chore
import com.app.haven.domain.chore.ChoreCompletion
import com.app.haven.domain.chore.ChoreScope
import com.app.haven.domain.chore.ChoreTransferRequest
import com.app.haven.domain.chore.HouseholdMember
import com.app.haven.domain.chore.TransferStatus
import com.app.haven.domain.chore.VacationPeriod
import com.app.haven.domain.chore.automaticPriorityOrder
import com.app.haven.domain.chore.contributionCount
import com.app.haven.domain.chore.offerFor
import com.app.haven.domain.chore.pendingForVacation
import com.app.haven.domain.chore.vacationBlockedMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VacationRedistributionTest {
    private val today = 20_000L

    private fun chore(assignee: String, done: Boolean = false, scope: String = ChoreScope.HOUSEHOLD, due: Long = 100L) =
        Chore(id = "$assignee-$done-$scope-$due", title = "Chore", assigneeUid = assignee, assigneeName = assignee,
            dueEpochDay = due, done = done, scope = scope)

    @Test fun onlyMyUnfinishedHouseholdChoresAreListed() {
        val mine = chore("ann")
        val all = listOf(mine, chore("ann", done = true), chore("ann", scope = ChoreScope.PERSONAL), chore("ben"))
        assertEquals(listOf(mine), pendingForVacation(all, "ann"))
    }

    @Test fun startIsBlockedUntilNothingIsLeft() {
        assertEquals("2 chores still need to be finished or handed over", vacationBlockedMessage(2))
        assertNull(vacationBlockedMessage(0))
    }

    private fun offer(to: String, status: String, due: Long = 100L) = ChoreTransferRequest(
        id = "x_${due}_$to", choreId = "ann-false-household-100", choreTitle = "Chore", dueEpochDay = due,
        fromUid = "ann", fromName = "ann", toUid = to, toName = to, status = status
    )

    @Test fun anOpenOfferIsShownBeforeADeclinedOne() {
        val c = chore("ann")
        assertEquals("cat", offerFor(listOf(offer("ben", TransferStatus.DECLINED), offer("cat", TransferStatus.PENDING)), c)?.toUid)
        assertEquals("ben", offerFor(listOf(offer("ben", TransferStatus.DECLINED)), c)?.toUid)
        assertNull(offerFor(listOf(offer("ben", TransferStatus.ACCEPTED, due = 93L)), c))
    }

    // ---- coming back from vacation ----

    // away from today - 10 to yesterday, back today (the stored end is the last day away)
    private val returned = HouseholdMember(
        "a", "A", joinedEpochDay = 0L, onVacation = false,
        vacationPeriods = listOf(VacationPeriod(today - 10, today - 1))
    )

    private fun doneOn(day: Long) = ChoreCompletion("a-$day", "chore-$day", "Chore", "a", "A", "a", day, day)

    @Test fun contributionCountsFromTheReturnDateAndNotTheDaysAway() {
        assertFalse(returned.participatedOn(today - 5))
        assertTrue(returned.participatedOn(today))
        assertEquals(2, contributionCount(returned, listOf(doneOn(today - 15), doneOn(today - 5), doneOn(today))))
    }

    @Test fun backFromVacationIsEligibleForAssignmentsAgain() {
        val other = HouseholdMember("b", "B", joinedEpochDay = 0L)
        val away = returned.copy(onVacation = true)
        assertEquals(listOf("b"), automaticPriorityOrder(listOf(away, other), emptyList(), emptyList(), today).map { it.uid })
        assertEquals(
            setOf("a", "b"),
            automaticPriorityOrder(listOf(returned, other), emptyList(), emptyList(), today).map { it.uid }.toSet()
        )
    }
}
