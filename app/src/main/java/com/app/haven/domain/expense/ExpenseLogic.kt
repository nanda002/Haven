package com.app.haven.domain.expense

import java.math.BigDecimal

//*the domain is for pure business logic, it does not touch upon the firebase, compose, view, view models, etc

//this is to split the expense evenly among the specified members of the household
fun splitExpenseLogic(total: Long, uids: List<String>): Map<String, Long> {
    require(total >= 0) {"No Negative Amounts allowed!"}
    val includedMembers = uids.distinct().sorted()

    if (includedMembers.isEmpty())
    {
        return emptyMap()
    }

    //first, we'll calculate the minimum that everyone has to pay, which is the base.
    val base = total / includedMembers.size

    //then we calculate the leftover with remains
    val remains = (total % includedMembers.size)

    //returning a map of user: their owed amount
    return includedMembers.mapIndexed {i, uid -> uid to base + if (i < remains) 1L else 0L}.toMap()
}

//converting the user entered amount (which is in dollars) to cents
fun parseDollarsToCents(input: String): Long? {
    val text = input.trim()
    if (!Regex("""\d+(\.\d{1,2})?""").matches(text)) return null
    return BigDecimal(text).movePointRight(2).toLong()
}

fun remainingCents(amountCents: Long, shares: Map<String, Long>): Long =
    amountCents - shares.values.sum()

fun validateShares(amountCents: Long, shares: Map<String, Long>): String? = when {
    amountCents <= 0 -> "Enter an amount greater than 0"
    shares.isEmpty() -> "Pick at least one person"
    shares.values.any { it < 0 } -> "Amounts can't be negative"
    remainingCents(amountCents, shares) != 0L -> "Amounts must add up to the total"
    else -> null
}

fun validateSettlement(amountCents: Long?, balanceCents: Long): String? = when {
    amountCents == null -> "Enter an amount greater than 0"
    amountCents <= 0 -> "Enter an amount greater than 0"
    amountCents > balanceCents -> "Can't be more than what's owed"
    else -> null
}


