package com.app.haven.actions

import android.util.Log
import com.app.haven.data.model.Chore as ChoreDoc
import com.app.haven.data.model.toData
import com.app.haven.data.model.toDomain
import com.app.haven.domain.chore.Chore
import com.app.haven.domain.chore.validateChore
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

interface ChoreActions {
    fun getChores(householdId: String): Flow<List<Chore>>
    suspend fun addChore(householdId: String, chore: Chore): Result<Unit>
    suspend fun updateChore(householdId: String, chore: Chore): Result<Unit>
    suspend fun deleteChore(householdId: String, choreId: String): Result<Unit>
}

class ChoreRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) : ChoreActions {
    private fun chores(householdId: String) =
        db.collection("households").document(householdId).collection("chores")

    override fun getChores(householdId: String): Flow<List<Chore>> = callbackFlow {
        val registration = chores(householdId)
            .orderBy("dueEpochDay", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.toObjects(ChoreDoc::class.java)
                    ?.map { it.toDomain() } ?: emptyList()
                trySend(list)
            }
        awaitClose { registration.remove() }
    }

    override suspend fun addChore(householdId: String, chore: Chore): Result<Unit> =
        runCatching {
            validateChore(chore.title, chore.assigneeUid)?.let { throw IllegalArgumentException(it) }
            chores(householdId).add(chore.toData())
                .addOnFailureListener { Log.e("Chore Repo", "add failed on the server") }
            Unit
        }

    override suspend fun updateChore(householdId: String, chore: Chore): Result<Unit> =
        runCatching {
            require(chore.id.isNotBlank()) { "Missing chore id" }
            chores(householdId).document(chore.id).update(
                mapOf(
                    "assigneeUid" to chore.assigneeUid,
                    "assigneeName" to chore.assigneeName,
                    "dueEpochDay" to chore.dueEpochDay,
                    "done" to chore.done,
                    "completedByUid" to chore.completedByUid
                )
            ).addOnFailureListener { Log.e("Chore Repo", "update failed on the server") }
            Unit
        }

    override suspend fun deleteChore(householdId: String, choreId: String): Result<Unit> =
        runCatching {
            require(choreId.isNotBlank()) { "Missing chore id" }
            chores(householdId).document(choreId).delete()
                .addOnFailureListener { Log.e("Chore Repo", "delete failed on the server") }
            Unit
        }
}