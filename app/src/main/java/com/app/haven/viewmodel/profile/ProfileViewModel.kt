package com.app.haven.viewmodel.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.app.haven.actions.HouseholdActions
import com.app.haven.actions.UserActions
import com.app.haven.actions.friendlyMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.app.haven.appContainer

data class ProfileInfo(
    val username: String,
    val email: String,
    val joinedAtMillis: Long?,
    val householdName: String?,
    val memberCount: Int
)

//now we describe the different states
sealed interface ProfileUiState{
    data object Loading : ProfileUiState
    data class Success(val profile: ProfileInfo) : ProfileUiState
    data class Error(val message: String) : ProfileUiState
}

class ProfileViewModel(
    private val users: UserActions,
    private val households: HouseholdActions
): ViewModel() {

    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val ioState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.value = ProfileUiState.Loading
            _uiState.value = try {
                val user = users.loadUser()
                if (user == null) {
                    ProfileUiState.Error("Couldn't load profile")
                } else {
                    val household = runCatching { households.loadCurrentHousehold() }.getOrNull()
                    ProfileUiState.Success(
                        ProfileInfo(
                            username = user.username,
                            email = user.email,
                            joinedAtMillis = user.createdAt?.time,
                            householdName = household?.name,
                            memberCount = household?.memberIds?.size ?: 0
                        )
                    )
                }
            } catch (e: Exception) {
                ProfileUiState.Error(e.friendlyMessage())
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                ProfileViewModel(container.userActions, container.householdActions)
            }
        }
    }
}
