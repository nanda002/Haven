package com.app.haven

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.haven.ui.theme.HavenTheme
import com.app.haven.view.authUI.AuthScreen
import com.app.haven.view.common.householdUI.HouseholdSetupScreen
import com.app.haven.navigation.MainScaffold
import com.app.haven.notifications.EXTRA_NOTIFICATION_TYPE
import com.app.haven.notifications.TYPE_EXPENSE
import com.app.haven.viewmodel.SessionState
import com.app.haven.viewmodel.SessionViewModel
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.app.haven.view.authUI.WelcomeScreen

class MainActivity : ComponentActivity() {

    //true after tapping an expense notification
    private var openLedger by mutableStateOf(false)

    //if the app is closed, and the user taps on an expense notification
    //we want the user to be led to the ledger tab
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        //checks if app was opened by tapping a notification
        if (savedInstanceState == null) openLedger = intent.isExpenseNotification()
        setContent {
            HavenTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    HavenApp(openLedger = openLedger, onLedgerOpened = { openLedger = false })
                }
            }
        }
    }

    //app was already open when the notification was tapped
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.isExpenseNotification()) openLedger = true
    }
}

private fun Intent.isExpenseNotification() = getStringExtra(EXTRA_NOTIFICATION_TYPE) == TYPE_EXPENSE

@Composable
fun HavenApp(
    //from tapping an expense notification: open the Ledger tab once the main app shows
    openLedger: Boolean = false,
    onLedgerOpened: () -> Unit = {},
    vm: SessionViewModel = viewModel(factory = SessionViewModel.Factory)
) {
    val state by vm.state.collectAsStateWithLifecycle()

    when (val s = state) {
        SessionState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        SessionState.SignedOut -> {
            // false = welcome screen, true = login/sign-up form
            var showAuth by rememberSaveable { mutableStateOf(false) }
            if (showAuth) {
                AuthScreen(
                    onAuthenticated = vm::refresh,
                    onBack = { showAuth = false }
                )
            } else {
                WelcomeScreen(onGetStarted = { showAuth = true })
            }
        }
        SessionState.NeedsHousehold -> HouseholdSetupScreen(onDone = vm::refresh, onLogout = vm::logOut)
        SessionState.InHousehold -> MainScaffold(
            onLogout = vm::logOut,
            onLeftHousehold = vm::onLeftHousehold,
            openLedger = openLedger,
            onLedgerOpened = onLedgerOpened
        )
        is SessionState.Error -> Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(s.message, color = MaterialTheme.colorScheme.error)
            Button(onClick = vm::refresh) { Text("Retry") }
        }
    }
}
