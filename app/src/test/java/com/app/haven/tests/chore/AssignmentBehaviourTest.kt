package com.app.haven.tests.chore

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.app.haven.domain.chore.Chore
import com.app.haven.domain.chore.ChoreCompletion
import com.app.haven.domain.chore.ChoreScope
import com.app.haven.domain.chore.ChoreTransferRequest
import com.app.haven.domain.chore.HouseholdMember
import com.app.haven.domain.chore.TransferStatus
import com.app.haven.domain.chore.automaticPriorityOrder
import com.app.haven.domain.chore.nextRecipientUid
import com.app.haven.domain.chore.offersExhausted
import com.app.haven.domain.chore.pendingForVacation
import com.app.haven.domain.chore.pickAutomaticAssignee
import com.app.haven.domain.chore.validateChore
import com.app.haven.domain.chore.vacationBlockedMessage

class AssignmentBehaviourTest {

    private val today = 1000L

    // ---- helpers: if one of these shows red, match it to your Chore.kt / ChoreCompletion.kt ----

    private fun member(uid: String, joined: Long, onVacation: Boolean = false, active: Boolean = true) =
        HouseholdMember(uid = uid, name = uid, joinedEpochDay = joined, active = active, onVacation = onVacation)

    private fun done(uid: String, day: Long) =
        ChoreCompletion(
            id = "c$day$uid",
            choreId = "c$day$uid",
            choreTitle = "chore",
            completedByUid = uid,
            completedByName = uid,
            assignedToUid = uid,
            dueEpochDay = day,
            completedEpochDay = day
        )
    private fun chore(
        id: String,
        assignee: String,
        isDone: Boolean = false,
        scope: String = ChoreScope.HOUSEHOLD
    ) = Chore(
        id = id, title = id, category = "other", notes = "",
        nextAssigneeUid = "", nextAssigneeName = "",
        assigneeUid = assignee, assigneeName = assignee,
        dueEpochDay = today, repeatDays = 0, rotate = false, endEpochDay = 0L,
        done = isDone, completedByUid = null, completedByName = null, lastCompletedEpochDay = 0L,
        scope = scope, ownerUid = "",
        originalAssigneeUid = assignee, originalAssigneeName = assignee, originalForEpochDay = today,
        rotationOrder = emptyList()
    )

    private fun offer(chore: Chore, from: String, to: String, status: String) =
        ChoreTransferRequest(
            id = "${chore.id}-$to", choreId = chore.id, choreTitle = chore.title,
            dueEpochDay = chore.dueEpochDay, fromUid = from, fromName = from, toUid = to, toName = to
        ).copy(status = status)

    // ---- AUTOMATIC ASSIGNMENT ----

    @Test
    fun newRoommateWithNoHistoryDoesNotJumpTheQueue() {
        val old1 = member("old1", joined = 0)
        val old2 = member("old2", joined = 0)
        val newbie = member("newbie", joined = today - 1)   // joined yesterday
        val completions = (1..5).map { done("old1", 100L + it) }   // old2 did nothing

        val order = automaticPriorityOrder(
            listOf(old1, old2, newbie), completions, emptyList(), today, Random(1)
        ).map { it.uid }

        assertEquals("old2", order.first())          // long-time roommate who did nothing is most behind
        assertNotEquals("newbie", order.first())     // joined yesterday: not treated as equally behind
        assertEquals("old1", order.last())           // did all the work, so last
    }

    @Test
    fun vacationingAndInactiveMembersAreNeverPicked() {
        val members = listOf(
            member("a", 0), member("away", 0, onVacation = true), member("gone", 0, active = false)
        )
        val order = automaticPriorityOrder(members, emptyList(), emptyList(), today, Random(1)).map { it.uid }
        assertEquals(listOf("a"), order)
    }

    @Test
    fun nobodyEligibleGivesNoAssignee() {
        val members = listOf(member("away", 0, onVacation = true))
        assertNull(pickAutomaticAssignee(members, emptyList(), emptyList(), today, Random(1)))
    }

    // ---- MANUAL ASSIGNMENT ----
    // A manual pick never goes through the algorithm; the only rule is that someone must be picked.

    @Test
    fun manualAssignmentNeedsAnAssignee() {
        assertEquals("Pick who it's for", validateChore("Dishes", null, "other"))
        assertEquals("Pick who it's for", validateChore("Dishes", "", "other"))
        assertNull(validateChore("Dishes", "b", "other"))
    }
    // ---- VACATION HAND-OVER ----

    @Test
    fun onlyMyOpenHouseholdChoresBlockVacation() {
        val mine = chore("mine", "a")
        val list = listOf(
            mine,
            chore("finished", "a", isDone = true),
            chore("personal", "a", scope = ChoreScope.PERSONAL),
            chore("theirs", "b")
        )
        assertEquals(listOf("mine"), pendingForVacation(list, "a").map { it.id })
    }

    @Test
    fun firstRoundIsSpreadRoundRobinAndSkipsTheVacationer() {
        val candidates = listOf("b", "c", "d")
        val x = chore("x", "a")
        assertEquals("b", nextRecipientUid(candidates, emptyList(), x, 0))
        assertEquals("c", nextRecipientUid(candidates, emptyList(), x, 1))
        assertEquals("d", nextRecipientUid(candidates, emptyList(), x, 2))
        assertEquals("b", nextRecipientUid(candidates, emptyList(), x, 3))
        // the vacationer is never offered their own chore, even if they appear in the list
        assertNotEquals("a", nextRecipientUid(listOf("a", "b"), emptyList(), x, 0))
    }

    @Test
    fun declineMovesToNextRoommateAndNeverAsksTheSamePersonTwice() {
        val candidates = listOf("b", "c", "d")
        val x = chore("x", "a")
        val sent = mutableListOf(offer(x, "a", "b", TransferStatus.DECLINED))

        val second = nextRecipientUid(candidates, sent, x, 0)
        assertNotNull(second)
        assertNotEquals("b", second)
        assertNotEquals("a", second)

        sent += offer(x, "a", second!!, TransferStatus.DECLINED)
        val third = nextRecipientUid(candidates, sent, x, 0)
        assertNotNull(third)
        assertFalse(third in listOf("a", "b", second))
    }

    @Test
    fun everyoneDecliningLeavesChoreWithVacationerAndBlocksVacation() {
        val candidates = listOf("b", "c")
        val x = chore("x", "a")
        val sent = listOf(
            offer(x, "a", "b", TransferStatus.DECLINED),
            offer(x, "a", "c", TransferStatus.DECLINED)
        )

        assertNull(nextRecipientUid(candidates, sent, x, 0))
        assertTrue(offersExhausted(candidates, sent, x))

        // declines never change the assignee, so the chore is still pending for the vacationer
        assertEquals(listOf("x"), pendingForVacation(listOf(x), "a").map { it.id })
        assertNotNull(vacationBlockedMessage(pendingForVacation(listOf(x), "a").size))
    }

    @Test
    fun waitingOfferIsNotExhaustedAndAcceptedChoreStopsBlocking() {
        val candidates = listOf("b", "c")
        val x = chore("x", "a")
        val waiting = listOf(offer(x, "a", "b", TransferStatus.PENDING))
        assertFalse(offersExhausted(candidates, waiting, x))

        // after b accepts, the chore is b's: nothing left for a, vacation is allowed
        val moved = chore("x", "b")
        assertTrue(pendingForVacation(listOf(moved), "a").isEmpty())
        assertNull(vacationBlockedMessage(0))
    }
}