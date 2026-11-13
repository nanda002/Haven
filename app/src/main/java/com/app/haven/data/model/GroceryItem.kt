package com.app.haven.data.model

import com.app.haven.domain.grocerylist.GroceryItem as DomainGrocery
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class GroceryItem (
    @DocumentId val id: String = "",
    //the name that the user gives the grocery item
    val description: String = "",
    //added by which user
    val addedByUid: String = "",
    val addedByName: String = "",
    //paid by which user
    val paidByUid: String = "",
    val paidByName: String = "",
    //for checking if the items were bought or not
    val bought: Boolean = false,
    val boughtAt: Long? = null,
    @ServerTimestamp val createdAt: Date? = null
)

//dto to transport model conversions and vice versa
fun GroceryItem.toDomain() = DomainGrocery(
    id = id,
    description = description,
    addedByUid = addedByUid,
    addedByName = addedByName,
    paidByUid = paidByUid,
    paidByName = paidByName,
    bought = bought,
    boughtAt = boughtAt,
    createdAtMillis = createdAt?.time
)

fun DomainGrocery.toData() = GroceryItem(
    id = id,
    description = description,
    addedByUid = addedByUid,
    addedByName = addedByName,
    paidByUid = paidByUid,
    paidByName = paidByName,
    bought = bought,
    boughtAt = boughtAt,
)