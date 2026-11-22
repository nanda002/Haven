package com.app.haven.actions

import android.util.Log
import com.app.haven.data.model.GroceryItem as GroceryItemDoc
import com.app.haven.data.model.toData
import com.app.haven.data.model.toDomain
import com.app.haven.domain.grocerylist.GroceryItem
import com.app.haven.domain.grocerylist.validateGroceryItem
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

interface GroceryItemActions {
    fun getItems(householdId: String): Flow<List<GroceryItem>>
    suspend fun addItem(householdId: String, groceryitem: GroceryItem): Result<Unit>
    suspend fun updateItem(householdId: String, groceryitem: GroceryItem): Result<Unit>
    suspend fun deleteItem(householdId: String, groceryitemId: String): Result<Unit>
    //checkbox: bought = true saves who bought it and when; false clears those again
    suspend fun setBought(
        householdId: String, groceryitemId: String, bought: Boolean, buyerUid: String, buyerName: String
    ): Result<Unit>
    //after the expense for these items is saved: take them off the list (kept for Bought history)
    suspend fun markAddedToExpense(householdId: String, groceryitemIds: List<String>): Result<Unit>
    suspend fun markClearedFromList(householdId: String, groceryitemIds: List<String>): Result<Unit>
}

class GroceryItemRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) : GroceryItemActions
{
    private fun groceryitems(householdId: String) =
        db.collection("households").document(householdId).collection("groceryitem")

    override fun getItems(householdId: String): Flow<List<GroceryItem>> = callbackFlow {
        val registration = groceryitems(householdId)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.toObjects(GroceryItemDoc::class.java)
                    ?.map { it.toDomain() } ?: emptyList()
                trySend(list)
            }
        awaitClose { registration.remove() }
    }

    override suspend fun addItem(householdId: String, groceryitem: GroceryItem): Result<Unit> =
        runCatching {
            validateGroceryItem(groceryitem.description)?.let { throw IllegalArgumentException(it) }
            groceryitems(householdId).add(groceryitem.toData())
                .addOnFailureListener { Log.e("Grocery Item Repo", "add failed on the server") }
            Unit
        }

    override suspend fun updateItem(householdId: String, groceryitem: GroceryItem): Result<Unit> =
        runCatching {
            require(groceryitem.id.isNotBlank()) { "Missing Grocery Item id" }
            groceryitems(householdId).document(groceryitem.id).update(
                mapOf(
                    "description" to groceryitem.description,
                    "note" to groceryitem.note
                )
            ).addOnFailureListener { Log.e("Grocery Item Repo", "update failed on the server") }
            Unit
        }

    override suspend fun deleteItem(householdId: String, groceryitemId: String): Result<Unit> =
        runCatching {
            require(groceryitemId.isNotBlank()) { "Missing grocery item id" }
            groceryitems(householdId).document(groceryitemId).delete()
                .addOnFailureListener { Log.e("Grocery Item Repo", "delete failed on the server") }
            Unit
        }

    override suspend fun setBought(
        householdId: String, groceryitemId: String, bought: Boolean, buyerUid: String, buyerName: String
    ): Result<Unit> =
        runCatching {
            require(groceryitemId.isNotBlank()) { "Missing grocery item id" }
            if (bought) require(buyerUid.isNotBlank()) { "Missing user" }
            //only these fields, so a rename by someone else at the same time isn't overwritten
            groceryitems(householdId).document(groceryitemId).update(
                mapOf(
                    "bought" to bought,
                    "boughtAt" to if (bought) System.currentTimeMillis() else null,
                    "paidByUid" to if (bought) buyerUid else "",
                    "paidByName" to if (bought) buyerName else ""
                )
            ).addOnFailureListener { Log.e("Grocery Item Repo", "set bought failed on the server") }
            Unit
        }

    override suspend fun markAddedToExpense(householdId: String, groceryitemIds: List<String>): Result<Unit> =
        runCatching {
            val ids = groceryitemIds.filter { it.isNotBlank() }
            if (ids.isEmpty()) return@runCatching
            val batch = db.batch()
            ids.forEach { id -> batch.update(groceryitems(householdId).document(id), "addedToExpense", true) }
            batch.commit()
                .addOnFailureListener { Log.e("Grocery Item Repo", "mark added to expense failed on the server") }
            Unit
        }

    override suspend fun markClearedFromList(householdId: String, groceryitemIds: List<String>): Result<Unit> =
        runCatching {
            val ids = groceryitemIds.filter { it.isNotBlank() }
            if (ids.isEmpty()) return@runCatching
            val batch = db.batch()
            ids.forEach { id -> batch.update(groceryitems(householdId).document(id), "clearedFromList", true) }
            batch.commit()
                .addOnFailureListener { Log.e("Grocery Item Repo", "mark cleared from list failed on the server") }
            Unit
        }
}