package com.app.haven.actions

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
import kotlinx.coroutines.tasks.await

interface ExpenseActions {
    fun getExpenses(householdId: String): Flow<List<Expense>>
    suspend fun addExpense(householdId: String, expense: Expense): Result<Unit>
    suspend fun updateExpense(householdId: String, expense: Expense): Result<Unit>
    suspend fun deleteExpense(householdId: String, expenseId: String): Result<Unit>
}

class ExpenseRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) : ExpenseActions {
    private fun expenses(householdId: String) =
        db.collection("households").document(householdId).collection("expenses")

    override fun getExpenses(householdId: String): Flow<List<Expense>> = callbackFlow {
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
            expenses(householdId).add(expense.toData()).await()
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
            ).await()
            Unit
        }

    override suspend fun deleteExpense(householdId: String, expenseId: String): Result<Unit> =
        runCatching {
            require(expenseId.isNotBlank()) { "Missing expense id" }
            expenses(householdId).document(expenseId).delete().await()
            Unit
        }
}