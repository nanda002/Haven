package com.app.haven.domain.grocerylist

data class GroceryItem(
    val id: String = "",
    val description: String = "",
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
    val createdAtMillis: Long? = null
)