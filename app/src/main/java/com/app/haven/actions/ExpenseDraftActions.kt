package com.app.haven.actions

import com.app.haven.data.local.daos.ExpenseDraftDao
import com.app.haven.data.local.entities.toDomain
import com.app.haven.data.local.entities.toEntity
import com.app.haven.domain.expense.ExpenseDraft

//unsaved add-expense forms; unlike expenses, these live only in Room and never go to Firestore
interface ExpenseDraftActions {
    suspend fun loadDraft(householdId: String): ExpenseDraft?
    suspend fun saveDraft(householdId: String, draft: ExpenseDraft)
    suspend fun deleteDraft(householdId: String)
}

class ExpenseDraftRepository(
    private val dao: ExpenseDraftDao
) : ExpenseDraftActions {

    override suspend fun loadDraft(householdId: String): ExpenseDraft? =
        dao.get(householdId)?.toDomain()

    override suspend fun saveDraft(householdId: String, draft: ExpenseDraft) =
        dao.save(draft.toEntity(householdId))

    override suspend fun deleteDraft(householdId: String) =
        dao.delete(householdId)
}
