package com.app.haven.tests.grocery

import com.app.haven.domain.grocerylist.GroceryItem
import com.app.haven.domain.grocerylist.boughtDayLabel
import com.app.haven.domain.grocerylist.dayKey
import com.app.haven.domain.grocerylist.groupBoughtByDay
import com.app.haven.domain.grocerylist.onList
import com.app.haven.domain.grocerylist.ordinalSuffix
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class BoughtHistoryTest {

    private val utc = TimeZone.getTimeZone("UTC")

    private fun millis(year: Int, month: Int, day: Int, hour: Int = 12): Long =
        Calendar.getInstance(utc).apply {
            clear()
            set(year, month - 1, day, hour, 0)
        }.timeInMillis

    private fun item(id: String, boughtAt: Long?, bought: Boolean = true, added: Boolean = false) =
        GroceryItem(id = id, description = id, bought = bought, boughtAt = boughtAt, addedToExpense = added)

    @Test
    fun groupsByDayNewestFirst() {
        val items = listOf(
            item("milk", millis(2026, 11, 3, 9)),
            item("eggs", millis(2026, 11, 3, 18)),
            item("bread", millis(2026, 11, 5)),
            item("notBought", null, bought = false)
        )
        val days = groupBoughtByDay(items, utc)
        assertEquals(listOf("2026-11-05", "2026-11-03"), days.map { it.dayKey })
        assertEquals(listOf("bread"), days[0].items.map { it.id })
        //same day, newest first
        assertEquals(listOf("eggs", "milk"), days[1].items.map { it.id })
    }

    @Test
    fun itemsWithoutBoughtTimeGoLast() {
        val days = groupBoughtByDay(listOf(item("old", null), item("new", millis(2026, 11, 3))), utc)
        assertEquals(listOf("new"), days[0].items.map { it.id })
        assertEquals(null, days[1].dayKey)
        assertEquals("Earlier", boughtDayLabel(null))
    }

    @Test
    fun dayKeyUsesTheGivenTimeZone() {
        //11 PM in Boston on Nov 3 is already Nov 4 in UTC
        val lateNight = millis(2026, 11, 4, 4)
        assertEquals("2026-11-04", dayKey(lateNight, utc))
        assertEquals("2026-11-03", dayKey(lateNight, TimeZone.getTimeZone("America/New_York")))
    }

    @Test
    fun keysSortByDateAcrossMonths() {
        val days = groupBoughtByDay(listOf(item("a", millis(2026, 9, 30)), item("b", millis(2026, 10, 2))), utc)
        assertEquals(listOf("2026-10-02", "2026-09-30"), days.map { it.dayKey })
    }

    @Test
    fun labelUsesOrdinalAndYearOnlyWhenNotCurrent() {
        val now = millis(2026, 11, 14)
        assertEquals("Nov 3rd", boughtDayLabel("2026-11-03", now, utc))
        assertEquals("Dec 21st, 2025", boughtDayLabel("2025-12-21", now, utc))
    }

    @Test
    fun ordinalSuffixes() {
        assertEquals(listOf("st", "nd", "rd", "th"), (1..4).map(::ordinalSuffix))
        assertEquals(listOf("th", "th", "th"), (11..13).map(::ordinalSuffix))
        assertEquals(listOf("st", "nd", "rd"), (21..23).map(::ordinalSuffix))
    }

    @Test
    fun itemsAddedToExpenseLeaveTheList() {
        val list = listOf(item("a", 1L, added = true), item("b", 1L))
        assertEquals(listOf("b"), list.onList().map { it.id })
    }
}
