package com.app.haven.viewmodel.home

import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.app.haven.actions.ExpenseActions
import com.app.haven.appContainer
import com.app.haven.domain.expense.ExpenseCategory
import com.app.haven.domain.expense.currMonthShareStats
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface HomeExpenseStatsState {
    data object Loading : HomeExpenseStatsState
    data class Success(val byCategory: Map<ExpenseCategory, Long>, val total: Long) : HomeExpenseStatsState
    data class Error(val message: String) : HomeExpenseStatsState
}

class HomeExpenseStatsViewModel(
    expenses: ExpenseActions,
    householdId: String,
    myUid: String,
) : ViewModel() {
    val state: StateFlow<HomeExpenseStatsState> = expenses.getExpenses(householdId)
        .map<_, HomeExpenseStatsState> { list ->
            val byCategory = currMonthShareStats(list, myUid)
            HomeExpenseStatsState.Success(byCategory, byCategory.values.sum())
        }
        .catch { emit(HomeExpenseStatsState.Error(it.message ?: "Couldn't load stats")) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, HomeExpenseStatsState.Loading)

    companion object {
        fun factory(householdId: String, myUid: String) = viewModelFactory {
            initializer {
                HomeExpenseStatsViewModel(appContainer().expenseRepository, householdId, myUid)
            }
        }
    }
}