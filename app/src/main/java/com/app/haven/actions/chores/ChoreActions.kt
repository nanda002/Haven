package com.app.haven.actions.chores

import android.util.Log
import com.app.haven.data.model.Chore as ChoreDoc
import com.app.haven.data.model.toData
import com.app.haven.data.model.toDomain
import com.app.haven.domain.chore.Chore
import com.app.haven.domain.chore.ChoreCompletion
import com.app.haven.domain.chore.ChoreTransferRequest
import com.app.haven.domain.chore.TransferStatus
import com.google.firebase.firestore.FieldValue
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
import com.app.haven.data.model.ChoreTransferDoc
import com.app.haven.data.model.Member
import com.app.haven.domain.chore.HouseholdMember
import com.app.haven.domain.chore.VacationPeriod
import com.app.haven.domain.chore.epochDayOfMillis
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull


// Groups together all the information needed by the automatic assignment algorithm.
// Instead of making separate database calls from the ViewModel, we load the members,
// their completion history, and currently open chores in one place.
// The ViewModel passes this data to AutoAssign.kt to decide who should get a chore.
data class AssignmentInputs(
    val members: List<HouseholdMember>,
    val completions: List<ChoreCompletion>,
    val openChores: List<Chore>
)

// Interface that defines the operations the chore repository must support.
// The ViewModel uses this interface instead of directly communicating with Firestore.
interface ChoreActions {
    fun getChores(householdId: String, myUid: String): Flow<List<Chore>>
    suspend fun addChore(householdId: String, chore: Chore): Result<Unit>
    suspend fun updateChore(householdId: String, chore: Chore): Result<Unit>
    suspend fun deleteChore(householdId: String, chore: Chore): Result<Unit>
    suspend fun recordCompletion(householdId: String, completion: ChoreCompletion): Result<Unit>
    suspend fun removeCompletion(householdId: String, completionId: String): Result<Unit>
    fun observeTransfers(householdId: String, myUid: String): Flow<List<ChoreTransferRequest>>
    suspend fun sendTransfer(householdId: String, request: ChoreTransferRequest): Result<Unit>
    suspend fun respondToTransfer(householdId: String, request: ChoreTransferRequest, accept: Boolean): Result<Unit>
    suspend fun loadAssignmentInputs(householdId: String): Result<AssignmentInputs>
}

// Shared chores live in households/{id}/chores. Personal chores live in their own collection,
// households/{id}/personalChores, so they can never mix into household lists, workload or contribution.
class ChoreRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) : ChoreActions {

    // These functions point to the different Firestore collections used by the app.
    // Household chores are shared between roommates.
    // Personal chores are stored separately so they don't affect household contribution
    // percentages or automatic assignment workload.
    // Completion records are stored separately to preserve the history of who completed each chore.
    private fun household(householdId: String) = db.collection("households").document(householdId)
    private fun householdChores(householdId: String) = household(householdId).collection("chores")
    private fun personalChores(householdId: String) = household(householdId).collection("personalChores")
    private fun completions(householdId: String) = household(householdId).collection("choreCompletions")

    // Chooses which Firestore collection to use based on the chore's scope.
    // This lets add, update, and delete operations work with either household or personal chores.
    private fun collectionFor(householdId: String, scope: String): CollectionReference =
        if (scope == ChoreScope.PERSONAL) personalChores(householdId) else householdChores(householdId)

    // Observes both chore collections and combines their results into one list.
    // Shared chores are visible to household members, while personal chores are filtered
    // by ownerUid so the current user only receives their own personal chores.
    // The ViewModel observes this Flow to keep the chore screen updated when Firestore changes.
    override fun getChores(householdId: String, myUid: String): Flow<List<Chore>> =
        combine(
            listen(householdChores(householdId).orderBy("dueEpochDay")),
            // only this user's own personal chores are ever requested, other people's never reach this phone
            listen(personalChores(householdId).whereEqualTo("ownerUid", myUid)).catch { emit(emptyList()) }
        ) { shared, personal -> shared + personal }

