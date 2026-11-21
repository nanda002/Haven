package com.app.haven.tests.grocery

import com.app.haven.domain.grocerylist.cleanUsdaName
import com.app.haven.domain.grocerylist.parseResults
import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsdaParseTest {

    @Test
    fun putsVarietyBeforeMainName() {
        assertEquals("Cheddar cheese", cleanUsdaName("Cheese, cheddar"))
        assertEquals("Black beans", cleanUsdaName("Beans, black, mature seeds, canned"))
        assertEquals("Whole-wheat bread", cleanUsdaName("Bread, whole-wheat, commercially prepared"))
        assertEquals("Straw mushrooms", cleanUsdaName("Mushrooms, straw, canned"))
    }

    @Test
    fun skipsDescriptiveSecondPart() {
        assertEquals("Strawberries", cleanUsdaName("Strawberries, raw"))
        assertEquals("Peanut butter", cleanUsdaName("Peanut butter, smooth style, with salt"))
    }

    @Test
    fun skipsSecondPartLongerThanTwoWords() {
        assertEquals("Chicken", cleanUsdaName("Chicken, broilers or fryers, breast, meat only, raw"))
    }

    @Test
    fun keepsSinglePartNames() {
        assertEquals("Milk chocolate", cleanUsdaName("Milk chocolate"))
    }

    @Test
    fun fixesAllCapsNames() {
        assertEquals("Sharp cheddar cheese", cleanUsdaName("CHEDDAR CHEESE, SHARP"))
    }

    @Test
    fun parsesFoodsArray() {
        val json = """
            { "foods": [
                { "fdcId": 1, "description": "Strawberries, raw", "dataType": "SR Legacy" },
                { "fdcId": 2, "description": "Cheese, cheddar", "dataType": "SR Legacy" }
            ] }
        """.trimIndent()

        assertEquals(listOf("Strawberries", "Cheddar cheese"), parseResults(json))
    }

    @Test
    fun skipsNullBlankAndMissingDescriptions() {
        val json = """
            { "foods": [
                { "description": null },
                { "description": "   " },
                { "fdcId": 3 },
                { "description": "Eggs, whole" }
            ] }
        """.trimIndent()

        assertEquals(listOf("Whole eggs"), parseResults(json))
    }

    @Test
    fun noResultsGivesEmptyList() {
        assertTrue(parseResults("""{ "totalHits": 0, "foods": [] }""").isEmpty())
    }

    @Test
    fun missingFoodsArrayGivesEmptyList() {
        assertTrue(parseResults("""{ "error": "bad request" }""").isEmpty())
    }

    @Test(expected = JSONException::class)
    fun brokenJsonThrows() {
        parseResults("not json")
    }
}
