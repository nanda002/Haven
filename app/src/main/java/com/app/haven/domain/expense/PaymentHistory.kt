package com.app.haven.domain.expense

// Settlements the user paid or received, newest first.
// Pending ones (no server date yet) count as newest, so they show at the top.
fun paymentHistory(settlements: List<Settlement>, myUid: String): List<Settlement> =
    //need only settlements which consist of the current user
    settlements
        .filter { it.fromUid == myUid || it.toUid == myUid }
        .sortedByDescending { it.createdAtMillis ?: Long.MAX_VALUE }
