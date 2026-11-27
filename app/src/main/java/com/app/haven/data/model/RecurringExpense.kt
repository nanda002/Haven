package com.app.haven.data.model

import com.app.haven.domain.expense.DomainRecurringExpense
import com.app.haven.domain.expense.ExpenseCategory
import com.app.haven.domain.expense.ExpenseRepeat
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class RecurringExpense (
    @DocumentId val id: String = "",
    val description: String = "",
    val amountCents: Long = 0L,
    val category: String = "OTHER",
    val paidByUid: String = "",
    val paidByName: String = "",
    val shares: Map<String, Long> = emptyMap(),
    val startEpochDay: Long = 0L,
    val repeat: String = "once",
    val endEpochDay: Long = 0L,
    val occurrence: Int = 0,
    val dueEpochDay: Long = 0L,
    val active: Boolean = true,
    @ServerTimestamp val createdAt: Date? = null
)

fun RecurringExpense.toDomain() = DomainRecurringExpense(
    id = id,
    description = description,
    amountCents = amountCents,
    category = runCatching { ExpenseCategory.valueOf(category) }.getOrDefault(ExpenseCategory.OTHER),
    paidByUid = paidByUid,
    paidByName = paidByName,
    shares = shares,
    startEpochDay = startEpochDay,
    repeat = ExpenseRepeat.fromKey(repeat),
    endEpochDay = endEpochDay,
    occurrence = occurrence,
    dueEpochDay = dueEpochDay,
    active = active
)

fun DomainRecurringExpense.toData() = RecurringExpense(
    description = description,
    amountCents = amountCents,
    category = category.name,
    paidByUid = paidByUid,
    paidByName = paidByName,
    shares = shares,
    startEpochDay = startEpochDay,
    repeat = repeat.key,
    endEpochDay = endEpochDay,
    occurrence = occurrence,
    dueEpochDay = dueEpochDay,
    active = active
)
