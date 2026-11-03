package com.app.haven.viewmodel.expense

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.haven.actions.ExpenseActions
import com.app.haven.actions.SettleExpenseActions
import com.app.haven.domain.expense.Balance
import com.app.haven.domain.expense.Expense
import com.app.haven.domain.expense.ExpenseCategory
import com.app.haven.domain.expense.Settlement
import com.app.haven.domain.expense.ShareDetails
import com.app.haven.domain.expense.calculateBalances
import com.app.haven.domain.expense.withShare
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
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
        val categoryTotalCents: Long
    ) : LedgerUiState
    data class Error(val message: String) : LedgerUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
class LedgerViewModel(
    private val repository: ExpenseActions,
    private val settlements: SettleExpenseActions,
    private val householdId: String,
    private val myUid: String
) : ViewModel() {

    private val selectedCategory = MutableStateFlow<ExpenseCategory?>(null)
    private val retryCount = MutableStateFlow(0)

    //settlements are included so the balance card matches the settle up screen
    val uiState: StateFlow<LedgerUiState> = retryCount
        .flatMapLatest {
            combine(
                repository.getExpenses(householdId),
                settlements.getSettlements(householdId),
                selectedCategory
            ) { expenses, settlementList, category ->
                buildState(expenses, settlementList, category)
            }.catch { e ->
                emit(LedgerUiState.Error(e.message ?: "Couldn't load expenses"))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LedgerUiState.Loading)

    fun onCategorySelected(category: ExpenseCategory?) { selectedCategory.value = category }

    fun onRetry() { retryCount.update { it + 1 } }

    //set when a delete fails, so the screen can show it once
    private val _deleteError = MutableStateFlow<String?>(null)
    val deleteError: StateFlow<String?> = _deleteError.asStateFlow()

    //the live expenses listener removes the row and updates the balance on its own
    fun onDelete(expenseId: String) {
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
        category: ExpenseCategory?
    ): LedgerUiState {
        if (expenses.isEmpty()) return LedgerUiState.Empty
        val visible = expenses.filter { category == null || it.category == category }
        return LedgerUiState.Success(
            rows = visible.map { withShare(it, myUid) },
            balance = calculateBalances(expenses, myUid, settlementList),
            selectedCategory = category,
            categoryTotalCents = visible.sumOf { it.amountCents }
        )
    }
}