package com.app.haven.data.model

import com.app.haven.domain.expense.ExpenseCategory
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date
import com.app.haven.domain.expense.Expense as DomainExpense
data class Expense(
    @DocumentId val id: String = "",
    //the name that the user gives the expense
    val description: String = "",
    //total amount - in cents, since splits from total amount is not calculated clearly with Double type
    val amountCents: Long = 0L,
    val category: String = "OTHER",
    //paid by which user
    val paidByUid: String = "",
    val paidByName: String = "",
    //which members are included in the transaction
    val shares: Map<String, Long> = emptyMap(),
    @ServerTimestamp val createdAt: Date? = null
)

//dto to transport model conversions and vice versa
fun Expense.toDomain() = DomainExpense(
    id = id,
    description = description,
    amountCents = amountCents,
    category = runCatching { ExpenseCategory.valueOf(category) }
        .getOrDefault(ExpenseCategory.OTHER),
    paidByUid = paidByUid,
    paidByName = paidByName,
    shares = shares,
    createdAtMillis = createdAt?.time
)

fun DomainExpense.toData() = Expense(
    description = description,
    amountCents = amountCents,
    category = category.name,
    paidByUid = paidByUid,
    paidByName = paidByName,
    shares = shares
)



