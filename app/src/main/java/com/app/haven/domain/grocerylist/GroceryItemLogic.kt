package com.app.haven.domain.grocerylist

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

fun validateGroceryItem(description: String): String? = when {
    description.isBlank() -> "Enter a grocery item"
    else -> null
}

//items still on the shopping list (not yet part of a saved expense)
fun List<GroceryItem>.onList(): List<GroceryItem> = filter { !it.addedToExpense && !it.clearedFromList }

data class BoughtDay(val dayKey: String?, val items: List<GroceryItem>)

//bought items grouped by the day they were checked off
fun groupBoughtByDay(items: List<GroceryItem>, timeZone: TimeZone = TimeZone.getDefault()): List<BoughtDay> {
    val bought = items.filter { it.bought }.sortedByDescending { it.boughtAt ?: Long.MIN_VALUE }
    val (dated, undated) = bought.partition { it.boughtAt != null }
    val days = dated.groupBy { dayKey(it.boughtAt!!, timeZone) }
        .map { (day, list) -> BoughtDay(day, list) }
        .sortedByDescending { it.dayKey }
    return if (undated.isEmpty()) days else days + BoughtDay(null, undated)
}

fun historySuggestions(allItems: List<GroceryItem>, query: String, limit: Int = 3): List<String> {
    val q = query.trim()
    if (q.isEmpty()) return emptyList()

    val onListNow = allItems.onList()
//        .filter { !it.bought }
        .map { it.description.trim().lowercase() }
        .toSet()

    return allItems
        .filter { it.description.isNotBlank() }
        .groupBy { it.description.trim().lowercase() }
        //not showing items currently on pending list
        .filterKeys { it !in onListNow }
        .filterKeys { name -> name.split(" ").any { it.startsWith(q, ignoreCase = true) } }
        .values
        //sorted between items by checking its size i.e. the most popular
        //or frequently bought item
        //then a tiebreaker based on timestamp if first comparison returns equal size
        .sortedWith(
            compareByDescending<List<GroceryItem>> { it.size }
                .thenByDescending { group -> group.maxOf { it.createdAtMillis ?: 0L } }
        )
        .map { group -> group.maxBy { it.createdAtMillis ?: 0L }.description.trim() }
        .take(limit)
}

fun dayKey(millis: Long, timeZone: TimeZone = TimeZone.getDefault()): String {
    val cal = Calendar.getInstance(timeZone).apply { timeInMillis = millis }
    return String.format(
        Locale.US, "%04d-%02d-%02d",
        cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)
    )
}

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

fun boughtDayLabel(
    dayKey: String?,
    nowMillis: Long = System.currentTimeMillis(),
    timeZone: TimeZone = TimeZone.getDefault()
): String {
    if (dayKey == null) return "Earlier"
    val parts = dayKey.split("-").mapNotNull { it.toIntOrNull() }
    if (parts.size != 3) return dayKey
    val (year, month, day) = parts
    val monthName = MONTHS.getOrNull(month - 1) ?: return dayKey
    val nowYear = Calendar.getInstance(timeZone).apply { timeInMillis = nowMillis }.get(Calendar.YEAR)
    val label = "$monthName $day${ordinalSuffix(day)}"
    return if (year == nowYear) label else "$label, $year"
}

fun ordinalSuffix(day: Int): String = when {
    day % 100 in 11..13 -> "th"
    day % 10 == 1 -> "st"
    day % 10 == 2 -> "nd"
    day % 10 == 3 -> "rd"
    else -> "th"
}