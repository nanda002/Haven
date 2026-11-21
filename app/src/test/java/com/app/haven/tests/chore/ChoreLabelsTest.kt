package com.app.haven.tests.chore

import com.app.haven.domain.chore.Chore
import com.app.haven.domain.chore.dueLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChoreLabelsTest {
    private val today = 20_000L
    private fun chore(due: Long, done: Boolean = false, completed: Long = 0L) = Chore(
        id = "c1", title = "Dishes", assigneeUid = "ann", assigneeName = "Ann",
        dueEpochDay = due, done = done, lastCompletedEpochDay = completed
    )

    @Test fun dueToday() = assertEquals("Due today", chore(today).dueLabel(today))

    @Test fun dueTomorrow() = assertEquals("Due tomorrow", chore(today + 1).dueLabel(today))

    @Test fun dueThisWeekUsesWeekday() {
        val label = chore(today + 3).dueLabel(today)
        assertTrue(label.startsWith("Due "))
        assertTrue(!label.contains(" in "))
    }

    @Test fun overdueByOneDay() = assertEquals("Overdue by 1 day", chore(today - 1).dueLabel(today))

    @Test fun doneToday() = assertEquals("Done today", chore(today, done = true, completed = today).dueLabel(today))
}
