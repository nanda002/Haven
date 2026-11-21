package com.app.haven.view.authUI

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.haven.domain.InputLimits
import com.app.haven.view.common.LimitedTextField
import com.app.haven.viewmodel.auth.AuthViewModel

@Composable
fun AuthScreen(
    onAuthenticated: () -> Unit,
    startInSignUp: Boolean = false,
    onBack: () -> Unit = {},
    vm: AuthViewModel = viewModel()
) {
    BackHandler(onBack = onBack)
    Log.d("AuthScreen", "Composing AuthScreen")
    val ui by vm.ui.collectAsStateWithLifecycle()
    var isSignUp by remember { mutableStateOf(startInSignUp) }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                if (isSignUp) "Create your account" else "Log in",
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(Modifier.height(24.dp))

            if (isSignUp) {
                LimitedTextField(
                    value = username,
                    onValueChange = { username = it },
                    maxLength = InputLimits.USERNAME,
                    label = "Username",
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
            }
            LimitedTextField(
                value = email,
                onValueChange = { email = it.trim() },
                maxLength = InputLimits.EMAIL,
                label = "Email",
                showCounter = false,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            LimitedTextField(
                value = password,
                onValueChange = { password = it },
                maxLength = InputLimits.PASSWORD_MAX,
                label = "Password",
                showCounter = false,
                error = if (isSignUp && password.isNotEmpty() && password.length < InputLimits.PASSWORD_MIN)
                    "At least ${InputLimits.PASSWORD_MIN} characters" else null,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )

            ui.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(20.dp))
            Button(
                enabled = !ui.loading,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    if (isSignUp) vm.signUp(username, email, password, onAuthenticated)
                    else vm.logIn(email, password, onAuthenticated)
                }
            ) {
                if (ui.loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text(if (isSignUp) "Sign up" else "Log in")
            }

            TextButton(onClick = { isSignUp = !isSignUp; vm.clearError() }) {
                Text(if (isSignUp) "Already have an account? Log in" else "Don't have an account? Sign up")
            }
            TextButton(onClick = { vm.clearError(); onBack() }) { Text("Back") }
        }
    }
}