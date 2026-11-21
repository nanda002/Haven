package com.app.haven.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class Member(
    @DocumentId val uid: String = "",
    val displayName: String = "",
    val photoUrl: String? = null,
    val active: Boolean = true,
    val vacation: Boolean = false,                 // on vacation right now
    val vacationStartEpochDay: Long = 0L,          // when the current vacation started
    val vacationPeriods: List<VacationPeriodDoc> = emptyList(),   // finished vacations
    val membershipPeriods: List<MembershipPeriodDoc> = emptyList(),   // earlier stays, added each time they leave
    val leftAt: Date? = null,                                      // when they last left (active = false)
    @ServerTimestamp val joinedAt: Date? = null
)

// a finished vacation, stored as local epoch days

data class VacationPeriodDoc(
    val startEpochDay: Long = 0L,
    val endEpochDay: Long = 0L
)

// an earlier stay in the household, stored as local epoch days
data class MembershipPeriodDoc(
    val joinedEpochDay: Long = 0L,
    val leftEpochDay: Long = 0L
)
