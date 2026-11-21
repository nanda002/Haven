package com.app.haven.data.api

import com.app.haven.domain.grocerylist.ScannedItem
import com.app.haven.domain.grocerylist.parseProduct
import org.json.JSONException
import java.io.IOException

interface ProductLookupActions {
    suspend fun lookUp(code: String): Result<ScannedItem?>
}

class ProductLookupRepository(
    private val api: OpenFoodFactsApi = OpenFoodFactsApi()
) : ProductLookupActions {

    override suspend fun lookUp(code: String): Result<ScannedItem?> =
        try {
            Result.success(parseProduct(api.productJson(code)))
        } catch (e: HttpStatusException) {
            Result.failure(Exception(
                if (e.code == 429 || e.code >= 500) "Open Food Facts is busy. Try again in a minute."
                else "Couldn't look up this product (error ${e.code})."
            ))
        } catch (e: IOException) {
            Result.failure(Exception("Couldn't reach Open Food Facts. Check your connection."))
        } catch (e: JSONException) {
            Result.failure(Exception("Got an unexpected response from Open Food Facts."))
        }
}
