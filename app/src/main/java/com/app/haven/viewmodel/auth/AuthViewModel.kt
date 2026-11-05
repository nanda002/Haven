package com.app.haven.viewmodel.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.haven.actions.UserActions
import com.app.haven.actions.friendlyMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthUiState(val loading: Boolean = false, val error: String? = null)

class AuthViewModel(private val users: UserActions = UserActions()) : ViewModel() {
    private val _ui = MutableStateFlow(AuthUiState())
    val ui: StateFlow<AuthUiState> = _ui.asStateFlow()

    fun clearError() { _ui.value = AuthUiState() }

    fun signUp(username: String, email: String, password: String, onSuccess: () -> Unit) {
        val problem = when {
            username.isBlank() -> "Enter a username"
            !email.contains("@") -> "Enter a valid email"
            password.length < 6 -> "Password must be at least 6 characters"
            else -> null
        }
        if (problem != null) { _ui.value = AuthUiState(error = problem); return }
        run(onSuccess) { users.signUp(username.trim(), email.trim(), password) }
    }

    fun logIn(email: String, password: String, onSuccess: () -> Unit) {
        if (email.isBlank() || password.isEmpty()) {
            _ui.value = AuthUiState(error = "Enter your email and password"); return
        }
        run(onSuccess) { users.logIn(email.trim(), password) }
    }

    private fun run(onSuccess: () -> Unit, block: suspend () -> Unit) {
        viewModelScope.launch {
            _ui.value = AuthUiState(loading = true)
            try {
                block()
                _ui.value = AuthUiState()
                onSuccess()
            } catch (e: Exception) {
                _ui.value = AuthUiState(error = e.friendlyMessage())
            }
        }
    }
}