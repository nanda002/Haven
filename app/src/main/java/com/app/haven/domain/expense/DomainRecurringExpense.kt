package com.app.haven.domain.expense

import java.util.Date

data class DomainRecurringExpense(
    val id: String = "",
    val description: String = "",
    val amountCents: Long = 0L,
    val category: ExpenseCategory,
    val paidByUid: String = "",
    val paidByName: String = "",
    val shares: Map<String, Long> = emptyMap(),
    val startEpochDay: Long = 0L,
    val repeat: ExpenseRepeat,
    val endEpochDay: Long = 0L,
    val occurrence: Int = 0,
    val dueEpochDay: Long = startEpochDay,
    val active: Boolean = true,
    val createdAt: Date? = null
)