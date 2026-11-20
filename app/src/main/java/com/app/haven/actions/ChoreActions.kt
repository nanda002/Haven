package com.app.haven.actions

import android.util.Log
import com.app.haven.data.model.Chore as ChoreDoc
import com.app.haven.data.model.toData
import com.app.haven.data.model.toDomain
import com.app.haven.domain.chore.Chore
import com.app.haven.domain.chore.ChoreCompletion
import com.app.haven.domain.chore.ChoreScope
import com.app.haven.domain.chore.resolveOccurrence
import com.app.haven.domain.chore.todayEpochDay
import com.app.haven.domain.chore.validateChore
import com.app.haven.domain.chore.validateEndDate
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import com.app.haven.data.model.ChoreCompletionDoc
import com.app.haven.data.model.Member
import com.app.haven.domain.chore.HouseholdMember
import com.app.haven.domain.chore.VacationPeriod
import com.app.haven.domain.chore.epochDayOfMillis
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull


// What the assignment logic needs, read at the moment a chore is saved
data class AssignmentInputs(
    val members: List<HouseholdMember>,
    val completions: List<ChoreCompletion>,
    val openChores: List<Chore>
)



interface ChoreActions {
    fun getChores(householdId: String, myUid: String): Flow<List<Chore>>
    suspend fun addChore(householdId: String, chore: Chore): Result<Unit>
    suspend fun updateChore(householdId: String, chore: Chore): Result<Unit>
    suspend fun deleteChore(householdId: String, chore: Chore): Result<Unit>
    suspend fun recordCompletion(householdId: String, completion: ChoreCompletion): Result<Unit>
    suspend fun removeCompletion(householdId: String, completionId: String): Result<Unit>
    suspend fun loadAssignmentInputs(householdId: String): Result<AssignmentInputs>
}

// Shared chores live in households/{id}/chores. Personal chores live in their own collection,
// households/{id}/personalChores, so they can never mix into household lists, workload or contribution.
class ChoreRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) : ChoreActions {

    private fun household(householdId: String) = db.collection("households").document(householdId)
    private fun householdChores(householdId: String) = household(householdId).collection("chores")
    private fun personalChores(householdId: String) = household(householdId).collection("personalChores")
    private fun completions(householdId: String) = household(householdId).collection("choreCompletions")

    private fun collectionFor(householdId: String, scope: String): CollectionReference =
        if (scope == ChoreScope.PERSONAL) personalChores(householdId) else householdChores(householdId)

    override fun getChores(householdId: String, myUid: String): Flow<List<Chore>> =
        combine(
            listen(householdChores(householdId).orderBy("dueEpochDay")),
            // only this user's own personal chores are ever requested, other people's never reach this phone
            listen(personalChores(householdId).whereEqualTo("ownerUid", myUid)).catch { emit(emptyList()) }
        ) { shared, personal -> shared + personal }

    private fun listen(query: Query): Flow<List<Chore>> = callbackFlow {
        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val today = todayEpochDay()
            val list = snapshot?.toObjects(ChoreDoc::class.java)
                ?.map { resolveOccurrence(it.toDomain(), today) } ?: emptyList()
            trySend(list)
        }
        awaitClose { registration.remove() }
    }

    override suspend fun addChore(householdId: String, chore: Chore): Result<Unit> =
        runCatching {
            validateChore(chore.title, chore.assigneeUid, chore.category)?.let { throw IllegalArgumentException(it) }
            validateEndDate(chore.dueEpochDay, chore.repeatDays, chore.endEpochDay)?.let { throw IllegalArgumentException(it) }
            // not awaited: the write lands in this phone's copy at once and syncs when there's a connection, same as expenses
            collectionFor(householdId, chore.scope).add(chore.toData())
                .addOnFailureListener { Log.e("Chore Repo", "add failed on the server") }
            Unit
        }

    override suspend fun updateChore(householdId: String, chore: Chore): Result<Unit> =
        runCatching {
            require(chore.id.isNotBlank()) { "Missing chore id" }
            collectionFor(householdId, chore.scope).document(chore.id).update(
                mapOf(
                    "title" to chore.title.trim(),
                    "category" to chore.category,
                    "notes" to chore.notes,
                    "assigneeUid" to chore.assigneeUid,
                    "assigneeName" to chore.assigneeName,
                    "dueEpochDay" to chore.dueEpochDay,
                    "repeatDays" to chore.repeatDays,
                    "rotate" to chore.rotate,
                    "endEpochDay" to chore.endEpochDay,
                    "done" to chore.done,
                    "completedByUid" to chore.completedByUid,
                    "completedByName" to chore.completedByName,
                    "lastCompletedEpochDay" to chore.lastCompletedEpochDay,
                    "nextAssigneeUid" to chore.nextAssigneeUid,
                    "nextAssigneeName" to chore.nextAssigneeName
                )
            ).addOnFailureListener { Log.e("Chore Repo", "update failed on the server") }
            Unit
        }

    // the completion history is kept when a chore is deleted, so contribution numbers don't change
    override suspend fun deleteChore(householdId: String, chore: Chore): Result<Unit> =
        runCatching {
            require(chore.id.isNotBlank()) { "Missing chore id" }
            collectionFor(householdId, chore.scope).document(chore.id).delete()
                .addOnFailureListener { Log.e("Chore Repo", "delete failed on the server") }
            Unit
        }

    override suspend fun recordCompletion(householdId: String, completion: ChoreCompletion): Result<Unit> =
        runCatching {
            completions(householdId).document(completion.id).set(completion.toData())
                .addOnFailureListener { Log.e("Chore Repo", "completion record failed on the server") }
            Unit
        }

    override suspend fun removeCompletion(householdId: String, completionId: String): Result<Unit> =
        runCatching {
            completions(householdId).document(completionId).delete()
                .addOnFailureListener { Log.e("Chore Repo", "completion removal failed on the server") }
            Unit
        }

    // Reads membership, history and open chores again from the server (this phone's copy when offline or slow)
    override suspend fun loadAssignmentInputs(householdId: String): Result<AssignmentInputs> =
        runCatching {
            val today = todayEpochDay()
            val members = household(householdId).collection("members")
                .whereEqualTo("active", true).latest()
                .toObjects(Member::class.java)
                .map { m ->
                    // finished vacations plus the one still going, so participation can be worked out for any day
                    val periods = m.vacationPeriods.map { VacationPeriod(it.startEpochDay, it.endEpochDay) } +
                            if (m.vacation) listOf(VacationPeriod(m.vacationStartEpochDay)) else emptyList()
                    HouseholdMember(
                        uid = m.uid,
                        name = m.displayName,
                        joinedEpochDay = m.joinedAt?.let { epochDayOfMillis(it.time) } ?: today,
                        active = m.active,
                        onVacation = m.vacation,
                        vacationPeriods = periods
                    )
                }
            val history = completions(householdId).latest()
                .toObjects(ChoreCompletionDoc::class.java).map { it.toDomain() }
            val open = householdChores(householdId).latest()
                .toObjects(ChoreDoc::class.java)
                .map { resolveOccurrence(it.toDomain(), today) }
                .filter { !it.done }
            AssignmentInputs(members, history, open)
        }

    // The newest data from the server, or this phone's copy if offline or the server is slow
    private suspend fun Query.latest(): QuerySnapshot {
        val fromServer = try {
            withTimeoutOrNull(4_000) { get(Source.SERVER).await() }
        } catch (e: FirebaseFirestoreException) {
            null
        }
        return fromServer ?: get(Source.CACHE).await()
    }
}