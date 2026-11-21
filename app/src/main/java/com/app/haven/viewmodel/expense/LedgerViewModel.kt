package com.app.haven.viewmodel.expense

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.app.haven.appContainer
import com.app.haven.actions.ExpenseActions
import com.app.haven.actions.SettleExpenseActions
import com.app.haven.domain.expense.Balance
import com.app.haven.domain.expense.Expense
import com.app.haven.domain.expense.ExpenseCategory
import com.app.haven.domain.expense.Settlement
import com.app.haven.domain.expense.ShareDetails
import com.app.haven.domain.expense.calculateBalances
import com.app.haven.domain.expense.involvesFormerMember
import com.app.haven.domain.expense.withShare
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

//This is for the screen that consists of the balance view card for the logged in user, basically the main screen when the user moves to
//the ledger tab

//first describing the different ui states
sealed interface LedgerUiState {
    data object Loading : LedgerUiState
    data object Empty : LedgerUiState
    data class Success(
        val rows: List<ShareDetails>,
        val balance: Balance,
        val selectedCategory: ExpenseCategory?,
        val categoryTotalCents: Long,            //total of the expenses shown (after both filters)
        val onlyMine: Boolean = false            //"My expenses" ticked: only expenses the user paid or shares in
    ) : LedgerUiState
    data class Error(val message: String) : LedgerUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
class LedgerViewModel(
    private val repository: ExpenseActions,
    private val settlements: SettleExpenseActions,
    private val householdId: String,
    private val myUid: String,
    //uids of members still in the household; used to lock expenses that involve someone who left
    activeMemberUids: Flow<Set<String>>
) : ViewModel() {

    private val selectedCategory = MutableStateFlow<ExpenseCategory?>(null)
    private val onlyMine = MutableStateFlow(false)
    private val retryCount = MutableStateFlow(0)

    //null = not known yet; members can't fail the whole ledger, they only decide what's locked
    private val activeUids: Flow<Set<String>?> = activeMemberUids
        .map<Set<String>, Set<String>?> { it }
        .onStart { emit(null) }
        .catch { emit(null) }

    //settlements are included so the balance card matches the settle up screen
    val uiState: StateFlow<LedgerUiState> = retryCount
        .flatMapLatest {
            combine(
                repository.getExpenses(householdId),
                settlements.getSettlements(householdId),
                selectedCategory,
                onlyMine,
                activeUids
            ) { expenses, settlementList, category, mine, active ->
                buildState(expenses, settlementList, category, mine, active)
            }.catch { e ->
                emit(LedgerUiState.Error(e.message ?: "Couldn't load expenses"))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LedgerUiState.Loading)

    //for getting the current selected category value
    fun onCategorySelected(category: ExpenseCategory?) { selectedCategory.value = category }

     //when the filter for the user involved expenses is selected
    fun onOnlyMineChanged(checked: Boolean) { onlyMine.value = checked }

    fun onRetry() { retryCount.update { it + 1 } }

    //set when a delete fails, so the screen can show it once
    private val _deleteError = MutableStateFlow<String?>(null)
    val deleteError: StateFlow<String?> = _deleteError.asStateFlow()

    //the live expenses listener removes the row and updates the balance on its own
    fun onDelete(expenseId: String) {
        val row = (uiState.value as? LedgerUiState.Success)?.rows?.firstOrNull { it.expense.id == expenseId }
        //if the expense is locked because a previous member left
        if (row?.locked == true) {
            _deleteError.value = LOCKED_MESSAGE
            return
        }
        //update the delete to the database
        viewModelScope.launch {
            repository.deleteExpense(householdId, expenseId)
                .onFailure { e -> _deleteError.value = e.message ?: "Couldn't delete the expense" }
        }
    }

    fun onDeleteErrorShown() { _deleteError.value = null }

    //for the view
    private fun buildState(
        expenses: List<Expense>,
        settlementList: List<Settlement>,
        category: ExpenseCategory?,
        mine: Boolean,
        active: Set<String>?
    ): LedgerUiState {
        if (expenses.isEmpty()) return LedgerUiState.Empty
        //category chip and "My expenses" both narrow the list; the balance always uses every expense
        val visible = expenses.filter {
            (category == null || it.category == category) &&
                (!mine || it.paidByUid == myUid || myUid in it.shares)
        }
        return LedgerUiState.Success(
            rows = visible.map { withShare(it, myUid).copy(locked = involvesFormerMember(it, active)) },
            balance = calculateBalances(expenses, myUid, settlementList),
            selectedCategory = category,
            categoryTotalCents = visible.sumOf { it.amountCents },
            onlyMine = mine
        )
    }

    companion object {
        const val LOCKED_MESSAGE = "Someone in this expense has left the household, so it can't be changed."

        //the screen asks for this; the repositories come from the app container
        fun factory(householdId: String, myUid: String) = viewModelFactory {
            initializer {
                val container = appContainer()
                LedgerViewModel(
                    container.expenseRepository, container.settlementRepository, householdId, myUid,
                    container.householdActions.observeMembers(householdId).map { list -> list.map { it.uid }.toSet() }
                )
            }
        }
    }
}