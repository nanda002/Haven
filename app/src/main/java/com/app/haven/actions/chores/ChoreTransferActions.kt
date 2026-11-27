package com.app.haven.actions.chores

import android.util.Log
import com.app.haven.data.model.chore.Chore as ChoreDoc
import com.app.haven.data.model.chore.ChoreTransferDoc
import com.app.haven.data.model.chore.toData
import com.app.haven.data.model.chore.toDomain
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
// A declined offer never touches the chore: it stays with the person who offered it. Choosing who is
// asked next after a decline is decided by the caller (see nextRecipientUid), not here.
interface ChoreTransferActions {
    // Live list of every request this person sent or received.
    fun observeTransfers(householdId: String, myUid: String): Flow<List<ChoreTransferRequest>>

    // Records a new offer. Does not change the chore.
    suspend fun sendTransfer(householdId: String, request: ChoreTransferRequest): Result<Unit>

    // Accepts or declines an offer. Only an accept changes the chore; both record the answer and its time.
    suspend fun respondToTransfer(householdId: String, request: ChoreTransferRequest, accept: Boolean): Result<Unit>
}

class ChoreTransferRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) : ChoreTransferActions {

    private fun household(householdId: String) = db.collection("households").document(householdId)
    private fun transfers(householdId: String) = household(householdId).collection("choreTransfers")
    private fun sharedChores(householdId: String) = household(householdId).collection("chores")

    // Merges two live queries (offers sent to me, offers sent by me) into one list without duplicates.
    // Firestore cannot OR two different fields in one query, hence the two listeners.
    override fun observeTransfers(householdId: String, myUid: String): Flow<List<ChoreTransferRequest>> =
        combine(
            listen(transfers(householdId).whereEqualTo("toUid", myUid)),
            listen(transfers(householdId).whereEqualTo("fromUid", myUid))
        ) { received, sent -> (received + sent).distinctBy { it.id } }

    // Turns a Firestore query into a Flow of requests that updates live and removes its listener when
    // the collector goes away. An error makes the flow emit an empty list instead of crashing.
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

    // Writes the offer under an id built from chore, due date and receiver, so one roommate can only ever
    // have one request document per occurrence. The write is not awaited, so it also works offline.
    // Callers must not send an id twice: writing over an existing request would reset its answer.
    // The chore stays with its current assignee.
    override suspend fun sendTransfer(householdId: String, request: ChoreTransferRequest): Result<Unit> =
        runCatching {
            require(request.fromUid != request.toUid) { "Pick a different roommate" }
            transfers(householdId).document(request.id).set(request.toData())
                .addOnFailureListener { Log.e("Chore Transfer", "request failed on the server") }
            Unit
        }

    // Answers an offer inside one transaction (needs a connection, so two phones can't both win).
    // Decline: only the request is marked DECLINED; the chore is not touched.
    // Accept: the request must still be open and the chore must still be exactly what was offered
    //   (not finished, still with the sender, still the same due date). Then the chore moves to the receiver
    //   and the original assignee is recorded once per occurrence. If it fails any check, nothing changes.
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
