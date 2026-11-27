package com.app.haven.actions.household

import com.app.haven.data.model.User
import com.app.haven.data.model.household.Household
import com.app.haven.data.model.household.Member
import com.app.haven.domain.chore.epochDayOfMillis
import com.app.haven.domain.chore.todayEpochDay
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Transaction
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom

// Creating or joining a household (just backend for now)
class HouseholdActions(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    // No 0/O/1/I so codes are easy to read aloud
    private val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private val random = SecureRandom()

    private fun uid(): String = auth.currentUser?.uid ?: error("Not signed in")

    suspend fun createHousehold(name: String): Household {
        val uid = uid()
        val userRef = db.collection("users").document(uid)
        val user = userRef.get().await().toObject(User::class.java) ?: error("Profile not found")
        check(user.householdId == null) { "You already belong to a household" }

        val code = uniqueInviteCode()
        val householdRef = db.collection("households").document()

        // household + member + user.householdId succeed or fail together
        val batch = db.batch()
        batch.set(
            householdRef, mapOf(
                "name" to name,
                "inviteCode" to code,
                "createdBy" to uid,
                "memberIds" to listOf(uid),
                "createdAt" to FieldValue.serverTimestamp()
            )
        )
        batch.set(
            householdRef.collection("members").document(uid), mapOf(
                "displayName" to user.username,
                "photoUrl" to user.photoUrl,
                "active" to true,
                "joinedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.update(userRef, "householdId", householdRef.id)
        batch.commit().await()

        return Household(
            id = householdRef.id, name = name, inviteCode = code,
            createdBy = uid, memberIds = listOf(uid)
        )
    }

    suspend fun joinHousehold(rawCode: String) {
        val uid = uid()
        val code = rawCode.trim().uppercase()
        val userRef = db.collection("users").document(uid)

        val match = db.collection("households")
            .whereEqualTo("inviteCode", code).limit(1).get().await()
            .documents.firstOrNull()
            ?: throw IllegalArgumentException("That invite code doesn't match any household")
        val householdRef = match.reference

        // Transaction so a double-tap or two phones can't add the member twice
        db.runTransaction { tx ->
            val userSnap = tx.get(userRef)
            val householdSnap = tx.get(householdRef)

            if (userSnap.getString("householdId") != null)
                throw IllegalStateException("You already belong to a household")
            val members = householdSnap.get("memberIds") as? List<*> ?: emptyList<Any>()
            if (uid in members)
                throw IllegalStateException("You're already in this household")

            tx.update(householdRef, "memberIds", FieldValue.arrayUnion(uid))
            // merge, so someone coming back keeps their earlier stays and vacations
            tx.set(
                householdRef.collection("members").document(uid), mapOf(
                    "displayName" to (userSnap.getString("username") ?: ""),
                    "photoUrl" to userSnap.getString("photoUrl"),
                    "active" to true,
                    "vacation" to false,
                    "vacationStartEpochDay" to 0L,
                    "leftAt" to FieldValue.delete(),
                    "joinedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            tx.update(userRef, "householdId", householdRef.id)
            null
        }.await()
    }

    suspend fun loadCurrentHousehold(): Household? {
        val uid = auth.currentUser?.uid ?: return null
        val user = db.collection("users").document(uid).get().await().toObject(User::class.java)
        val householdId = user?.householdId ?: return null
        return db.collection("households").document(householdId).get().await()
            .toObject(Household::class.java)
    }

    private suspend fun uniqueInviteCode(): String {
        repeat(5) {
            val code = (1..6).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
            val taken = !db.collection("households")
                .whereEqualTo("inviteCode", code).limit(1).get().await().isEmpty
            if (!taken) return code
        }
        error("Couldn't generate an invite code, please try again")
    }

    // New code replaces the old one; the old one stops working immediately (join looks codes up by query)
    suspend fun regenerateInviteCode(): String {
        val uid = uid()
        val householdId = db.collection("users").document(uid).get().await()
            .getString("householdId") ?: error("You're not in a household")
        val code = uniqueInviteCode()
        db.collection("households").document(householdId).update("inviteCode", code).await()
        return code
    }

    // Leaves the current household. Member doc is kept (active=false) so old expenses still show their name
    suspend fun leaveHousehold() {
        val uid = uid()
        db.runTransaction { tx ->
            val householdId = tx.get(db.collection("users").document(uid)).getString("householdId")
                ?: throw IllegalStateException("You're not in a household")
            deactivateMember(tx, householdId, uid)
            null
        }.await()
    }

    suspend fun removeMember(memberUid: String) {
        val uid = uid()
        if (memberUid == uid) throw IllegalStateException("Use Leave household to leave")
        db.runTransaction { tx ->
            val householdId = tx.get(db.collection("users").document(uid)).getString("householdId")
                ?: throw IllegalStateException("You're not in a household")
            val memberIds = tx.get(db.collection("households").document(householdId)).get("memberIds") as? List<*>
            if (memberIds == null || memberUid !in memberIds) {
                throw IllegalStateException("They're not in your household")
            }
            deactivateMember(tx, householdId, memberUid)
            null
        }.await()
    }

    private fun deactivateMember(tx: Transaction, householdId: String, memberUid: String) {
        val householdRef = db.collection("households").document(householdId)
        val userRef = db.collection("users").document(memberUid)
        val memberRef = householdRef.collection("members").document(memberUid)
        val memberSnap = tx.get(memberRef)
        val today = todayEpochDay()
        val joinedDay = memberSnap.getTimestamp("joinedAt")?.let { epochDayOfMillis(it.toDate().time) } ?: today

        tx.update(householdRef, "memberIds", FieldValue.arrayRemove(memberUid))
        val leaving = mutableMapOf<String, Any>(
            "active" to false,
            "leftAt" to FieldValue.serverTimestamp(),
            // this stay is kept for good, so work done during it still counts for them if they come back
            "membershipPeriods" to FieldValue.arrayUnion(mapOf("joinedEpochDay" to joinedDay, "leftEpochDay" to today))
        )
        if (memberSnap.getBoolean("vacation") == true) {
            // leaving ends a vacation that is still going
            val start = memberSnap.getLong("vacationStartEpochDay") ?: today
            leaving["vacation"] = false
            leaving["vacationStartEpochDay"] = 0L
            leaving["vacationPeriods"] = FieldValue.arrayUnion(
                mapOf("startEpochDay" to start, "endEpochDay" to maxOf(start, today))
            )
        }
        tx.update(memberRef, leaving)
        tx.update(userRef, mapOf<String, Any?>("householdId" to null))
    }

    // Vacation Mode. Its start and end days are kept for good, so the contribution history can tell
    // which days the member was expected to take part. The end is the last day away.
    suspend fun startVacation(householdId: String, today: Long = todayEpochDay()) {
        val ref = db.collection("households").document(householdId).collection("members").document(uid())
        db.runTransaction { tx ->
            if (tx.get(ref).getBoolean("vacation") != true) {
                tx.update(ref, mapOf("vacation" to true, "vacationStartEpochDay" to today))
            }
            null
        }.await()
    }

    suspend fun endVacation(householdId: String, today: Long = todayEpochDay()) {
        val ref = db.collection("households").document(householdId).collection("members").document(uid())
        db.runTransaction { tx ->
            val snap = tx.get(ref)
            if (snap.getBoolean("vacation") == true) {
                val start = snap.getLong("vacationStartEpochDay") ?: today
                val changes = mutableMapOf<String, Any>("vacation" to false, "vacationStartEpochDay" to 0L)
                // Vacation days count from the day after it was switched on, so work done that day still counts.
                // A vacation that hadn't begun yet when it ended leaves no period behind.
                if (start <= today - 1) {
                    changes["vacationPeriods"] = FieldValue.arrayUnion(
                        mapOf("startEpochDay" to start, "endEpochDay" to today - 1)
                    )
                }
                tx.update(ref, changes)
            }
            null
        }.await()
    }

    // Live list of active members, used for "Paid by" and splitting expenses
    fun observeMembers(householdId: String): Flow<List<Member>> = observeMembersWhere(householdId, active = true)

    fun observePastMembers(householdId: String): Flow<List<Member>> = observeMembersWhere(householdId, active = false)

    private fun observeMembersWhere(householdId: String, active: Boolean): Flow<List<Member>> =
        callbackFlow {
            val registration =
                db.collection("households").document(householdId).collection("members")
                    .whereEqualTo("active", active)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            close(error)
                            return@addSnapshotListener
                        }
                        trySend(snapshot?.toObjects(Member::class.java) ?: emptyList())
                    }
            awaitClose { registration.remove() }
        }
}