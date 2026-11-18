package com.app.haven.actions

import com.app.haven.data.local.daos.ChoreDraftDao
import com.app.haven.data.local.entities.toDomain
import com.app.haven.data.local.entities.toEntity
import com.app.haven.domain.chore.ChoreDraft

// unsaved add-chore forms; unlike chores, these live only in Room and never go to Firestore
interface ChoreDraftActions {
    suspend fun loadDraft(householdId: String): ChoreDraft?
    suspend fun saveDraft(householdId: String, draft: ChoreDraft)
    suspend fun deleteDraft(householdId: String)
}

class ChoreDraftRepository(
    private val dao: ChoreDraftDao
) : ChoreDraftActions {

    override suspend fun loadDraft(householdId: String): ChoreDraft? =
        dao.get(householdId)?.toDomain()

    override suspend fun saveDraft(householdId: String, draft: ChoreDraft) =
        dao.save(draft.toEntity(householdId))

    override suspend fun deleteDraft(householdId: String) =
        dao.delete(householdId)
}