package com.app.haven.data.local.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.app.haven.data.local.entities.ExpenseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {

    //live list for the screen; room re-emits whenever the table changes
    @Query("SELECT * FROM expenses WHERE householdId = :householdId ORDER BY createdAtMillis DESC")
    fun observe(householdId: String): Flow<List<ExpenseEntity>>

    //replace one household's rows with the latest Firestore snapshot, all or nothing
    @Transaction
    suspend fun replaceForHousehold(householdId: String, expenses: List<ExpenseEntity>) {
        deleteForHousehold(householdId)
        insertAll(expenses)
    }

    @Query("DELETE FROM expenses WHERE householdId = :householdId")
    suspend fun deleteForHousehold(householdId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(expenses: List<ExpenseEntity>)

    //on logout, so the next user doesn't see this household's data
    @Query("DELETE FROM expenses")
    suspend fun clearAll()
}