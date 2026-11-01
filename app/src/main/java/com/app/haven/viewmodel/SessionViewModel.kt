package com.app.haven.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.haven.actions.UserActions
import com.app.haven.actions.friendlyMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SessionState {
    data object Loading : SessionState
    data object SignedOut : SessionState
    data object NeedsHousehold : SessionState
    data object InHousehold : SessionState
    data class Error(val message: String) : SessionState
}

// Decides which part of the app to show: login, household setup, or the main tabs
class SessionViewModel(private val users: UserActions = UserActions()) : ViewModel() {
    private val _state = MutableStateFlow<SessionState>(SessionState.Loading)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.value = SessionState.Loading
            _state.value = if (!users.isSignedIn) SessionState.SignedOut
            else try {
                if (users.loadUser()?.householdId == null) SessionState.NeedsHousehold
                else SessionState.InHousehold
            } catch (e: Exception) {
                SessionState.Error(e.friendlyMessage())
            }
        }
    }

    fun logOut() {
        users.logOut()
        _state.value = SessionState.SignedOut
    }
}