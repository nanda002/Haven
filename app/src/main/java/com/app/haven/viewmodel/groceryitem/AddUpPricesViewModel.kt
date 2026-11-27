package com.app.haven.viewmodel.groceryitem

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.app.haven.actions.grocery.GroceryItemActions
import com.app.haven.appContainer
import com.app.haven.domain.expense.parseDollarsToCents
import com.app.haven.domain.grocerylist.GroceryItem
import com.app.haven.domain.grocerylist.onList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

//one row per item the user bought
data class PriceRow(
    val item: GroceryItem,
    val priceText: String = "",
    val included: Boolean = true,
    val priceError: String? = null
)

data class AddPricesUiState(
    val rows: List<PriceRow> = emptyList(),
    val totalCents: Long = 0L,
    val canContinue: Boolean = false,
    val loading: Boolean = true
)

class AddUpPricesViewModel(
    groceries: GroceryItemActions,
    householdId: String,
    private val myUid: String
) : ViewModel() {

    //what the user typed for each item (by item id), and which items they unticked
    private val priceTexts = MutableStateFlow<Map<String, String>>(emptyMap())
    private val excludedIds = MutableStateFlow<Set<String>>(emptySet())

    val uiState: StateFlow<AddPricesUiState> = combine(
        groceries.getItems(householdId),
        priceTexts,
        excludedIds
    ) { items, prices, excluded ->
        buildState(items, prices, excluded)
    }
        .catch { emit(AddPricesUiState(loading = false)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AddPricesUiState())

    // ---- events from the screen ----

    fun onPriceChanged(itemId: String, amt: String) {
        priceTexts.update { it + (itemId to amt) }
    }

    fun onIncludedChanged(itemId: String, included: Boolean) {
        excludedIds.update { if (included) it - itemId else it + itemId }
    }

    //gives the add expense form what it needs
    //does nothing if a price is still missing or invalid
    fun onContinue(onReady: (amountCents: Long, description: String, itemIds: List<String>) -> Unit) {
        val s = uiState.value
        if (!s.canContinue) return
        val included = s.rows.filter { it.included }.map { it.item }
        //passes to navigation
        onReady(s.totalCents, "Groceries: " + included.joinToString(", ") { it.description }, included.map { it.id })
    }

    // ---- helpers ----

    private fun buildState(
        items: List<GroceryItem>,
        prices: Map<String, String>,
        excluded: Set<String>
    ): AddPricesUiState {
        //same items the bottom bar counts: bought by me and still on the list
        val rows = items.onList().filter { it.bought && it.paidByUid == myUid }.map { item ->
            val text = prices[item.id].orEmpty()
            val cents = priceCents(text)
            PriceRow(
                item = item,
                priceText = text,
                included = item.id !in excluded,
                priceError = if (text.isNotBlank() && cents == null) "Enter a price like 3.49" else null
            )
        }
        val included = rows.filter { it.included }
        val includedCents = included.map { priceCents(it.priceText) }
        return AddPricesUiState(
            rows = rows,
            totalCents = includedCents.sumOf { it ?: 0L },
            canContinue = included.isNotEmpty() && includedCents.all { it != null },
            loading = false
        )
    }

    //null when blank, not a number, or 0
    private fun priceCents(text: String): Long? = parseDollarsToCents(text)?.takeIf { it > 0 }

    companion object {
        fun factory(householdId: String, myUid: String) = viewModelFactory {
            initializer {
                AddUpPricesViewModel(appContainer().groceryItemRepository, householdId, myUid)
            }
        }
    }
}
