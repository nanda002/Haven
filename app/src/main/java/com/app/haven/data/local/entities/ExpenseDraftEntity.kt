package com.app.haven.data.local.entities

import com.app.haven.domain.expense.ExpenseCategory
import kotlin.String
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.app.haven.domain.expense.ExpenseDraft

//a half-filled add-expense form, as the app works with it
//text fields are kept exactly as typed, since they may not be valid numbers yet
@Entity(
    tableName = "expense_draft",
    indices = [Index("householdId")]
)
data class ExpenseDraftEntity(
    @PrimaryKey val householdId: String,
    val description: String,
    val amountText: String,
    val category: String,
    val paidByUid: String,
    val evenSplit: Boolean,
    val includedUids: Set<String>,
    val customAmountTexts: Map<String, String>
)

fun ExpenseDraftEntity.toDomain() = ExpenseDraft(
    householdId = householdId,
    description = description,
    amountText = amountText,
    category = runCatching { ExpenseCategory.valueOf(category) }.getOrDefault(ExpenseCategory.OTHER),
    paidByUid = paidByUid,
    evenSplit = evenSplit,
    includedUids = includedUids,
    customAmountTexts = customAmountTexts
)

fun ExpenseDraft.toEntity(householdId: String) = ExpenseDraftEntity(
    householdId = householdId,
    description = description,
    amountText = amountText,
    category = category.name,
    paidByUid = paidByUid,
    evenSplit = evenSplit,
    includedUids = includedUids,
    customAmountTexts = customAmountTexts
)