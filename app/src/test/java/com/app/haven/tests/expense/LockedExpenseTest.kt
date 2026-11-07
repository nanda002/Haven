package com.app.haven.tests.expense

import com.app.haven.domain.expense.Expense
import com.app.haven.domain.expense.ExpenseCategory
import com.app.haven.domain.expense.involvesFormerMember
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockedExpenseTest {

    private fun expense(paidBy: String, vararg sharedWith: String) = Expense(
        description = "Groceries",
        amountCents = 900,
        category = ExpenseCategory.GROCERIES,
        paidByUid = paidBy,
        paidByName = paidBy,
        shares = sharedWith.associateWith { 900L / sharedWith.size }
    )

    @Test fun everyoneStillActive_notLocked() {
        assertFalse(involvesFormerMember(expense("a", "a", "b", "c"), setOf("a", "b", "c")))
    }

    @Test fun payerLeft_locked() {
        assertTrue(involvesFormerMember(expense("c", "a", "b", "c"), setOf("a", "b")))
    }

    @Test fun someoneInTheSplitLeft_locked() {
        assertTrue(involvesFormerMember(expense("a", "a", "b", "c"), setOf("a", "b")))
    }

    @Test fun formerMemberNotInThisExpense_notLocked() {
        // c left, but this expense was only between a and b
        assertFalse(involvesFormerMember(expense("a", "a", "b"), setOf("a", "b")))
    }

    @Test fun memberListUnknown_notLocked() {
        assertFalse(involvesFormerMember(expense("a", "a", "b"), null))
    }

    @Test fun memberListEmpty_notLocked() {
        // empty = not loaded yet (e.g. offline); a household always has at least you in it
        assertFalse(involvesFormerMember(expense("a", "a", "b"), emptySet()))
    }
}
