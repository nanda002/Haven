package com.app.haven.data.api

import com.app.haven.domain.grocerylist.parseResults
import org.json.JSONException
import java.io.IOException

interface FoodSuggestionActions {
    suspend fun lookUp(query: String): Result<List<String>>
}

class FoodSuggestionRepo(
    private val api: UsdaApi = UsdaApi()
) : FoodSuggestionActions {
    override suspend fun lookUp(query: String): Result<List<String>> =
        try {
            Result.success(parseResults(api.searchJson(query)))
        } catch (e: HttpStatusException) {
            Result.failure(Exception(
                if (e.code == 429 || e.code >= 500) "USDA is busy. Try again in a minute."
                else "Couldn't look up this product (error ${e.code})."
            ))
        } catch (e: IOException) {
            Result.failure(Exception("Couldn't reach USDA. Check your connection."))
        } catch (e: JSONException) {
            Result.failure(Exception("Got an unexpected response from USDA"))
        }
}