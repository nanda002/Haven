package com.app.haven.viewmodel.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.app.haven.actions.ExpenseActions
import com.app.haven.actions.HouseholdActions
import com.app.haven.actions.SettleExpenseActions
import com.app.haven.appContainer
import com.app.haven.data.model.Member
import com.app.haven.domain.expense.calculateBalances
import com.app.haven.domain.household.canLeaveHousehold
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

// Current + past members for the Home members popup
class HouseholdMembersViewModel(
    households: HouseholdActions,
    expenses: ExpenseActions,
    settlements: SettleExpenseActions,
    householdId: String
) : ViewModel() {

    val members: StateFlow<List<Member>> = households.observeMembers(householdId)
        .map { list -> list.sortedBy { it.displayName.lowercase() } }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pastMembers: StateFlow<List<Member>> = households.observePastMembers(householdId)
        .map { list -> list.sortedBy { it.displayName.lowercase() } }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    //required for calculating the settlements of each user for removing a member from the household
    val settledUids: StateFlow<Set<String>> = combine(
        members,
        expenses.getExpenses(householdId),
        settlements.getSettlements(householdId)
    ) { m, e, s ->
        m.map { it.uid }.filter { canLeaveHousehold(calculateBalances(e, it, s)) }.toSet()
    }
        .catch { emit(emptySet()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    companion object {
        fun factory(householdId: String) = viewModelFactory {
            initializer {
                val c = appContainer()
                HouseholdMembersViewModel(c.householdActions, c.expenseRepository, c.settlementRepository, householdId)
            }
        }
    }
}
