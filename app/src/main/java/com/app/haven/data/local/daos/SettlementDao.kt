package com.app.haven.data.local.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.app.haven.data.local.entities.SettlementEntity
import kotlinx.coroutines.flow.Flow

//database operations on the local "settlements" table
@Dao
interface SettlementDao {

    @Query("SELECT * FROM settlements WHERE householdId = :householdId ORDER BY createdAtMillis DESC")
    fun observe(householdId: String): Flow<List<SettlementEntity>>

    @Transaction
    suspend fun replaceForHousehold(householdId: String, settlements: List<SettlementEntity>) {
        deleteForHousehold(householdId)
        insertAll(settlements)
    }

    @Query("DELETE FROM settlements WHERE householdId = :householdId")
    suspend fun deleteForHousehold(householdId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(settlements: List<SettlementEntity>)

    @Query("DELETE FROM settlements")
    suspend fun clearAll()
}