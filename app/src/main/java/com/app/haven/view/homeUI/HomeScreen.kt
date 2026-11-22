package com.app.haven.view.homeUI

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.haven.view.common.AskForNotificationsOnce
import com.app.haven.view.common.DrawerContent
import com.app.haven.view.common.HavenTopBar
import com.app.haven.viewmodel.home.HomeBalanceViewModel
import com.app.haven.viewmodel.home.HomeExpenseStatsViewModel
import com.app.haven.viewmodel.home.HomeViewModel
import com.app.haven.viewmodel.home.HomeChoresViewModel
import com.app.haven.viewmodel.home.HomeGroceryViewModel
import com.app.haven.viewmodel.home.HouseholdMembersViewModel
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    householdId: String?,
    myUid: String?,
    onOpenProfile: () -> Unit,
    onOpenHouseholdDetails: () -> Unit,
    onOpenLedger: () -> Unit,
    onOpenChores: () -> Unit,
    onOpenGrocery: () -> Unit,
    vm: HomeViewModel = viewModel()
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { vm.load() }

    if (ui.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        scrimColor = Color.Black.copy(alpha = 0.4f),
        drawerContent = {
            if (householdId != null && myUid != null) {
                HomeDrawer(
                    householdId = householdId,
                    myUid = myUid,
                    onProfile = { scope.launch { drawerState.close() }; onOpenProfile() },
                    onHousehold = { scope.launch { drawerState.close() }; onOpenHouseholdDetails() }
                )
            }
        }
    ) {
        Column(Modifier.fillMaxSize()) {
            ui.household?.let { h ->
                HavenTopBar(title = h.name, onMenu = { scope.launch { drawerState.open() } })
            }
            Column(
                Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when {
                    ui.error != null -> {
                        Text(ui.error!!, color = MaterialTheme.colorScheme.error)
                        Button({ vm.load() }) { Text("Retry") }
                    }
                    else -> ui.household?.let {
                        //notification permission pop up for the user
                        AskForNotificationsOnce()
                        if (householdId != null && myUid != null) {
                            BalanceSection(householdId, myUid, onOpenLedger)
                            ExpenseStatsSection(householdId, myUid)
                            ChoresSection(householdId, myUid, onOpenChores)
                            GrocerySection(householdId, myUid, onOpenGrocery)
                        }
                    }
                }
                // log out lives on the Profile screen
            }
        }
    }
}

// The drawer needs my own name and photo; they come from the household member list.
@Composable
private fun HomeDrawer(
    householdId: String,
    myUid: String,
    onProfile: () -> Unit,
    onHousehold: () -> Unit,
    membersVm: HouseholdMembersViewModel = viewModel(factory = HouseholdMembersViewModel.factory(householdId))
) {
    val members by membersVm.members.collectAsStateWithLifecycle()
    val me = members.firstOrNull { it.uid == myUid }
    DrawerContent(
        name = me?.displayName.orEmpty(),
        photoUrl = me?.photoUrl,
        selected = null,
        onProfile = onProfile,
        onHousehold = onHousehold
    )
}
// Balance card; tap opens the Ledger tab
@Composable
private fun BalanceSection(
    householdId: String,
    myUid: String,
    onOpenLedger: () -> Unit,
    balanceVm: HomeBalanceViewModel = viewModel(factory = HomeBalanceViewModel.factory(householdId, myUid))
) {
    val state by balanceVm.state.collectAsStateWithLifecycle()
    HomeBalanceCard(state, onClick = onOpenLedger)
}

@Composable
private fun ExpenseStatsSection(
    householdId: String,
    myUid: String,
    statsVm: HomeExpenseStatsViewModel = viewModel(factory = HomeExpenseStatsViewModel.factory(householdId, myUid))
) {
    val state by statsVm.state.collectAsStateWithLifecycle()
    HomeExpenseStatsCard(state)
}

@Composable
private fun ChoresSection(
    householdId: String,
    myUid: String,
    onOpenChores: () -> Unit,
    choresVm: HomeChoresViewModel = viewModel(factory = HomeChoresViewModel.factory(householdId, myUid))
) {
    val mine by choresVm.myChores.collectAsStateWithLifecycle()
    HomeChoresCard(mine, onClick = onOpenChores)
}

@Composable
private fun GrocerySection(
    householdId: String,
    myUid: String,
    onOpenLedger: () -> Unit,
    groceryVm: HomeGroceryViewModel = viewModel(factory = HomeGroceryViewModel.factory(householdId, myUid))
) {
    val state by groceryVm.unboughtItems.collectAsStateWithLifecycle()
    HomeGroceryCard(state, onClick = onOpenLedger)
}