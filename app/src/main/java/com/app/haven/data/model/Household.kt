package com.app.haven.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class Household(
    @DocumentId val id: String = "",
    val name: String = "",
    //unique invite code for each household
    val inviteCode: String = "",
    val createdBy: String = "",
    val memberIds: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Date? = null
)