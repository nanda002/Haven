package com.app.haven.domain.grocerylist

data class GroceryItem(
    val id: String = "",
    val description: String = "",
    val note: String = "",
    //added by which user
    val addedByUid: String = "",
    val addedByName: String = "",
    //paid by which user
    val paidByUid: String = "",
    val paidByName: String = "",
    //for checking if the items were bought or not
    val bought: Boolean = false,
    //when it was checked off (millis); null while not bought
    val boughtAt: Long? = null,
    //true once it's part of a saved expense: hidden from the list, still shown in Bought history
    val addedToExpense: Boolean = false,
    val clearedFromList: Boolean = false,
    val createdAtMillis: Long? = null
)