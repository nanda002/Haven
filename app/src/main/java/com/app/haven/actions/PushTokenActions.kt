package com.app.haven.actions

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

//This phone's push token, kept in users/{uid}.fcmTokens
//so the Cloud Function can send the user notifications
interface PushTokenActions {
    //after sign in
    suspend fun saveToken(): Result<Unit>
    //on logout
    suspend fun removeToken(): Result<Unit>
}
class PushTokenRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val messaging: FirebaseMessaging = FirebaseMessaging.getInstance(),
    private val pushOn: () -> Boolean = { true }
) : PushTokenActions {

    private fun userDoc(uid: String) = db.collection("users").document(uid)

    override suspend fun saveToken(): Result<Unit> = runCatching {
        val uid = auth.currentUser?.uid ?: error("Not signed in")
        if (!pushOn()) return@runCatching
        @Suppress("DEPRECATION")
        val token = messaging.token.await()
        //using arrayUnion to add to array of values
        userDoc(uid).update("fcmTokens", FieldValue.arrayUnion(token))
            .addOnFailureListener { Log.e(TAG, "saving the push token failed", it) }
        Unit
    }.onFailure { Log.w(TAG, "couldn't save the push token", it) }

    override suspend fun removeToken(): Result<Unit> = runCatching {
        val uid = auth.currentUser?.uid ?: return@runCatching
        @Suppress("DEPRECATION")
        val token = messaging.token.await()
        userDoc(uid).update("fcmTokens", FieldValue.arrayRemove(token)).await()
        @Suppress("DEPRECATION")
        messaging.deleteToken().await()
        Unit
    }.onFailure { Log.w(TAG, "couldn't remove the push token", it) }

    private companion object {
        const val TAG = "PushTokens"
    }
}
