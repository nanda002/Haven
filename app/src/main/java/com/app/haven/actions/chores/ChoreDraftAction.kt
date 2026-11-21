package com.app.haven.actions.chores

import com.app.haven.data.local.daos.ChoreDraftDao
import com.app.haven.data.local.entities.toDomain
import com.app.haven.data.local.entities.toEntity
import com.app.haven.domain.chore.ChoreDraft

// Handles saving and retrieving unfinished chore forms
// Drafts are temporary and stored locally in Room, not in Firestore
// This means a draft is not yet a real household or personal chore
interface ChoreDraftActions {

    // Retrieves the saved draft for a particular household
    // Returns null if the user has not saved a draft yet
    suspend fun loadDraft(householdId: String): ChoreDraft?

    // Saves the current form information locally so the user can continue later
    suspend fun saveDraft(householdId: String, draft: ChoreDraft)

    // Deletes the saved draft, usually after the user finishes creating the chore
    // or chooses to discard the unfinished form
    suspend fun deleteDraft(householdId: String)
}

// Implements draft operations using the Room database
// The DAO handles the actual database queries, this repository
// converts between Room entities and domain models
class ChoreDraftRepository(
    private val dao: ChoreDraftDao
) : ChoreDraftActions {

    // Gets the saved draft from Room using the household ID
    // toDomain() converts the database entity into a ChoreDraft
    // that the rest of the app can use
    override suspend fun loadDraft(householdId: String): ChoreDraft? =
        dao.get(householdId)?.toDomain()

    // Converts the ChoreDraft into a Room database entity using toEntity(),
    // then asks the DAO to save it locally
    override suspend fun saveDraft(householdId: String, draft: ChoreDraft) =
        dao.save(draft.toEntity(householdId))

    // Removes the saved draft for this household from the local Room database
    override suspend fun deleteDraft(householdId: String) =
        dao.delete(householdId)
}