package com.app.haven.viewmodel.expense

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.app.haven.appContainer
import com.app.haven.actions.ExpenseActions
import com.app.haven.actions.ExpenseDraftActions
import com.app.haven.domain.expense.Expense
import com.app.haven.domain.expense.ExpenseCategory
import com.app.haven.domain.expense.ExpenseDraft
import com.app.haven.domain.expense.parseDollarsToCents
import com.app.haven.domain.expense.remainingCents
import com.app.haven.domain.expense.splitExpenseLogic
import com.app.haven.domain.expense.validateShares
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MemberOption(val uid: String, val name: String)

//either even or uneven splits
enum class SplitMode { EVEN, CUSTOM }
enum class SaveStatus { EDITING, SAVING, SAVED }

data class AddExpenseUiState(
    val description: String = "",
    val amountText: String = "",
    val category: ExpenseCategory = ExpenseCategory.OTHER,
    val paidByUid: String = "",
    val members: List<MemberOption> = emptyList(),
    val splitMode: SplitMode = SplitMode.EVEN,
    val includedUids: Set<String> = emptySet(),                 // Even mode: who is in the split
    val customAmountTexts: Map<String, String> = emptyMap(),    // Custom mode: typed text per member
    val evenShares: Map<String, Long> = emptyMap(),             // preview for Even mode
    val remainingCents: Long? = null,                           // Custom mode: what's left to assign
    val descriptionError: String? = null,
    val amountError: String? = null,
    val splitError: String? = null,
    val saveError: String? = null,
    val saveStatus: SaveStatus = SaveStatus.EDITING,
    val isEditing: Boolean = false,          // true when changing an existing expense
    val loadingExpense: Boolean = false,     // edit mode: waiting for the expense to load
    val restoredDraft: Boolean = false       // add mode: the form was filled from a saved draft
)

