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
    //1 means product was found
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

//for usda api
fun parseResults(json: String) : List<String>
{
    val foods = JSONObject(json).optJSONArray("foods") ?: return emptyList()

    //we want just the names of the product so
    val names = mutableListOf<String>()
    for (i in 0 until foods.length())
    {
        val food = foods.optJSONObject(i) ?: continue
        if (food.isNull("description")) continue
        /*
        "foods": [
      { "fdcId": 746782, "description": "Milk, whole, 3.25% milkfat", "dataType": "SR Legacy" },
      { "fdcId": 746776, "description": "Milk, lowfat, fluid, 1% milkfat", "dataType": "SR Legacy" },
      { "fdcId": 2346404, "description": "Milk chocolate", "dataType": "Foundation" }
    ]
         */
        val name = cleanUsdaName(food.optString("description"))
        if (name.isNotBlank()) names.add(name)
    }
    return names
}

private val usdaSkipWords = setOf(
    "raw", "cooked", "canned", "frozen", "fresh", "dried", "boiled", "roasted",
    "regular", "enriched", "prepared", "unprepared", "commercially prepared",
    "meat only", "with salt", "without salt", "mature seeds", "fluid", "ns as to form",
    "smooth style", "chunk style"
)

fun cleanUsdaName(description: String): String {
    val parts = description.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    val main = parts.firstOrNull() ?: return ""
    val variety = parts.getOrNull(1)?.takeIf { v ->
        v.lowercase() !in usdaSkipWords && v.split(" ").size <= 2
    }
    val name = if (variety == null) main else "$variety $main"
    return name.lowercase().replaceFirstChar { it.uppercase() }
}

fun ScannedItem.listName(): String = if (brand == null) name else "$name ($brand)"
