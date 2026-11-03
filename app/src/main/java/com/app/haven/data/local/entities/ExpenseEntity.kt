package com.app.haven.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.app.haven.domain.expense.Expense
import com.app.haven.domain.expense.ExpenseCategory

@Entity(
    tableName = "expenses",
    indices = [Index("householdId")]
)
data class ExpenseEntity (
    @PrimaryKey val id: String,          //same id as the Firestore document
    val householdId: String,             //which household it belongs to
    val description: String,
    val amountCents: Long,
    val category: String,                //enum stored as its name, e.g. "RENT"
    val paidByUid: String,
    val paidByName: String,
    val shares: Map<String, Long>,       //stored as text by the shares converter
    val createdAtMillis: Long?
)

//room entity to domain model conversions and vice versa
fun ExpenseEntity.toDomain() = Expense(
    id = id,
    description = description,
    amountCents = amountCents,
    category = runCatching { ExpenseCategory.valueOf(category) }
        .getOrDefault(ExpenseCategory.OTHER),
    paidByUid = paidByUid,
    paidByName = paidByName,
    shares = shares,
    createdAtMillis = createdAtMillis
)

fun Expense.toEntity(householdId: String) = ExpenseEntity(
    id = id,
    householdId = householdId,
    description = description,
    amountCents = amountCents,
    category = category.name,
    paidByUid = paidByUid,
    paidByName = paidByName,
    shares = shares,
    createdAtMillis = createdAtMillis
)
