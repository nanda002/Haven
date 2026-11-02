package com.app.haven.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.haven.actions.UserActions
import com.app.haven.actions.friendlyMessage
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds

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
            val signedIn = users.isSignedIn
            Log.d("SessionViewModel", "refresh: isSignedIn = $signedIn")
            _state.value = if (!signedIn) SessionState.SignedOut
            else try {
                Log.d("SessionViewModel", "refresh: loading user...")
                val user = withTimeout(5.seconds) {
                    users.loadUser()
                }
                Log.d("SessionViewModel", "refresh: user loaded, householdId = ${user?.householdId}")
                if (user?.householdId == null) SessionState.NeedsHousehold
                else SessionState.InHousehold
            } catch (_: TimeoutCancellationException) {
                Log.d("SessionViewModel", "refresh: timeout loading user")
                SessionState.Error("Connection timed out. Please check your internet connection.")
            } catch (e: Exception) {
                Log.e("SessionViewModel", "refresh error", e)
                SessionState.Error(e.friendlyMessage())
            }
        }
    }

    fun logOut() {
        users.logOut()
        _state.value = SessionState.SignedOut
    }
}
