package com.app.haven.tests.expense

import com.app.haven.domain.expense.Expense
import com.app.haven.domain.expense.ExpenseCategory
import com.app.haven.domain.expense.PersonBalance
import com.app.haven.domain.expense.Settlement
import com.app.haven.domain.expense.balancesWithEach
import com.app.haven.domain.expense.calculateBalances
import com.app.haven.domain.expense.withShare
import org.junit.Assert.assertEquals
import org.junit.Test

class BalanceTest {
    private fun expense(
        id: String,
        total: Long,
        category: ExpenseCategory,
        payer: String,
        shares: Map<String, Long>
    ) = Expense(id, "test", total, category, payer, payer, shares)

    // Jamie paid $90, split three ways
    private val electric = expense(
        "1", 9000, ExpenseCategory.UTILITIES, "j",
        mapOf("a" to 3000L, "j" to 3000L, "s" to 3000L)
    )

    // Alex (the user, "a") paid $60, split three ways
    private val groceries = expense(
        "2", 6000, ExpenseCategory.GROCERIES, "a",
        mapOf("a" to 2000L, "j" to 2000L, "s" to 2000L)
    )

    // withShare

    @Test fun whenIPaidOthersOweMe() {
        val r = withShare(groceries, "a")
        assertEquals(2000L, r.userShare)
        assertEquals(0L, r.userDues)
        assertEquals(4000L, r.userOwed)
    }

    @Test fun whenSomeoneElsePaidIOweMyShare() {
        val r = withShare(electric, "a")
        assertEquals(3000L, r.userShare)
        assertEquals(3000L, r.userDues)
        assertEquals(0L, r.userOwed)
    }

    @Test fun payerNotInSplitIsOwedTheFullAmount() {
        val gift = expense("3", 3000, ExpenseCategory.OTHER, "a", mapOf("j" to 1500L, "s" to 1500L))
        val r = withShare(gift, "a")
        assertEquals(0L, r.userShare)
        assertEquals(3000L, r.userOwed)
    }

    @Test fun notInvolvedMeansNothingOwedEitherWay() {
        val other = expense("4", 2000, ExpenseCategory.OTHER, "j", mapOf("j" to 1000L, "s" to 1000L))
        val r = withShare(other, "a")
        assertEquals(0L, r.userShare)
        assertEquals(0L, r.userDues)
        assertEquals(0L, r.userOwed)
    }

    @Test fun customUnevenSplit() {
        val rent = expense(
            "5", 240000, ExpenseCategory.RENT, "a",
            mapOf("a" to 100000L, "j" to 80000L, "s" to 60000L)
        )
        assertEquals(140000L, withShare(rent, "a").userOwed)
        assertEquals(80000L, withShare(rent, "j").userDues)
        assertEquals(60000L, withShare(rent, "s").userDues)
    }

    // balancesWithEach

    @Test fun noExpensesMeansNoBalances() {
        assertEquals(emptyList<PersonBalance>(), balancesWithEach(emptyList(), "a"))
    }

    @Test fun netsAcrossExpensesPerPerson() {
        val r = balancesWithEach(listOf(electric, groceries), "a")
        assertEquals(listOf(PersonBalance("j", -1000L), PersonBalance("s", 2000L)), r)
    }

    @Test fun eachPhoneSeesTheSameDebtFromItsOwnSide() {
        val mine = balancesWithEach(listOf(electric), "a")
        val jamies = balancesWithEach(listOf(electric), "j")
        assertEquals(listOf(PersonBalance("j", -3000L)), mine)
        assertEquals(
            listOf(PersonBalance("a", 3000L), PersonBalance("s", 3000L)),
            jamies.sortedBy { it.uid }
        )
    }

    @Test fun balancesThatCancelOutDisappear() {
        val back = expense("6", 6000, ExpenseCategory.OTHER, "a", mapOf("a" to 3000L, "j" to 3000L))
        val there = expense("7", 6000, ExpenseCategory.OTHER, "j", mapOf("a" to 3000L, "j" to 3000L))
        assertEquals(emptyList<PersonBalance>(), balancesWithEach(listOf(back, there), "a"))
    }

    @Test fun peopleIOweComeFirst() {
        val r = balancesWithEach(listOf(electric, groceries), "a")
        assertEquals("j", r.first().uid)
    }

    // settlements

    @Test fun payingInFullClearsTheDebt() {
        val r = balancesWithEach(listOf(electric), "a", listOf(Settlement("a", "j", 3000L)))
        assertEquals(emptyList<PersonBalance>(), r)
    }

    @Test fun partialPaymentLeavesTheRest() {
        val r = balancesWithEach(listOf(electric), "a", listOf(Settlement("a", "j", 1000L)))
        assertEquals(listOf(PersonBalance("j", -2000L)), r)
    }

    @Test fun beingPaidBackClearsWhatTheyOweMe() {
        val r = balancesWithEach(listOf(groceries), "a", listOf(Settlement("j", "a", 2000L)))
        assertEquals(listOf(PersonBalance("s", 2000L)), r)
    }

    @Test fun settlementBetweenOthersIsIgnored() {
        val r = balancesWithEach(listOf(electric), "a", listOf(Settlement("j", "s", 500L)))
        assertEquals(listOf(PersonBalance("j", -3000L)), r)
    }

    // calculateBalances

    @Test fun summaryMatchesPerPersonList() {
        val b = calculateBalances(listOf(electric, groceries), "a")
        assertEquals(1000L, b.iOweCents)
        assertEquals(2000L, b.owedToMeCents)
        assertEquals(1000L, b.netCents)
    }

    @Test fun summaryChangesAfterSettlement() {
        val b = calculateBalances(listOf(electric, groceries), "a", listOf(Settlement("a", "j", 1000L)))
        assertEquals(0L, b.iOweCents)
        assertEquals(2000L, b.owedToMeCents)
    }

    @Test fun summaryIsZeroWhenNothingOwed() {
        val b = calculateBalances(emptyList(), "a")
        assertEquals(0L, b.iOweCents)
        assertEquals(0L, b.owedToMeCents)
        assertEquals(0L, b.netCents)
    }
}