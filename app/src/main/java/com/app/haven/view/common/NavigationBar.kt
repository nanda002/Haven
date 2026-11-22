package com.app.haven.view.common

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.haven.domain.chore.titleCase

private val Navy = Color(0xFF1F3A5F)
private val SelectedBg = Color(0xFFD3E4FA)
private val Grey = Color(0xFF6B7280)

@Composable
fun DrawerContent(
    name: String,
    photoUrl: String?,
    selected: String?,
    onProfile: () -> Unit,
    onHousehold: () -> Unit
) {
    ModalDrawerSheet(
        drawerContainerColor = Color.White,   // set in the theme file
        drawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 24.dp, vertical = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Avatar(name, photoUrl, size = 100.dp, initialsSize = 20.sp)
            Column(verticalArrangement = Arrangement.Center) {
                Text(
                    titleCase(name).ifBlank { "Unnamed" },
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Navy
                )
                Text("YOU", fontSize = 11.sp, letterSpacing = 1.sp, color = Grey)
            }
        }
        HorizontalDivider(Modifier.padding(horizontal = 24.dp), color = Navy.copy(alpha = 0.12f))
        Spacer(Modifier.height(16.dp))
        DrawerRow("Profile", Icons.Outlined.Person, selected == "profile", onProfile)
        Spacer(Modifier.height(8.dp))
        DrawerRow("Household", Icons.Outlined.Home, selected == "household", onHousehold)
    }
}

@Composable
private fun DrawerRow(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = { Text(label, fontSize = 17.sp) },
        icon = { Icon(icon, contentDescription = null) },
        selected = selected,
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = SelectedBg,
            unselectedContainerColor = Color.Transparent,
            selectedIconColor = Navy, unselectedIconColor = Navy.copy(alpha = 0.7f),
            selectedTextColor = Navy, unselectedTextColor = Navy.copy(alpha = 0.85f)
        ),
        modifier = Modifier.padding(horizontal = 16.dp).height(56.dp)
    )
}