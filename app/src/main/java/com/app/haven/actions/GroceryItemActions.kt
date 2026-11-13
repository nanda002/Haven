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
}