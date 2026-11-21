package com.app.haven.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.app.haven.domain.chore.ChoreDraft

// a half-filled add-chore form, saved on the phone only (one per household)
@Entity(tableName = "chore_draft")
data class ChoreDraftEntity(
    @PrimaryKey val householdId: String,
    val title: String,
    val categoryKey: String?,
    val notes: String,
    val assigneeUid: String?,
    val dueOffset: Int?,
    val fixedDueDay: Long,
    val repeatDays: Int,
    val rotate: Boolean,
    val endDay: Long
)

fun ChoreDraftEntity.toDomain() = ChoreDraft(
    title = title, categoryKey = categoryKey, notes = notes, assigneeUid = assigneeUid,
    dueOffset = dueOffset, fixedDueDay = fixedDueDay, repeatDays = repeatDays,
    rotate = rotate, endDay = endDay
)

fun ChoreDraft.toEntity(householdId: String) = ChoreDraftEntity(
    householdId = householdId, title = title, categoryKey = categoryKey, notes = notes,
    assigneeUid = assigneeUid, dueOffset = dueOffset, fixedDueDay = fixedDueDay,
    repeatDays = repeatDays, rotate = rotate, endDay = endDay
)