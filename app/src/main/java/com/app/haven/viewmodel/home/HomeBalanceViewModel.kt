package com.app.haven.viewmodel.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.app.haven.actions.ExpenseActions
import com.app.haven.actions.SettleExpenseActions
import com.app.haven.appContainer
import com.app.haven.domain.expense.Balance
import com.app.haven.domain.expense.Expense
import com.app.haven.domain.expense.Settlement
import com.app.haven.domain.expense.calculateBalances
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

sealed interface HomeBalanceState {
    data object Loading : HomeBalanceState
    data class Success(val balance: Balance) : HomeBalanceState
    data class Error(val message: String) : HomeBalanceState
}

class HomeBalanceViewModel(
    expenses: ExpenseActions,
    settlements: SettleExpenseActions,
    householdId: String,
    myUid: String
) : ViewModel() {

    val state: StateFlow<HomeBalanceState> =
        combine<List<Expense>, List<Settlement>, HomeBalanceState>(
            expenses.getExpenses(householdId),
            settlements.getSettlements(householdId)
        ) { e, s -> HomeBalanceState.Success(calculateBalances(e, myUid, s)) }
            .catch { emit(HomeBalanceState.Error(it.message ?: "Couldn't load balances")) }
            // Eagerly: keep listening the whole time this ViewModel exists (the Home screen's lives
            // for the whole session), so the card is already up to date when you come back to Home
            .stateIn(viewModelScope, SharingStarted.Eagerly, HomeBalanceState.Loading)

    companion object {
        fun factory(householdId: String, myUid: String) = viewModelFactory {
            initializer {
                val c = appContainer()
                HomeBalanceViewModel(c.expenseRepository, c.settlementRepository, householdId, myUid)
            }
        }
    }
}