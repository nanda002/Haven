package com.app.haven.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.app.haven.domain.expense.Settlement

@Entity(
    tableName = "settlements",
    indices = [Index("householdId")]
)
data class SettlementEntity (
    @PrimaryKey val id: String,   //same id as the Firestore document
    val householdId: String,
    val fromUid: String,          //who paid
    val toUid: String,            //who received it
    val amountCents: Long,
    val createdByUid: String,     //who pressed Settle
    val createdAtMillis: Long?
)

//room entity to domain model conversions and vice versa
fun SettlementEntity.toDomain() = Settlement(
    id = id,
    fromUid = fromUid,
    toUid = toUid,
    amountCents = amountCents,
    createdByUid = createdByUid,
    createdAtMillis = createdAtMillis
)

fun Settlement.toEntity(householdId: String) = SettlementEntity(
    id = id,
    householdId = householdId,
    fromUid = fromUid,
    toUid = toUid,
    amountCents = amountCents,
    createdByUid = createdByUid,
    createdAtMillis = createdAtMillis
)