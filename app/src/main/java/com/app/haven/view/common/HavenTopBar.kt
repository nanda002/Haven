package com.app.haven.view.common

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Hamburger on the far left; house icon + household name grouped and centered on the screen.
@Composable
fun HavenTopBar(title: String, onMenu: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(64.dp)
            .padding(horizontal = 8.dp)
    ) {
        IconButton(onClick = onMenu, modifier = Modifier.align(Alignment.CenterStart)) {
            Icon(Icons.Filled.Menu, contentDescription = "Open menu")
        }
        Row(
            Modifier.align(Alignment.Center).padding(horizontal = 56.dp),   // keeps long names off the menu button
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Filled.Home, contentDescription = null, modifier = Modifier.size(26.dp))   // was 20dp
            Text(
                title,
                fontSize = 36.sp,                                                                  // was 28sp
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}