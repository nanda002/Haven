package com.app.haven.tests.chore

import com.app.haven.domain.chore.Chore
import com.app.haven.domain.chore.ChoreScope
import com.app.haven.domain.chore.ChoreTransferRequest
import com.app.haven.domain.chore.TransferStatus
import com.app.haven.domain.chore.offerFor
import com.app.haven.domain.chore.pendingForVacation
import com.app.haven.domain.chore.vacationBlockedMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VacationRulesTest {
    private fun chore(
        id: String,
        assignee: String = "ann",
        due: Long = 100,
        done: Boolean = false,
        scope: String = ChoreScope.HOUSEHOLD
    ) = Chore(id = id, title = id, assigneeUid = assignee, assigneeName = assignee, dueEpochDay = due, done = done, scope = scope)

    private fun request(choreId: String, status: String, due: Long = 100, to: String = "ben") = ChoreTransferRequest(
        id = "$choreId-$to", choreId = choreId, choreTitle = choreId, dueEpochDay = due,
        fromUid = "ann", fromName = "Ann", toUid = to, toName = to, status = status
    )

    @Test fun onlyMyOpenSharedChoresBlockVacation() {
        val all = listOf(
            chore("mine", due = 105),
            chore("soonest", due = 101),
            chore("finished", done = true),
            chore("personal", scope = ChoreScope.PERSONAL),
            chore("theirs", assignee = "ben")
        )
        assertEquals(listOf("soonest", "mine"), pendingForVacation(all, "ann").map { it.id })
    }

    @Test fun messageIsEmptyWhenNothingIsLeft() {
        assertNull(vacationBlockedMessage(0))
        assertEquals("1 chore still needs to be finished or handed over", vacationBlockedMessage(1))
        assertEquals("3 chores still need to be finished or handed over", vacationBlockedMessage(3))
    }

    @Test fun aWaitingOfferIsShownBeforeADeclinedOne() {
        val offers = listOf(request("c1", TransferStatus.DECLINED, to = "ben"), request("c1", TransferStatus.PENDING, to = "cat"))
        assertEquals("cat", offerFor(offers, chore("c1"))?.toUid)
    }

    @Test fun aDeclinedOfferIsShownWhenNothingElseIsWaiting() {
        val offers = listOf(request("c1", TransferStatus.DECLINED))
        assertEquals(TransferStatus.DECLINED, offerFor(offers, chore("c1"))?.status)
    }

    @Test fun offersForOtherChoresOrOtherOccurrencesAreIgnored() {
        val offers = listOf(request("c2", TransferStatus.PENDING), request("c1", TransferStatus.PENDING, due = 90))
        assertNull(offerFor(offers, chore("c1", due = 100)))
    }
}