//editingExpenseId == null -> adding a new expense, otherwise editing that one
class AddExpenseViewModel(
    private val repository: ExpenseActions,
    private val householdId: String,
    private val myUid: String,
    members: Flow<List<MemberOption>>,
    private val editingExpenseId: String? = null,
    //unsaved add-expense forms, kept only on this phone
    private val drafts: ExpenseDraftActions? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AddExpenseUiState(
            paidByUid = myUid,
            isEditing = editingExpenseId != null,
            loadingExpense = editingExpenseId != null
        )
    )
    val uiState: StateFlow<AddExpenseUiState> = _uiState.asStateFlow()

    private var membersInitialized = false

    //kept so editing doesn't rename the payer if they're no longer in the member list
    private var originalPaidByName: String? = null

    //true once the form was filled from a saved draft, so the member list doesn't overwrite who's ticked
    private var restoredFromDraft = false
    //true after a successful save, so a last pending auto-save can't bring the draft back
    private var draftFinished = false

    init {
        viewModelScope.launch {
            members.collect { list ->
                val first = !membersInitialized && list.isNotEmpty()
                if (first) membersInitialized = true
                _uiState.update { s ->
                    recompute(
                        s.copy(
                            members = list,
                            // when editing, who is included comes from the saved expense instead
                            // a restored draft also keeps its own ticked members
                            includedUids = if (first && editingExpenseId == null && !restoredFromDraft) list.map { it.uid }.toSet()
                                else s.includedUids
                        )
                    )
                }
            }
        }
        editingExpenseId?.let { loadExpense(it) }
        if (editingExpenseId == null && drafts != null) startDrafts(drafts)
    }

    //add mode only: fill the form from a saved draft, then keep the draft up to date as the user types
    @OptIn(FlowPreview::class)
    private fun startDrafts(drafts: ExpenseDraftActions) {
        viewModelScope.launch {
            drafts.loadDraft(householdId)?.let { d ->
                restoredFromDraft = true
                _uiState.update { s ->
                    recompute(
                        s.copy(
                            description = d.description,
                            amountText = d.amountText,
                            category = d.category,
                            paidByUid = d.paidByUid.ifBlank { myUid },
                            splitMode = if (d.evenSplit) SplitMode.EVEN else SplitMode.CUSTOM,
                            includedUids = d.includedUids,
                            customAmountTexts = d.customAmountTexts,
                            restoredDraft = true
                        )
                    )
                }
            }
            //save shortly after the user stops typing, not on every key press
            _uiState.map { it.toDraft() }
                .distinctUntilChanged()
                .debounce(500)
                .collect { d ->
                    if (draftFinished) return@collect
                    if (d.isBlank()) drafts.deleteDraft(householdId) else drafts.saveDraft(householdId, d)
                }
        }
    }

    private fun loadExpense(expenseId: String) {
        viewModelScope.launch {
            val expense = runCatching {
                repository.getExpenses(householdId).first().firstOrNull { it.id == expenseId }
            }.getOrNull()
            if (expense == null) {
                _uiState.update { it.copy(loadingExpense = false, saveError = "Couldn't load this expense") }
                return@launch
            }
            originalPaidByName = expense.paidByName
            // saved shares that match an even split open in Even mode, anything else in Custom mode
            val isEven = expense.shares == splitExpenseLogic(expense.amountCents, expense.shares.keys.toList())
            _uiState.update { s ->
                recompute(
                    s.copy(
                        description = expense.description,
                        amountText = centsToText(expense.amountCents),
                        category = expense.category,
                        paidByUid = expense.paidByUid,
                        splitMode = if (isEven) SplitMode.EVEN else SplitMode.CUSTOM,
                        includedUids = expense.shares.keys,
                        customAmountTexts = if (isEven) emptyMap() else expense.shares.mapValues { centsToText(it.value) },
                        loadingExpense = false
                    )
                )
            }
        }
    }

    // ---- events from the screen ----

    fun onDescriptionChanged(text: String) =
        _uiState.update { it.copy(description = text, descriptionError = null, saveError = null) }

    fun onAmountChanged(text: String) =
        _uiState.update { recompute(it.copy(amountText = text, amountError = null, splitError = null, saveError = null)) }

    fun onCategorySelected(category: ExpenseCategory) =
        _uiState.update { it.copy(category = category) }

    fun onPaidByChanged(uid: String) =
        _uiState.update { it.copy(paidByUid = uid) }

    fun onSplitModeChanged(mode: SplitMode) = _uiState.update { s ->
        if (mode == s.splitMode) return@update s
        if (mode == SplitMode.CUSTOM) {
            // start from the even split, so the user adjusts instead of retyping
            val texts = s.members.associate { m ->
                m.uid to (s.evenShares[m.uid]?.let(::centsToText) ?: "")
            }
            recompute(s.copy(splitMode = mode, customAmountTexts = texts, splitError = null))
        } else {
            recompute(s.copy(splitMode = mode, splitError = null))
        }
    }

    fun onMemberToggled(uid: String) = _uiState.update { s ->
        val next = if (uid in s.includedUids) s.includedUids - uid else s.includedUids + uid
        recompute(s.copy(includedUids = next, splitError = null))
    }

    fun onCustomAmountChanged(uid: String, text: String) = _uiState.update { s ->
        recompute(s.copy(customAmountTexts = s.customAmountTexts + (uid to text), splitError = null))
    }

    fun onSaveClicked() {
        val s = _uiState.value
        if (s.saveStatus == SaveStatus.SAVING || s.loadingExpense) return

        val description = s.description.trim()
        val amount = parseDollarsToCents(s.amountText)?.takeIf { it > 0 }

        val descriptionError = if (description.isEmpty()) "Enter a description" else null
        val amountError = if (amount == null) "Enter an amount greater than 0, like 12.50" else null

        var splitError: String? = null
        var shares: Map<String, Long> = emptyMap()
        if (amount != null) {
            if (s.splitMode == SplitMode.EVEN) {
                if (s.includedUids.isEmpty()) splitError = "Pick at least one person"
                else shares = splitExpenseLogic(amount, s.includedUids.toList())
            } else {
                val hasBadText = s.customAmountTexts.values.any { it.isNotBlank() && parseDollarsToCents(it) == null }
                if (hasBadText) splitError = "Check the amounts, use numbers like 12.50"
                else shares = customShares(s)
            }
            if (splitError == null) splitError = validateShares(amount, shares)
        }

        if (descriptionError != null || amountError != null || splitError != null || amount == null) {
            _uiState.update {
                it.copy(descriptionError = descriptionError, amountError = amountError, splitError = splitError)
            }
            return
        }

        val payerName = s.members.firstOrNull { it.uid == s.paidByUid }?.name ?: originalPaidByName ?: "Roommate"
        val expense = Expense(
            id = editingExpenseId ?: "",
            description = description,
            amountCents = amount,
            category = s.category,
            paidByUid = s.paidByUid,
            paidByName = payerName,
            shares = shares
        )

        viewModelScope.launch {
            _uiState.update { it.copy(saveStatus = SaveStatus.SAVING, saveError = null) }
            val result = if (editingExpenseId != null) repository.updateExpense(householdId, expense)
                else repository.addExpense(householdId, expense)
            result
                .onSuccess {
                    //the expense is saved now, so its draft isn't needed anymore
                    if (editingExpenseId == null) {
                        draftFinished = true
                        drafts?.deleteDraft(householdId)
                    }
                    _uiState.update { it.copy(saveStatus = SaveStatus.SAVED) }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(saveStatus = SaveStatus.EDITING, saveError = e.message ?: "Couldn't save the expense")
                    }
                }
        }
    }

    fun onSavedHandled() = _uiState.update { it.copy(saveStatus = SaveStatus.EDITING) }

    //start fresh: empty form (everyone ticked again) and no saved draft
    fun onClearClicked() {
        _uiState.update { s ->
            recompute(
                AddExpenseUiState(
                    paidByUid = myUid,
                    members = s.members,
                    includedUids = s.members.map { it.uid }.toSet()
                )
            )
        }
        viewModelScope.launch { drafts?.deleteDraft(householdId) }
    }

    // ---- helpers ----

    private fun AddExpenseUiState.toDraft() = ExpenseDraft(
        householdId = householdId,
        description = description,
        amountText = amountText,
        category = category,
        paidByUid = paidByUid,
        evenSplit = splitMode == SplitMode.EVEN,
        includedUids = includedUids,
        customAmountTexts = customAmountTexts
    )

    //nothing typed yet, so there's nothing worth keeping
    private fun ExpenseDraft.isBlank() =
        description.isBlank() && amountText.isBlank() && customAmountTexts.values.all { it.isBlank() }

    private fun customShares(s: AddExpenseUiState): Map<String, Long> =
        s.customAmountTexts.mapNotNull { (uid, text) ->
            val cents = if (text.isBlank()) 0L else parseDollarsToCents(text)
            if (cents != null && cents > 0) uid to cents else null
        }.toMap()

    // Refreshes the live preview (even shares, or remaining amount in Custom mode)
    private fun recompute(s: AddExpenseUiState): AddExpenseUiState {
        val amount = parseDollarsToCents(s.amountText)?.takeIf { it > 0 }
        val even = if (amount != null && s.includedUids.isNotEmpty())
            splitExpenseLogic(amount, s.includedUids.toList()) else emptyMap()
        val remaining = if (s.splitMode == SplitMode.CUSTOM && amount != null)
            remainingCents(amount, customShares(s)) else null
        return s.copy(evenShares = even, remainingCents = remaining)
    }

    private fun centsToText(cents: Long) = "${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"

    companion object {
        //the screen asks for this; repositories and members come from the app container
        fun factory(householdId: String, myUid: String, expenseId: String? = null) = viewModelFactory {
            initializer {
                val container = appContainer()
                AddExpenseViewModel(
                    container.expenseRepository, householdId, myUid,
                    container.householdActions.observeMembers(householdId)
                        .map { list -> list.map { MemberOption(it.uid, it.displayName) } },
                    editingExpenseId = expenseId,
                    drafts = container.expenseDraftRepository
                )
            }
        }
    }
}