package com.app.haven.tests.expense

import com.app.haven.domain.expense.parseDollarsToCents
import com.app.haven.domain.expense.remainingCents
import com.app.haven.domain.expense.splitExpenseLogic
import com.app.haven.domain.expense.validateShares
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExpenseLogicTest {

    // splitExpenseLogic

    @Test fun evenSplit() {
        val r = splitExpenseLogic(900, listOf("a", "b", "c"))
        assertEquals(mapOf("a" to 300L, "b" to 300L, "c" to 300L), r)
    }

    @Test fun remainderGoesToFirstSortedUid() {
        val r = splitExpenseLogic(10000, listOf("c", "a", "b"))
        assertEquals(3334L, r["a"])
        assertEquals(3333L, r["b"])
        assertEquals(3333L, r["c"])
    }

    @Test fun twoCentsLeftoverGoToFirstTwo() {
        val r = splitExpenseLogic(1001, listOf("a", "b", "c"))
        assertEquals(334L, r["a"])
        assertEquals(334L, r["b"])
        assertEquals(333L, r["c"])
    }

    @Test fun sharesAlwaysAddUpToTotal() {
        for (total in listOf(1L, 99L, 100L, 1001L, 10000L, 123457L)) {
            for (n in 1..7) {
                val uids = (1..n).map { "u$it" }
                assertEquals(total, splitExpenseLogic(total, uids).values.sum())
            }
        }
    }

    @Test fun orderOfInputDoesNotChangeResult() {
        assertEquals(
            splitExpenseLogic(10000, listOf("a", "b", "c")),
            splitExpenseLogic(10000, listOf("c", "b", "a"))
        )
    }

    @Test fun duplicateUidsAreChargedOnce() {
        assertEquals(mapOf("a" to 500L, "b" to 500L), splitExpenseLogic(1000, listOf("a", "b", "a")))
    }

    @Test fun onePersonPaysEverything() {
        assertEquals(mapOf("a" to 500L), splitExpenseLogic(500, listOf("a")))
    }

    @Test fun emptyListGivesEmptyMap() {
        assertEquals(emptyMap<String, Long>(), splitExpenseLogic(500, emptyList()))
    }

    @Test fun zeroAmountGivesZeroShares() {
        assertEquals(mapOf("a" to 0L, "b" to 0L), splitExpenseLogic(0, listOf("a", "b")))
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeAmountThrows() {
        splitExpenseLogic(-1, listOf("a"))
    }

    // parseDollarsToCents

    @Test fun parsesGoodAmounts() {
        assertEquals(1250L, parseDollarsToCents("12.50"))
        assertEquals(1210L, parseDollarsToCents("12.1"))
        assertEquals(1200L, parseDollarsToCents("12"))
        assertEquals(5L, parseDollarsToCents("0.05"))
        assertEquals(750L, parseDollarsToCents("  7.5  "))
    }

    @Test fun rejectsBadAmounts() {
        listOf("", " ", "abc", "-5", "1.234", ".5", "1,000", "12.").forEach {
            assertNull("expected null for \"$it\"", parseDollarsToCents(it))
        }
    }

    // remainingCents

    @Test fun remainingShowsWhatIsLeft() {
        assertEquals(250L, remainingCents(1000, mapOf("a" to 750L)))
        assertEquals(0L, remainingCents(1000, mapOf("a" to 600L, "b" to 400L)))
        assertEquals(-100L, remainingCents(1000, mapOf("a" to 1100L)))
    }

    // validateShares

    @Test fun validSharesReturnNull() {
        assertNull(validateShares(1000, mapOf("a" to 700L, "b" to 300L)))
    }

    @Test fun rejectsSharesThatDoNotAddUp() {
        assertEquals(
            "Amounts must add up to the total",
            validateShares(1000, mapOf("a" to 700L, "b" to 200L))
        )
    }

    @Test fun rejectsEmptyShares() {
        assertEquals("Pick at least one person", validateShares(1000, emptyMap()))
    }

    @Test fun rejectsZeroOrNegativeTotal() {
        assertEquals("Enter an amount greater than 0", validateShares(0, mapOf("a" to 0L)))
        assertEquals("Enter an amount greater than 0", validateShares(-5, mapOf("a" to -5L)))
    }

    @Test fun rejectsNegativeShare() {
        assertEquals(
            "Amounts can't be negative",
            validateShares(1000, mapOf("a" to 1500L, "b" to -500L))
        )
    }
}