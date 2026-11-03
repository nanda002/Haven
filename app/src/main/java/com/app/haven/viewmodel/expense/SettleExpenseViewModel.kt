package com.app.haven.viewmodel.expense

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.app.haven.appContainer
import com.app.haven.actions.ExpenseActions
import com.app.haven.actions.SettleExpenseActions
import com.app.haven.domain.expense.Settlement
import com.app.haven.domain.expense.balancesWithEach
import com.app.haven.domain.expense.parseDollarsToCents
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs

//one row per member the logged in user has a balance with
//netCents positive: they owe the user, negative: the user owes them
data class MemberRow(val uid: String, val name: String, val netCents: Long)

sealed interface SettleUiState {
    data object Loading : SettleUiState
    data object AllSettled : SettleUiState
    data class Success(val people: List<MemberRow>) : SettleUiState
    data class Error(val message: String) : SettleUiState
}

//the settle dialog, open while not null
data class SettleDialogState(
    val member: MemberRow,
    val amountText: String,          //pre-filled with the full balance
    val error: String? = null,
    val saving: Boolean = false
)

class SettleExpenseViewModel(
    private val expenses: ExpenseActions,
    private val settlements: SettleExpenseActions,
    private val householdId: String,
    private val myUid: String,
    members: Flow<List<MemberOption>>
) : ViewModel() {

    //balances come from expenses + settlements, names from members; all live
    val uiState: StateFlow<SettleUiState> = combine(
        expenses.getExpenses(householdId),
        settlements.getSettlements(householdId),
        members
    ) { expenseList, settlementList, memberList ->
        //balance list returns the uid : balance
        val balanceList = balancesWithEach(expenseList, myUid, settlementList)
        //nothing to show
        if (balanceList.isEmpty()) {
            SettleUiState.AllSettled
        } else {
            SettleUiState.Success(balanceList.map { p ->
                MemberRow(
                    p.uid,
                    memberList.firstOrNull { it.uid == p.uid }?.name ?: "Your roommate",
                    p.netCents
                )
            })
        }
    }.catch { e ->
        emit(SettleUiState.Error(e.message ?: "Couldn't load balances"))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettleUiState.Loading)

    //the dialog is for the user and their changes, the settleuistate is the live updates of the screen when the database is updated
    private val _dialog = MutableStateFlow<SettleDialogState?>(null)
    val dialog: StateFlow<SettleDialogState?> = _dialog.asStateFlow()

    // ---- events from the screen ----

    fun onSettleClicked(member: MemberRow) {
        _dialog.value = SettleDialogState(member, centsToText(abs(member.netCents)))
    }

    fun onSettleAmountChanged(text: String) =
        _dialog.update { it?.copy(amountText = text, error = null) }

    fun onSettleDismissed() { _dialog.value = null }

    fun onSettleConfirmed() {
        val d = _dialog.value ?: return
        if (d.saving) return

        //use the latest balance, in case it changed while the dialog was open
        val current = (uiState.value as? SettleUiState.Success)?.people?.firstOrNull { it.uid == d.member.uid }
        if (current == null) {
            _dialog.update { it?.copy(error = "Nothing left to settle with ${d.member.name}") }
            return
        }

        val amount = parseDollarsToCents(d.amountText)
        val error = validateSettlement(amount, abs(current.netCents))
        if (error != null || amount == null) {
            _dialog.update { it?.copy(error = error) }
            return
        }

        //whoever owes is the one paying
        val theyOweMe = current.netCents > 0
        val settlement = Settlement(
            fromUid = if (theyOweMe) current.uid else myUid,
            toUid = if (theyOweMe) myUid else current.uid,
            amountCents = amount,
            createdByUid = myUid
        )

        //the call to post the values in the database
        viewModelScope.launch {
            _dialog.update { it?.copy(saving = true, error = null) }
            settlements.addSettlement(householdId, settlement)
                .onSuccess { _dialog.value = null }
                .onFailure { e ->
                    _dialog.update { it?.copy(saving = false, error = e.message ?: "Couldn't record the payment") }
                }
        }
    }

    // ---- helpers ----

    //checks a partial settle amount against what's currently owed between the two people
    private fun validateSettlement(amountCents: Long?, balanceCents: Long): String? = when {
        amountCents == null -> "Enter an amount like 12.50"
        amountCents <= 0 -> "Enter an amount greater than 0"
        amountCents > balanceCents -> "Can't be more than what's owed"
        else -> null
    }

    private fun centsToText(cents: Long) = "${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"

    companion object {
        //the screen asks for this; repositories and members come from the app container
        fun factory(householdId: String, myUid: String) = viewModelFactory {
            initializer {
                val container = appContainer()
                SettleExpenseViewModel(
                    container.expenseRepository, container.settlementRepository, householdId, myUid,
                    container.householdActions.observeMembers(householdId)
                        .map { list -> list.map { MemberOption(it.uid, it.displayName) } }
                )
            }
        }
    }
}
