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

    // ---- saved rotation order ----

    private val ann = Assignee("ann", "Ann")
    private val ben = Assignee("ben", "Ben")
    private val cat = Assignee("cat", "Cat")

    private fun repeating(assignee: Assignee, order: List<String>, rotate: Boolean = true) = Chore(
        id = "r1", title = "Trash", assigneeUid = assignee.uid, assigneeName = assignee.name,
        dueEpochDay = today, repeatDays = 7, rotate = rotate, rotationOrder = order
    )

    @Test fun nextTurnFollowsTheSavedOrderFromWhoActuallyDidIt() {
        val chore = repeating(cat, listOf("cat", "ann", "ben"))
        val members = listOf(ann, ben, cat)
        assertEquals("ben", completeChore(chore, members, ann, today).nextAssigneeUid)
        assertEquals("ann", completeChore(chore, members, cat, today).nextAssigneeUid)
        assertEquals("cat", completeChore(chore, members, ben, today).nextAssigneeUid)   // wraps around
    }

    @Test fun samePersonKeepsTheSameAssignee() {
        val chore = repeating(cat, listOf("cat", "ann", "ben"), rotate = false)
        val done = completeChore(chore, listOf(ann, ben, cat), ben, today)
        assertEquals("cat", done.nextAssigneeUid)
    }

    @Test fun roommatesWhoLeftAreSkipped() {
        val chore = repeating(ann, listOf("ann", "ben", "cat"))
        assertEquals("cat", completeChore(chore, listOf(ann, cat), ann, today).nextAssigneeUid)
    }

    @Test fun aChoreWithNoSavedOrderStillRotates() {
        val chore = repeating(ann, emptyList())
        val next = completeChore(chore, listOf(ann, ben, cat), ann, today)
        assertEquals("ben", next.nextAssigneeUid)
    }
}
