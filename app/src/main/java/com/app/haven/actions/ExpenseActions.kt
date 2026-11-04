package com.app.haven.actions

import android.util.Log
import com.app.haven.data.local.daos.ExpenseDao
import com.app.haven.data.local.entities.toDomain
import com.app.haven.data.local.entities.toEntity
import com.app.haven.data.model.Expense as ExpenseDoc
import com.app.haven.data.model.toData
import com.app.haven.data.model.toDomain
import com.app.haven.domain.expense.Expense
import com.app.haven.domain.expense.validateShares
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

interface ExpenseActions {
    fun getExpenses(householdId: String): Flow<List<Expense>>
    suspend fun addExpense(householdId: String, expense: Expense): Result<Unit>
    suspend fun updateExpense(householdId: String, expense: Expense): Result<Unit>
    suspend fun deleteExpense(householdId: String, expenseId: String): Result<Unit>
}

//writes go to Firestore; reads come from Room, which the Firestore listener keeps up to date
class ExpenseRepository(
    private val dao: ExpenseDao,
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) : ExpenseActions {
    private fun expenses(householdId: String) =
        db.collection("households").document(householdId).collection("expenses")

    override fun getExpenses(householdId: String): Flow<List<Expense>> = channelFlow {
        //cloud -> phone: copy every Firestore snapshot into Room while the screen is watching
        launch {
            firestoreExpenses(householdId).collect { list ->
                dao.replaceForHousehold(householdId, list.map { it.toEntity(householdId) })
            }
        }
        //the screen only ever sees what's in Room
        dao.observe(householdId).collect { rows -> send(rows.map { it.toDomain() }) }
    }

    private fun firestoreExpenses(householdId: String): Flow<List<Expense>> = callbackFlow {
        val registration = expenses(householdId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.toObjects(ExpenseDoc::class.java)
                    ?.map { it.toDomain() } ?: emptyList()
                trySend(list)
            }
        awaitClose { registration.remove() }
    }

    override suspend fun addExpense(householdId: String, expense: Expense): Result<Unit> =
        runCatching {
            require(expense.description.isNotBlank()) { "Enter a description" }
            validateShares(expense.amountCents, expense.shares)?.let {
                throw IllegalArgumentException(it)
            }
            //removing await() since it lags real time when saving an expense on the add expense
            //form. Writes are anyways queued in firestore's cache
            //expenses(householdId).add(expense.toData()).await()
            expenses(householdId).add(expense.toData()).addOnFailureListener { e -> Log.e("Expense Repo", "add failed on the server") }
            Unit
        }

    override suspend fun updateExpense(householdId: String, expense: Expense): Result<Unit> =
        runCatching {
            require(expense.id.isNotBlank()) { "Missing expense id" }
            require(expense.description.isNotBlank()) { "Enter a description" }
            validateShares(expense.amountCents, expense.shares)?.let {
                throw IllegalArgumentException(it)
            }
            expenses(householdId).document(expense.id).update(
                mapOf(
                    "description" to expense.description.trim(),
                    "amountCents" to expense.amountCents,
                    "category" to expense.category.name,
                    "paidByUid" to expense.paidByUid,
                    "paidByName" to expense.paidByName,
                    "shares" to expense.shares
                )
            ).addOnFailureListener { e -> Log.e("Expense Repo", "update failed on the server") }
            Unit
        }

    override suspend fun deleteExpense(householdId: String, expenseId: String): Result<Unit> =
        runCatching {
            require(expenseId.isNotBlank()) { "Missing expense id" }
            expenses(householdId).document(expenseId).delete().addOnFailureListener { e -> Log.e("Expense Repo", "delete failed on the server") }
            Unit
        }
}