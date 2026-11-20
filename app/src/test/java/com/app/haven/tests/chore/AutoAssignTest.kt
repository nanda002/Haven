package com.app.haven.tests.chore

import com.app.haven.domain.chore.Chore
import com.app.haven.domain.chore.ChoreCompletion
import com.app.haven.domain.chore.HouseholdMember
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
}
