package com.app.haven.actions

import com.app.haven.data.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

import com.google.firebase.firestore.Source
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

// User login and signup logic (backnd)
class UserActions(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    val isSignedIn: Boolean get() = auth.currentUser != null

    suspend fun signUp(username: String, email: String, password: String) {
        val uid = auth.createUserWithEmailAndPassword(email, password).await().user!!.uid
        db.collection("users").document(uid).set(
            mapOf(
                "username" to username,
                "email" to email,
                "householdId" to null,
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }

    suspend fun logIn(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password).await()
    }

    fun logOut() = auth.signOut()

    suspend fun loadUser(): User? {
        val uid = auth.currentUser?.uid ?: return null
        val ref = db.collection("users").document(uid)

        val snapshot = withTimeoutOrNull(3.seconds) {ref.get().await()}
            //if the above returns null, the try-catch block is executed to access the cache
            ?: try{
                ref.get(Source.CACHE).await()
            } catch (e: Exception) {
                throw IllegalStateException("You're offline and this phone has no saved data yet. Connect to the internet and try again.")
            }
        return snapshot.toObject(User::class.java)
    }
}