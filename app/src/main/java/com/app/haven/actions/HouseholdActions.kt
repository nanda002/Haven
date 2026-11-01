package com.app.haven.actions

import com.app.haven.data.model.Household
import com.app.haven.data.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom

// Creating or joining a household (backend only, no UI here)
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
                "active" to true,
                "joinedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.update(userRef, "householdId", householdRef.id)
        batch.commit().await()

        return Household(id = householdRef.id, name = name, inviteCode = code,
            createdBy = uid, memberIds = listOf(uid))
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
            tx.set(
                householdRef.collection("members").document(uid), mapOf(
                    "displayName" to (userSnap.getString("username") ?: ""),
                    "active" to true,
                    "joinedAt" to FieldValue.serverTimestamp()
                )
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
}