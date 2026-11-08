package com.app.haven

import android.content.Context
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import com.app.haven.actions.ExpenseActions
import com.app.haven.actions.ExpenseDraftActions
import com.app.haven.actions.ExpenseDraftRepository
import com.app.haven.actions.ExpenseRepository
import com.app.haven.actions.HouseholdActions
import com.app.haven.actions.SettleExpenseActions
import com.app.haven.actions.SettleExpenseRepository
import com.app.haven.actions.UserActions
import com.app.haven.data.local.HavenDatabase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

// Manual dependency injection: the only place that knows about Room and Firebase.
// It builds the data layer once; ViewModel factories take what they need from here,
// so screens only ever ask for their ViewModel.
class AppContainer(context: Context) {

    //creating instances of each storage type
    private val localDb = HavenDatabase.get(context)
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val storage = FirebaseStorage.getInstance()

    //creating objects for each data model
    val userActions = UserActions(auth, firestore, storage)
    val householdActions = HouseholdActions(auth, firestore)

    val expenseRepository: ExpenseActions = ExpenseRepository(localDb.expenseDao(), firestore)
    val settlementRepository: SettleExpenseActions = SettleExpenseRepository(localDb.settlementDao(), firestore)
    val expenseDraftRepository: ExpenseDraftActions = ExpenseDraftRepository(localDb.expenseDraftDao())

    // on logout: remove the household's local copy and unsaved drafts, so the next user on this phone doesn't see them
    suspend fun clearLocalData() {
        localDb.expenseDao().clearAll()
        localDb.settlementDao().clearAll()
        localDb.expenseDraftDao().clearAll()
    }
}

// lets a ViewModel factory reach the container
fun CreationExtras.appContainer(): AppContainer =
    (this[APPLICATION_KEY] as HavenApplication).container
