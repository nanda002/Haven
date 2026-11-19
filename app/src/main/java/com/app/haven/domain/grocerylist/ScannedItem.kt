package com.app.haven.domain.grocerylist

import org.json.JSONObject

data class ScannedItem(
    val code: String,
    val name: String,
    val brand: String?,
)

//how the API response looks like:
/*{
    "code": "5000112548167",
    "status": 1,
    "product": {
    "product_name": "Oat Drink",
    "brands": "Oatly,Oatly AB"
}
}*/
fun parseProduct(json: String) : ScannedItem?
{
    val item: ScannedItem
    val root = JSONObject(json)
    val code = root.optString("code")
    val status = root.optInt("status", 0)

    //get the inner json values
    val product = root.optJSONObject("product")
        ?: return null

    val name = product.optString("product_name")
    val brands = product.optString("brands")
    //get a single value from brands
    val firstBrand = brands.split(",").first().trim()

    if (status != 1) return null
    val productName = if (product.isNull("product_name")) "" else name.trim()
    //response has a generic field name, useful if product name is null
    val genericName = if (product.isNull("generic_name")) "" else product.optString("generic_name").trim()
    val finalName = productName.ifBlank { genericName }
    if (finalName.isBlank() || code.isBlank()) return null

    val finalBrand = if (product.isNull("brands")) null else firstBrand.ifBlank { null }

    item = ScannedItem(code = code, name = finalName, brand = finalBrand)
    return item
}

fun ScannedItem.listName(): String = if (brand == null) name else "$name ($brand)"
