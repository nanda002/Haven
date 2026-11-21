package com.app.haven.domain.expense

import java.util.Calendar
import java.util.TimeZone

//getting the user's monthly shares - total + per category
fun currMonthShareStats(
    expenses: List<Expense>,
    userUid: String,
    now: Long = System.currentTimeMillis(),
    timeZone: TimeZone = TimeZone.getDefault()
) : Map<ExpenseCategory, Long> {

    //get expenses for the current month
    val monthExpenses = expenses.filter{ expense->
        val created = expense.createdAtMillis
        created != null && isSameMonth(created, now, timeZone)
    }

    //for each expense, filter by the user uid, and get the share amount for that user per category
    val total = monthExpenses.map{ it.category to (it.shares[userUid] ?: 0L)}

    //connect to each category
    return ExpenseCategory.entries.associateWith { category ->
        total.filter { it.first == category }.sumOf { it.second }
    }
}

fun isSameMonth(millis: Long, now: Long, timeZone: TimeZone = TimeZone.getDefault()): Boolean {
    //expense timestamp
    val a = Calendar.getInstance(timeZone).apply { timeInMillis = millis }
    //current timestamp
    val b = Calendar.getInstance(timeZone).apply { timeInMillis = now }
    return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.MONTH) == b.get(Calendar.MONTH)
}



