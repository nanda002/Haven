package com.app.haven.actions.chores

import android.util.Log
import com.app.haven.data.model.Chore as ChoreDoc
import com.app.haven.data.model.ChoreTransferDoc
import com.app.haven.data.model.toData
import com.app.haven.data.model.toDomain
import com.app.haven.domain.chore.ChoreTransferRequest
import com.app.haven.domain.chore.TransferStatus
import com.app.haven.domain.chore.resolveOccurrence
import com.app.haven.domain.chore.todayEpochDay
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.tasks.await

// Offering a chore to another roommate. Nothing changes on the chore until the receiver accepts.
interface ChoreTransferActions {
    fun observeTransfers(householdId: String, myUid: String): Flow<List<ChoreTransferRequest>>
    suspend fun sendTransfer(householdId: String, request: ChoreTransferRequest): Result<Unit>
    suspend fun respondToTransfer(householdId: String, request: ChoreTransferRequest, accept: Boolean): Result<Unit>
}

class ChoreTransferRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) : ChoreTransferActions {

    private fun household(householdId: String) = db.collection("households").document(householdId)
    private fun transfers(householdId: String) = household(householdId).collection("choreTransfers")
    private fun sharedChores(householdId: String) = household(householdId).collection("chores")

    // requests this person sent or received
    override fun observeTransfers(householdId: String, myUid: String): Flow<List<ChoreTransferRequest>> =
        combine(
            listen(transfers(householdId).whereEqualTo("toUid", myUid)),
            listen(transfers(householdId).whereEqualTo("fromUid", myUid))
        ) { received, sent -> (received + sent).distinctBy { it.id } }

    private fun listen(query: Query): Flow<List<ChoreTransferRequest>> = callbackFlow {
        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(snapshot?.toObjects(ChoreTransferDoc::class.java)?.map { it.toDomain() } ?: emptyList())
        }
        awaitClose { registration.remove() }
    }.catch { emit(emptyList()) }

    // Sending only records the offer. The chore stays with its current assignee.
    override suspend fun sendTransfer(householdId: String, request: ChoreTransferRequest): Result<Unit> =
        runCatching {
            require(request.fromUid != request.toUid) { "Pick a different roommate" }
            transfers(householdId).document(request.id).set(request.toData())
                .addOnFailureListener { Log.e("Chore Transfer", "request failed on the server") }
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
        val choreRef = sharedChores(householdId).document(request.choreId)
        db.runTransaction { tx ->
            val requestSnap = tx.get(requestRef)
            val choreSnap = tx.get(choreRef)
            check(requestSnap.getString("status") == TransferStatus.PENDING) { "This request is no longer open" }
            if (accept) {
                val stored = choreSnap.toObject(ChoreDoc::class.java)?.toDomain()
                check(stored != null) { "This chore no longer exists" }
                // The list shows a finished repeating chore as its next occurrence once the date has passed.
                // Check against that same view, not the older saved copy.
                val current = resolveOccurrence(stored, todayEpochDay())
                check(!current.done) { "This chore is already finished" }
                check(current.assigneeUid == request.fromUid) { "This chore is no longer with ${request.fromName}" }
                check(current.dueEpochDay == request.dueEpochDay) { "This chore has moved on to a later date" }

                val changes = mutableMapOf<String, Any>(
                    "assigneeUid" to request.toUid,
                    "assigneeName" to request.toName
                )
                if (current != stored) {
                    // save the rollover together with the hand-over
                    changes["dueEpochDay"] = current.dueEpochDay
                    changes["done"] = false
                    changes["nextAssigneeUid"] = ""
                    changes["nextAssigneeName"] = ""
                }
                // keep who it was first given to, once per occurrence
                val alreadyRecorded = current.originalForEpochDay == request.dueEpochDay &&
                        current.originalAssigneeUid.isNotBlank()
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
}
