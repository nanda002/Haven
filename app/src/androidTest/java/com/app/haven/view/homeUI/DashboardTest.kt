package com.app.haven.view.homeUI

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.app.haven.domain.chore.Chore
import com.app.haven.domain.chore.todayEpochDay
import com.app.haven.domain.grocerylist.GroceryItem
import com.app.haven.view.common.DrawerContent
import com.app.haven.view.common.HavenTopBar
import org.junit.Assert
import org.junit.Rule
import org.junit.Test

class DashboardTest {

    @get:Rule
    val rule = createComposeRule()

    private fun grocery(name: String) = GroceryItem(id = name, description = name)
    private fun choreDueToday(title: String) = Chore(
        id = title,
        title = title,
        assigneeUid = "me",
        assigneeName = "Me",
        dueEpochDay = todayEpochDay()
    )

    // ---- Chores widget ----

    @Test
    fun chores_dueToday_showsTitleAndCount() {
        rule.setContent {
            MaterialTheme {
                HomeChoresCard(
                    listOf(choreDueToday("Dishes")),
                    onClick = {})
            }
        }
        rule.onNodeWithText("Dishes").assertIsDisplayed()
        rule.onNodeWithText("1 chore to do").assertIsDisplayed()
    }

    @Test
    fun chores_tappingCard_callsOnClick() {
        var clicked = false
        rule.setContent {
            MaterialTheme {
                HomeChoresCard(
                    emptyList(),
                    onClick = { clicked = true })
            }
        }
        rule.onNodeWithText("Chores").performClick()
        Assert.assertTrue(clicked)
    }

    // ---- Top bar ----

    @Test
    fun topBar_showsHouseholdName_andMenuButtonOpensDrawer() {
        var opened = false
        rule.setContent {
            MaterialTheme {
                HavenTopBar(
                    title = "Apt5A",
                    onMenu = { opened = true })
            }
        }
        rule.onNodeWithText("Apt5A").assertIsDisplayed()
        rule.onNodeWithContentDescription("Open menu").performClick()
        Assert.assertTrue(opened)
    }

    // ---- Navigation drawer ----

    @Test
    fun drawer_showsCapitalizedName_YOU_andBothItems() {
        rule.setContent {
            MaterialTheme {
                DrawerContent(
                    name = "chris mary",
                    photoUrl = null,
                    selected = null,
                    onProfile = {},
                    onHousehold = {})
            }
        }
        rule.onNodeWithText("Chris Mary").assertIsDisplayed()
        rule.onNodeWithText("YOU").assertIsDisplayed()
        rule.onNodeWithText("Profile").assertIsDisplayed()
        rule.onNodeWithText("Household").assertIsDisplayed()
    }

    @Test
    fun drawer_tappingItems_callsNavigation() {
        var profile = false
        var household = false
        rule.setContent {
            MaterialTheme {
                DrawerContent(
                    "Sam",
                    null,
                    null,
                    onProfile = { profile = true },
                    onHousehold = { household = true })
            }
        }
        rule.onNodeWithText("Profile").performClick()
        rule.onNodeWithText("Household").performClick()
        Assert.assertTrue(profile && household)
    }

    // ---- Grocery widget ----

    @Test
    fun grocery_emptyState_showsStockedUpMessage() {
        rule.setContent { MaterialTheme { HomeGroceryCard(emptyList(), onClick = {}) } }
        rule.onNodeWithText("Grocery list").assertIsDisplayed()
        rule.onNodeWithText("All stocked up, nothing to buy!").assertIsDisplayed()
    }

    @Test
    fun grocery_withItems_showsCountAndItemNames() {
        rule.setContent {
            MaterialTheme {
                HomeGroceryCard(
                    listOf(grocery("Milk"), grocery("Eggs")),
                    onClick = {})
            }
        }
        rule.onNodeWithText("2 to buy").assertIsDisplayed()
        rule.onNodeWithText("Milk").assertIsDisplayed()
        rule.onNodeWithText("Eggs").assertIsDisplayed()
    }

    @Test
    fun grocery_moreThanFiveItems_showsPlusMore_andTapCallsOnClick() {
        var clicked = false
        val items = (1..7).map { grocery("Item$it") }
        rule.setContent { MaterialTheme { HomeGroceryCard(items, onClick = { clicked = true }) } }
        rule.onNodeWithText("+2 more").assertIsDisplayed()
        rule.onNodeWithText("Grocery list").performClick()
        Assert.assertTrue(clicked)
    }
}