package com.app.haven.tests.chore

import com.app.haven.domain.chore.Assignee
import com.app.haven.domain.chore.Chore
import com.app.haven.domain.chore.canComplete
import com.app.haven.domain.chore.completeChore
import com.app.haven.domain.chore.completionBlockedMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChoreCompletionRulesTest {
    private val today = 100L
    private fun chore(due: Long) = Chore(
        id = "c1", title = "Dishes", assigneeUid = "ann", assigneeName = "Ann",
        dueEpochDay = due, category = "kitchen", notes = "Use the blue sponge"
    )

    @Test fun assigneeCanCompleteBeforeAndOnDueDate() {
        assertTrue(canComplete(chore(today + 3), "ann", today))
        assertTrue(canComplete(chore(today), "ann", today))
    }

    @Test fun otherRoommateBlockedBeforeAndOnDueDate() {
        assertFalse(canComplete(chore(today + 3), "ben", today))
        assertFalse(canComplete(chore(today), "ben", today))
        assertEquals("Only Ann can complete this until it's overdue", completionBlockedMessage(chore(today), "ben", today))
    }

    @Test fun anyoneCanCompleteOnceOverdue() {
        assertTrue(canComplete(chore(today - 1), "ben", today))
        assertNull(completionBlockedMessage(chore(today - 1), "ben", today))
    }

    @Test fun completionRecordsActualPersonAndKeepsOtherFields() {
        val ben = Assignee("ben", "Ben")
        val done = completeChore(chore(today - 1), listOf(Assignee("ann", "Ann"), ben), ben, today)
        assertEquals("ben", done.completedByUid)
        assertEquals("Ben", done.completedByName)
        assertEquals(today, done.lastCompletedEpochDay)
        assertEquals("ann", done.assigneeUid)
        assertEquals("kitchen", done.category)
        assertEquals("Use the blue sponge", done.notes)
    }
}
