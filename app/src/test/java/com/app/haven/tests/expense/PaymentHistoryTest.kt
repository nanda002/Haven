package com.app.haven.tests.expense

import com.app.haven.domain.expense.Settlement
import com.app.haven.domain.expense.paymentHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentHistoryTest {
    private val me = "me"

    @Test fun excludesPaymentsBetweenOtherRoommates() {
        val list = listOf(Settlement("alex", "sam", 500), Settlement("alex", me, 300))
        assertEquals(listOf(Settlement("alex", me, 300)), paymentHistory(list, me))
    }

    @Test fun newestFirstAndPendingOnTop() {
        val old = Settlement(me, "alex", 100, createdAtMillis = 1_000)
        val new = Settlement("sam", me, 200, createdAtMillis = 2_000)
        val pending = Settlement(me, "sam", 300, createdAtMillis = null)
        assertEquals(listOf(pending, new, old), paymentHistory(listOf(old, pending, new), me))
    }

    @Test fun keepsSettledInFullFlag() {
        val s = Settlement(me, "alex", 100, settledInFull = true)
        assertTrue(paymentHistory(listOf(s), me).single().settledInFull)
    }

    @Test fun emptyWhenNoPaymentsInvolveMe() {
        assertTrue(paymentHistory(listOf(Settlement("alex", "sam", 500)), me).isEmpty())
    }
}
