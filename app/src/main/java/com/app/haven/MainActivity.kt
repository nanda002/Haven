package com.app.haven

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
import com.app.haven.view.householdUI.HouseholdSetupScreen
import com.app.haven.navigation.MainScaffold
import com.app.haven.viewmodel.SessionState
import com.app.haven.viewmodel.SessionViewModel
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.app.haven.view.authUI.WelcomeScreen
import androidx.activity.compose.BackHandler

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HavenTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    HavenApp()
                }
            }
        }
    }
}

@Composable
fun HavenApp(vm: SessionViewModel = viewModel(factory = SessionViewModel.Factory)) {
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
        SessionState.InHousehold -> MainScaffold(onLogout = vm::logOut, onLeftHousehold = vm::onLeftHousehold)
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
