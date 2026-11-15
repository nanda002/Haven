package com.app.haven.domain.grocerylist

fun validateGroceryItem(description: String): String? = when {
    description.isBlank() -> "Enter a grocery item"
    else -> null
}

//items still on the shopping list (not yet part of a saved expense)
fun List<GroceryItem>.onList(): List<GroceryItem> = filter { !it.addedToExpense }
