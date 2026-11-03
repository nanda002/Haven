package com.app.haven.tests

import android.util.Log
import com.app.haven.data.model.Household
import com.app.haven.data.model.Member
import com.app.haven.data.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlin.jvm.java

suspend fun runSmokeTest() {
    try {
        val auth = FirebaseAuth.getInstance()
        val db = FirebaseFirestore.getInstance()

        auth.signInWithEmailAndPassword("chris-test@example.com", "chris-test").await()
        val uid = auth.currentUser!!.uid

        // users/{uid}
        val userRef = db.collection("users").document(uid)
        userRef.set(mapOf(
            "username" to "chris",
            "email" to "chris-test@example.com",
            "householdId" to null,
            "createdAt" to FieldValue.serverTimestamp()
        )).await()

        // households/{id} + members/{uid} + user.householdId in one batch
        val householdRef = db.collection("households").document()
        val batch = db.batch()
        batch.set(householdRef, mapOf(
            "name" to "Test House",
            "inviteCode" to "ABC234",
            "createdBy" to uid,
            "memberIds" to listOf(uid),
            "createdAt" to FieldValue.serverTimestamp()
        ))
        batch.set(householdRef.collection("members").document(uid), mapOf(
            "displayName" to "chris",
            "active" to true,
            "joinedAt" to FieldValue.serverTimestamp()
        ))
        batch.update(userRef, "householdId", householdRef.id)
        batch.commit().await()

        // Read back into your classes
        val user = userRef.get().await().toObject(User::class.java)
        val household = householdRef.get().await().toObject(Household::class.java)
        val member = householdRef.collection("members").document(uid)
            .get().await().toObject(Member::class.java)

        Log.d("HavenTest", "USER: $user")
        Log.d("HavenTest", "HOUSEHOLD: $household")
        Log.d("HavenTest", "MEMBER: $member")
    } catch (e: Exception) {
        Log.e("HavenTest", "Smoke test failed", e)
    }
}