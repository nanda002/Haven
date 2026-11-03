package com.app.haven.actions


import com.app.haven.data.model.SettleExpenses
import com.app.haven.data.model.toData
import com.app.haven.data.model.toDomain
import com.app.haven.domain.expense.Expense
import com.app.haven.domain.expense.Settlement
import com.app.haven.domain.expense.validateShares
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

interface SettleExpenseActions {
    fun getSettlements(householdId: String): Flow<List<Settlement>>
    suspend fun addSettlement(householdId: String, settlement: Settlement): Result<Unit>
}
class SettleExpenseRepository (
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) : SettleExpenseActions {
    private fun settlements(householdId: String) =
        db.collection("households").document(householdId).collection("settlements")

    override fun getSettlements(householdId: String): Flow<List<Settlement>> = callbackFlow {
        val registration = settlements(householdId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.toObjects(SettleExpenses::class.java)
                    ?.map { it.toDomain() } ?: emptyList()
                trySend(list)
            }
        awaitClose { registration.remove() }
    }

    override suspend fun addSettlement(householdId: String, settlement: Settlement): Result<Unit> =
        runCatching {
            require(settlement.amountCents > 0) { "Enter an amount > 0" }
            require(settlement.fromUid.isNotBlank()) { "Enter an amount > 0" }
            require(settlement.toUid.isNotBlank()) { "Enter an amount > 0" }
            settlements(householdId).add(settlement.toData()).await()
            Unit
        }
}