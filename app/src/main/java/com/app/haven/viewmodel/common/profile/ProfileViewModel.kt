package com.app.haven.viewmodel.common.profile

import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.Job
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.app.haven.actions.household.HouseholdActions
import com.app.haven.actions.UserActions
import com.app.haven.actions.friendlyMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.app.haven.appContainer

// Vacation Mode as the Profile shows it: on or off, and the first day away
data class VacationInfo(val on: Boolean, val sinceEpochDay: Long)

data class ProfileInfo(
    val username: String,
    val email: String,
    val joinedAtMillis: Long?,
    val householdName: String?,
    val memberCount: Int,
    val photoUrl: String?,
    val uid: String? = null,
    val householdId: String? = null
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

    //for uploading the image
    private val _uploading = MutableStateFlow(false)
    val uploading: StateFlow<Boolean> = _uploading.asStateFlow()

    //set when an upload fails; cleared on the next attempt
    private val _uploadError = MutableStateFlow<String?>(null)
    val uploadError: StateFlow<String?> = _uploadError.asStateFlow()

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
                    user.householdId?.let { watchVacation(it, user.uid) }
                    ProfileUiState.Success(
                        ProfileInfo(
                            username = user.username,
                            email = user.email,
                            joinedAtMillis = user.createdAt?.time,
                            householdName = household?.name,
                            memberCount = household?.memberIds?.size ?: 0,
                            photoUrl = user.photoUrl,
                            uid = user.uid,
                            householdId = user.householdId
                        )
                    )
                }
            } catch (e: Exception) {
                ProfileUiState.Error(e.friendlyMessage())
            }
        }
    }

    //called with the image the user picked: upload it, then reload so the new picture shows
    fun onPhotoPicked(uri: Uri) {
        if (_uploading.value) return
        viewModelScope.launch {
            _uploading.value = true
            _uploadError.value = null
            try {
                users.uploadProfilePhoto(uri)
                load()
            } catch (e: Exception) {
                _uploadError.value = e.message ?: "Couldn't upload the picture"
            } finally {
                _uploading.value = false
            }
        }
    }


    // Vacation Mode: switched on from the Vacation screen (after chores are finished or handed over), switched off here
    private val _vacation = MutableStateFlow(VacationInfo(on = false, sinceEpochDay = 0L))
    val vacation: StateFlow<VacationInfo> = _vacation.asStateFlow()

    private val _vacationError = MutableStateFlow<String?>(null)
    val vacationError: StateFlow<String?> = _vacationError.asStateFlow()

    private var vacationHouseholdId: String? = null
    private var vacationJob: Job? = null

    private fun watchVacation(householdId: String, uid: String) {
        vacationHouseholdId = householdId
        vacationJob?.cancel()
        vacationJob = households.observeMembers(householdId)
            .onEach { members ->
                val me = members.firstOrNull { it.uid == uid }
                _vacation.value = VacationInfo(me?.vacation == true, me?.vacationStartEpochDay ?: 0L)
            }
            .catch { }
            .launchIn(viewModelScope)
    }

    // coming back: their contribution counts again from today and they can be given new chores again
    fun endVacation() {
        val householdId = vacationHouseholdId ?: return
        viewModelScope.launch {
            _vacationError.value = null
            runCatching { households.endVacation(householdId) }
                .onFailure { _vacationError.value = it.friendlyMessage() }
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
