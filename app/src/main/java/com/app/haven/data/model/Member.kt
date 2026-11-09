package com.app.haven.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class Member(
    @DocumentId val uid: String = "",
    val displayName: String = "",
    val photoUrl: String? = null,
    val active: Boolean = true,
    @ServerTimestamp val joinedAt: Date? = null
)