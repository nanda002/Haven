package com.app.haven.data.local.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.app.haven.data.local.entities.ChoreDraftEntity

// database operations on the local "chore_draft" table
// drafts are written here directly: they never go to Firestore
@Dao
interface ChoreDraftDao {

    // null when there's no draft for this household
    @Query("SELECT * FROM chore_draft WHERE householdId = :householdId")
    suspend fun get(householdId: String): ChoreDraftEntity?

    // insert or overwrite this household's draft
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(draft: ChoreDraftEntity)

    // after a successful save, or when the user taps Clear
    @Query("DELETE FROM chore_draft WHERE householdId = :householdId")
    suspend fun delete(householdId: String)

    // on logout
    @Query("DELETE FROM chore_draft")
    suspend fun clearAll()
}