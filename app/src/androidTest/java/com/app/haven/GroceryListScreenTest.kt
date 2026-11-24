package com.app.haven

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.app.haven.ui.theme.HavenTheme
import com.app.haven.view.groceryItemUI.MainGroceryItemScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class GroceryListScreenTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun addingAnItem_sendsTheTypedName() {
        var added = ""

        compose.setContent {
            HavenTheme {
                MainGroceryItemScreen(
                    myUid = "me",
                    items = emptyList(),
                    error = null,
                    onAdd = { name, done -> added = name; done() },
                    onToggleBought = {},
                    onRename = { _, _, _ -> },
                    onDelete = {}
                )
            }
        }
    compose.onNodeWithText("Add an item").performTextInput("Milk")
    compose.onNodeWithContentDescription("Add item").performClick()

    assertEquals("Milk", added)
}
}