package com.app.haven.actions

import com.app.haven.data.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

// User login and signup logic (backend only, no UI here)
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
        return db.collection("users").document(uid).get().await().toObject(User::class.java)
    }
}