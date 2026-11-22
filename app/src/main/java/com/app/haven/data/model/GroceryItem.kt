package com.app.haven.data.model

import com.app.haven.domain.grocerylist.GroceryItem as DomainGrocery
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class GroceryItem (
    @DocumentId val id: String = "",
    //the name that the user gives the grocery item
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
    val boughtAt: Long? = null,
    //true once it's part of a saved expense: hidden from the list, still shown in Bought history
    val addedToExpense: Boolean = false,
    val clearedFromList: Boolean = false,
    @ServerTimestamp val createdAt: Date? = null
)

//dto to transport model conversions and vice versa
fun GroceryItem.toDomain() = DomainGrocery(
    id = id,
    description = description,
    note = note,
    addedByUid = addedByUid,
    addedByName = addedByName,
    paidByUid = paidByUid,
    paidByName = paidByName,
    bought = bought,
    boughtAt = boughtAt,
    addedToExpense = addedToExpense,
    clearedFromList = clearedFromList,
    createdAtMillis = createdAt?.time
)

fun DomainGrocery.toData() = GroceryItem(
    id = id,
    description = description,
    note = note,
    addedByUid = addedByUid,
    addedByName = addedByName,
    paidByUid = paidByUid,
    paidByName = paidByName,
    bought = bought,
    boughtAt = boughtAt,
    addedToExpense = addedToExpense,
    clearedFromList = clearedFromList,
    )