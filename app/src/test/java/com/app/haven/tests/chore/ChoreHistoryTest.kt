package com.app.haven.tests.chore

import com.app.haven.domain.chore.Chore
import com.app.haven.domain.chore.ChoreCompletion
import com.app.haven.domain.chore.originalAssignee
import com.app.haven.domain.chore.transferId
import org.junit.Assert.assertEquals
import org.junit.Test

class ChoreHistoryTest {
    private fun chore(due: Long, assignee: String = "ben", originalFor: Long = 0L, original: String = "") = Chore(
        id = "c1", title = "Dishes", assigneeUid = assignee, assigneeName = assignee.uppercase(),
        dueEpochDay = due, originalAssigneeUid = original, originalAssigneeName = original.uppercase(),
        originalForEpochDay = originalFor
    )

    @Test fun originalAssigneeSurvivesAHandOver() {
        val handedOver = chore(due = 100, assignee = "ben", originalFor = 100, original = "ann")
        assertEquals("ann", handedOver.originalAssignee().uid)
        assertEquals("ben", handedOver.assigneeUid)
    }

    @Test fun newOccurrenceStartsWithItsOwnAssignee() {
        val nextWeek = chore(due = 107, assignee = "cat", originalFor = 100, original = "ann")
        assertEquals("cat", nextWeek.originalAssignee().uid)
    }

    @Test fun neverHandedOverMeansCurrentAssigneeIsTheOriginal() {
        assertEquals("ben", chore(due = 100).originalAssignee().uid)
    }

    @Test fun requestIdIsStablePerOccurrenceAndReceiver() {
        assertEquals("c1_100_ben", transferId("c1", 100, "ben"))
    }

    @Test fun completionKeepsOriginalAndCurrentAssigneeApart() {
        val record = ChoreCompletion("c1_100", "c1", "Dishes", "cat", "Cat", "ben", 100, 101, originalAssigneeUid = "ann")
        assertEquals("cat", record.completedByUid)
        assertEquals("ben", record.assignedToUid)
        assertEquals("ann", record.originalAssigneeUid)
    }
}
