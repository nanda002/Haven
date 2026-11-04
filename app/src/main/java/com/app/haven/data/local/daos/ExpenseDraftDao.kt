package com.app.haven.data.local.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.app.haven.data.local.entities.ExpenseDraftEntity

//database operations on the local "expense_drafts" table
//**unlike expenses, drafts are written here directly: they never go to Firestore
@Dao
interface ExpenseDraftDao {

    //null when there's no draft for this household
    @Query("SELECT * FROM expense_draft WHERE householdId = :householdId")
    suspend fun get(householdId: String): ExpenseDraftEntity?

    //insert or overwrite this household's draft
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(draft: ExpenseDraftEntity)

    //after a successful save, or when the user taps Clear
    @Query("DELETE FROM expense_draft WHERE householdId = :householdId")
    suspend fun delete(householdId: String)

    //on logout
    @Query("DELETE FROM expense_draft")
    suspend fun clearAll()
}

