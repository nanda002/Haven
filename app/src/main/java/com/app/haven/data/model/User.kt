package com.app.haven.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class User(
    @DocumentId val uid: String = "",
    val username: String = "",
    val email: String = "",
    val householdId: String? = null,
    @ServerTimestamp val createdAt: Date? = null
)