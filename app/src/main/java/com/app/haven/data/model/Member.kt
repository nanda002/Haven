package com.app.haven.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class Member(
    @DocumentId val uid: String = "",
    val displayName: String = "",
    //would be good when user leaves the household
    val active: Boolean = true,
    @ServerTimestamp val joinedAt: Date? = null
)