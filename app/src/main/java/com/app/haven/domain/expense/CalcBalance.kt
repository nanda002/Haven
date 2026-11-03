package com.app.haven.domain.expense

//* The 'user' here in variable names refers to the user currently logged in
data class ShareDetails(
    val expense: Expense,
    val userShare: Long,
    val userDues: Long,
    val userOwed: Long
)

fun withShare(expense: Expense, myUid: String): ShareDetails {
    val userPaid = (expense.paidByUid == myUid)
    return ShareDetails(
        expense = expense,
        //get the user's share of the expense
        userShare = expense.shares[myUid] ?: 0L,
        //get how much the user owes another member
        userDues = if (userPaid) 0L else (expense.shares[myUid] ?: 0L),
        //get how much the user is owed if the user paid
        userOwed = if (userPaid) expense.amountCents - (expense.shares[myUid] ?: 0L) else 0L
    )
}

//A recorded payment between two people
data class Settlement(val fromUid: String, val toUid: String, val amountCents: Long)

// Positive netCents: they owe me. Negative: I owe them.
data class PersonBalance(val uid: String, val netCents: Long)

fun balancesWithEach(
    expenses: List<Expense>,
    myUid: String,
    settlements: List<Settlement> = emptyList()
): List<PersonBalance> {
    val net = mutableMapOf<String, Long>()
    //accumulating what each person owes across multiple expenses
    fun add(uid: String, cents: Long) { net[uid] = (net[uid] ?: 0L) + cents }

    for (e in expenses) {
        if (e.paidByUid == myUid) {
            for ((uid, share) in e.shares) if (uid != myUid) add(uid, share)
        } else {
            val mine = e.shares[myUid] ?: 0L
            //because the member who is owed is defecit of that amount, hence add negative share
            if (mine > 0) add(e.paidByUid, -mine)
        }
    }
    for (s in settlements) {
        if (s.fromUid == myUid) add(s.toUid, s.amountCents)
        else if (s.toUid == myUid) add(s.fromUid, -s.amountCents)
    }
    return net.filterValues { it != 0L }
        .map { PersonBalance(it.key, it.value) }
        .sortedBy { it.netCents }
}

// Summary card totals, built from the per-person list so the card and the list always agree
data class Balance(val iOweCents: Long, val owedToMeCents: Long) {
    val netCents: Long get() = owedToMeCents - iOweCents
}

fun calculateBalances(
    expenses: List<Expense>,
    myUid: String,
    settlements: List<Settlement> = emptyList()
): Balance {
    val people = balancesWithEach(expenses, myUid, settlements)
    return Balance(
        iOweCents = people.filter { it.netCents < 0 }.sumOf { -it.netCents },
        owedToMeCents = people.filter { it.netCents > 0 }.sumOf { it.netCents }
    )
}


