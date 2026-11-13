package com.app.haven.domain.grocerylist

fun validateGroceryItem(description: String): String? = when {
    description.isBlank() -> "Enter a grocery item"
    else -> null
}