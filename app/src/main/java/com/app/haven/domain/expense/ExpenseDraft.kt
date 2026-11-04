package com.app.haven.domain.expense

//a half-filled add-expense form, as the app works with it
//text fields are kept exactly as typed, since they may not be valid numbers yet
data class ExpenseDraft(
    val householdId: String,
    val description: String,
    val amountText: String,
    val category: ExpenseCategory,
    val paidByUid: String,
    val evenSplit: Boolean,
    val includedUids: Set<String>,
    val customAmountTexts: Map<String, String>
)