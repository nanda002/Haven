package com.app.haven.domain.household

import com.app.haven.domain.expense.Balance

// Can only leave when nobody owes you and you owe nobody
fun canLeaveHousehold(balance: Balance): Boolean =
    balance.iOweCents == 0L && balance.owedToMeCents == 0L