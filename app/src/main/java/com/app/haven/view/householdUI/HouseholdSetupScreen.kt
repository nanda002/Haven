package com.app.haven.view.householdUI

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.haven.viewmodel.household.HouseholdViewModel

private enum class SetupMode { CHOOSE, CREATE, JOIN }

@Composable
fun HouseholdSetupScreen(
    onDone: () -> Unit,
    onLogout: () -> Unit,
    vm: HouseholdViewModel = viewModel()
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val username by vm.username.collectAsStateWithLifecycle()
    var mode by remember { mutableStateOf(SetupMode.CHOOSE) }
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Welcome! Let's set up your Haven.", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(24.dp))

        when (mode) {
            SetupMode.CHOOSE -> {
                Button({ mode = SetupMode.JOIN }, Modifier.fillMaxWidth()) { Text("Join an existing household") }
                Spacer(Modifier.height(12.dp))
                OutlinedButton({ mode = SetupMode.CREATE }, Modifier.fillMaxWidth()) { Text("Create a new household") }
                Spacer(Modifier.height(24.dp))
                // who is signed in, so it's clear which account is joining or creating
                username?.let {
                    Text("Logged in as $it", style = MaterialTheme.typography.bodyMedium)
                }
                TextButton(onLogout) { Text("Log out") }
            }

            SetupMode.CREATE -> {
                OutlinedTextField(name, { name = it }, label = { Text("Household name") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                FormFooter(ui.error, ui.loading, "Create",
                    onSubmit = { vm.create(name, onDone) },
                    onBack = { mode = SetupMode.CHOOSE; vm.clearError() })
            }

            SetupMode.JOIN -> {
                OutlinedTextField(code, { code = it.uppercase() }, label = { Text("Invite code") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth())
                FormFooter(ui.error, ui.loading, "Join",
                    onSubmit = { vm.join(code, onDone) },
                    onBack = { mode = SetupMode.CHOOSE; vm.clearError() })
            }
        }
    }
}

@Composable
private fun FormFooter(error: String?, loading: Boolean, label: String, onSubmit: () -> Unit, onBack: () -> Unit) {
    error?.let {
        Spacer(Modifier.height(8.dp))
        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
    Spacer(Modifier.height(16.dp))
    Button(onSubmit, Modifier.fillMaxWidth(), enabled = !loading) {
        if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text(label)
    }
    TextButton(onBack) { Text("Back") }
}