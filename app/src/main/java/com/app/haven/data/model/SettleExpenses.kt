package com.app.haven.data.model

import com.app.haven.domain.expense.Expense
import com.app.haven.domain.expense.ExpenseCategory
import com.app.haven.domain.expense.Settlement
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date
import kotlin.String

data class SettleExpenses (
    @DocumentId val id: String = "",
    val fromUid: String = "",
    val toUid: String = "",
    val amount: Long = 0L,
    val whoSettled: String = "",
    @ServerTimestamp val createdAt: Date? = null
)

//dto to transport model conversions and vice versa
fun SettleExpenses.toDomain() = Settlement(
    id = id,
    fromUid = fromUid,
    toUid = toUid,
    amountCents = amount,
    createdByUid = whoSettled,
    createdAtMillis = createdAt?.time
)

fun Settlement.toData() = SettleExpenses(
    id = id,
    fromUid = fromUid,
    toUid = toUid,
    amount = amountCents,
    whoSettled = createdByUid
)
