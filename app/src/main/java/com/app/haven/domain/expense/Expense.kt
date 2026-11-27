package com.app.haven.domain.expense

enum class ExpenseCategory { RENT, UTILITIES, GROCERIES, OTHER }

data class Expense(
    val id: String = "",
    val recurringId: String = "",
    val description: String,
    val amountCents: Long,
    val category: ExpenseCategory,
    val paidByUid: String,
    val paidByName: String,
    val shares: Map<String, Long>,
    val createdAtMillis: Long? = null,
    val dateEpochDay: Long = 0L
)