    // Creates a continuous listener for a Firestore query using callbackFlow.
    // Firestore sends a new snapshot whenever the collection changes.
    // Each Firestore document is converted into a domain Chore object, and resolveOccurrence()
    // calculates the current occurrence for recurring chores.
    // awaitClose removes the listener when the Flow is no longer being collected,
    // preventing unnecessary database listeners from staying active.
    private fun listen(query: Query): Flow<List<Chore>> = callbackFlow {
        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val today = todayEpochDay()
            val list = snapshot?.toObjects(ChoreDoc::class.java)
                // Resolve recurring chores against today's date so the UI receives the current occurrence,
                // rather than just the original chore document stored in Firestore.
                ?.map { resolveOccurrence(it.toDomain(), today) } ?: emptyList()
            trySend(list)
        }
        awaitClose { registration.remove() }
    }


    // Validates the chore information before saving it.
    // The chore is added to the collection based on whether it is personal or shared.
    // The Firestore write is not awaited, allowing the local cache to update immediately
    // and synchronize with the server when a connection is available.
    override suspend fun addChore(householdId: String, chore: Chore): Result<Unit> =
        runCatching {
            validateChore(chore.title, chore.assigneeUid, chore.category)?.let { throw IllegalArgumentException(it) }
            validateEndDate(chore.dueEpochDay, chore.repeatDays, chore.endEpochDay)?.let { throw IllegalArgumentException(it) }
            // not awaited: the write lands in this phone's copy at once and syncs when there's a connection, same as expenses
            collectionFor(householdId, chore.scope).add(chore.toData())
                .addOnFailureListener { Log.e("Chore Repo", "add failed on the server") }
            Unit
        }

    // Updates an existing chore document using its ID.
    // Only the fields that can change are updated, rather than replacing the entire document.
    // This is used when a user edits chore details or when completion changes the chore's state.
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

    // Deletes the chore itself but intentionally keeps its completion history.
    // This is important because deleting an old chore should not erase the contribution
    // credit that roommates already earned by completing it.
    override suspend fun deleteChore(householdId: String, chore: Chore): Result<Unit> =
        runCatching {
            require(chore.id.isNotBlank()) { "Missing chore id" }
            collectionFor(householdId, chore.scope).document(chore.id).delete()
                .addOnFailureListener { Log.e("Chore Repo", "delete failed on the server") }
            Unit
        }

    // Saves a separate record for a completed household chore occurrence.
    // The record includes who actually completed it and who it was originally assigned to.
    // This allows the contribution algorithm to credit the actual person who did the work,
    // even if someone else was originally assigned.
    override suspend fun recordCompletion(householdId: String, completion: ChoreCompletion): Result<Unit> =
        runCatching {
            completions(householdId).document(completion.id).set(completion.toData())
                .addOnFailureListener { Log.e("Chore Repo", "completion record failed on the server") }
            Unit
        }

    // Removes a completion record when a completed chore is undone.
    // This keeps the completion history consistent with the chore's current state
    // and prevents an undone chore from contributing to someone's percentage.
    override suspend fun removeCompletion(householdId: String, completionId: String): Result<Unit> =
        runCatching {
            completions(householdId).document(completionId).delete()
                .addOnFailureListener { Log.e("Chore Repo", "completion removal failed on the server") }
            Unit
        }

    private fun transfers(householdId: String) = household(householdId).collection("choreTransfers")

    // requests this person sent or received
    override fun observeTransfers(householdId: String, myUid: String): Flow<List<ChoreTransferRequest>> =
        combine(
            listenTransfers(transfers(householdId).whereEqualTo("toUid", myUid)),
            listenTransfers(transfers(householdId).whereEqualTo("fromUid", myUid))
        ) { received, sent -> (received + sent).distinctBy { it.id } }

    private fun listenTransfers(query: Query): Flow<List<ChoreTransferRequest>> = callbackFlow {
        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(snapshot?.toObjects(ChoreTransferDoc::class.java)?.map { it.toDomain() } ?: emptyList())
        }
        awaitClose { registration.remove() }
    }

    // Sending only records the offer. The chore stays with its current assignee.
    override suspend fun sendTransfer(householdId: String, request: ChoreTransferRequest): Result<Unit> =
        runCatching {
            require(request.fromUid != request.toUid) { "Pick a different roommate" }
            transfers(householdId).document(request.id).set(request.toData())
                .addOnFailureListener { Log.e("Chore Repo", "transfer request failed on the server") }
            Unit
        }

    // Accepting moves the chore and keeps who it was first given to. Declining changes nothing on the chore.
    // Either way the answer and its time are recorded. Needs a connection, so two phones can't both win.
    override suspend fun respondToTransfer(
        householdId: String,
        request: ChoreTransferRequest,
        accept: Boolean
    ): Result<Unit> = runCatching {
        val requestRef = transfers(householdId).document(request.id)
        val choreRef = householdChores(householdId).document(request.choreId)
        db.runTransaction { tx ->
            val requestSnap = tx.get(requestRef)
            val choreSnap = tx.get(choreRef)
            check(requestSnap.getString("status") == TransferStatus.PENDING) { "This request is no longer open" }
            if (accept) {
                check(choreSnap.exists()) { "This chore no longer exists" }
                check(choreSnap.getBoolean("done") != true) { "This chore is already finished" }
                check(choreSnap.getString("assigneeUid") == request.fromUid) {
                    "This chore is no longer with ${request.fromName}"
                }
                check(choreSnap.getLong("dueEpochDay") == request.dueEpochDay) { "This chore has moved on to a later date" }
                val changes = mutableMapOf<String, Any>(
                    "assigneeUid" to request.toUid,
                    "assigneeName" to request.toName
                )
                val alreadyRecorded = choreSnap.getLong("originalForEpochDay") == request.dueEpochDay &&
                        !choreSnap.getString("originalAssigneeUid").isNullOrBlank()
                if (!alreadyRecorded) {
                    changes["originalAssigneeUid"] = request.fromUid
                    changes["originalAssigneeName"] = request.fromName
                    changes["originalForEpochDay"] = request.dueEpochDay
                }
                tx.update(choreRef, changes)
            }
            val answer = if (accept) TransferStatus.ACCEPTED else TransferStatus.DECLINED
            tx.update(requestRef, mapOf("status" to answer, "respondedAt" to FieldValue.serverTimestamp()))
            null
        }.await()
        Unit
    }

    // Loads the latest information required for automatic chore assignment.
    // It retrieves active household members, their join and vacation information,
    // all household completion records, and currently unfinished household chores.
    // AutoAssign.kt uses this information to calculate contribution percentages,
    // check workload, and select an eligible roommate.
    override suspend fun loadAssignmentInputs(householdId: String): Result<AssignmentInputs> =
        runCatching {
            val today = todayEpochDay()
            val members = household(householdId).collection("members")
                .whereEqualTo("active", true).latest()
                .toObjects(Member::class.java)
                .map { m ->
                    // Converts the member's stored vacation dates into the domain VacationPeriod format.
                    // If the member is currently on vacation, their ongoing vacation is also included.
                    // The assignment algorithm uses these dates to exclude vacation periods from contribution tracking.
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

            // Retrieves the household's completion history.
            // Personal chores are not included because they are stored separately and should not
            // affect household contribution percentages.
            val history = completions(householdId).latest()
                .toObjects(ChoreCompletionDoc::class.java).map { it.toDomain() }

            // Loads shared chores and resolves recurring chores to their current occurrence.
            // Only unfinished chores are returned because completed chores should not count
            // toward someone's upcoming workload.
            val open = householdChores(householdId).latest()
                .toObjects(ChoreDoc::class.java)
                .map { resolveOccurrence(it.toDomain(), today) }
                .filter { !it.done }
            AssignmentInputs(members, history, open)
        }

    // Tries to retrieve the newest data directly from the Firestore server.
    // If the server is unavailable or takes longer than four seconds, it falls back
    // to the local Firestore cache so the app can still work offline.
    // This is particularly important when calculating assignments without a connection.
    private suspend fun Query.latest(): QuerySnapshot {
        val fromServer = try {
            withTimeoutOrNull(4_000) { get(Source.SERVER).await() }
        } catch (e: FirebaseFirestoreException) {
            null
        }
        return fromServer ?: get(Source.CACHE).await()
    }
}