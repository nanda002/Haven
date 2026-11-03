package com.app.haven.domain.expense

enum class ExpenseCategory { RENT, UTILITIES, GROCERIES, OTHER }

data class Expense(
    val id: String = "",
    val description: String,
    val amountCents: Long,
    val category: ExpenseCategory,
    val paidByUid: String,
    val paidByName: String,
    val shares: Map<String, Long>,     // uid -> cents, must add up to amountCents
    val createdAtMillis: Long? = null
)