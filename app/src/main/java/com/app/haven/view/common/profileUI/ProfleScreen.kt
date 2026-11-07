package com.app.haven.view.common.profileUI

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.haven.viewmodel.common.profile.ProfileInfo
import com.app.haven.viewmodel.common.profile.ProfileUiState
import com.app.haven.viewmodel.common.profile.ProfileViewModel
import coil3.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

//Profile: who the user is, their household, and placeholder settings/privacy sections
@Composable
fun ProfileScreen(
    onClose: () -> Unit,
    onLogout: () -> Unit,
    vm: ProfileViewModel = viewModel(factory = ProfileViewModel.Factory)
) {
    val state by vm.ioState.collectAsStateWithLifecycle()
    val uploading by vm.uploading.collectAsStateWithLifecycle()
    val uploadError by vm.uploadError.collectAsStateWithLifecycle()

    //Android's photo picker: no storage permission needed; gives back the chosen image (or null if cancelled)
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(vm::onPhotoPicked)
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClose) { Icon(Icons.Default.Close, contentDescription = "Close") }
            Text("Profile", style = MaterialTheme.typography.headlineSmall)
        }

        Box(Modifier.fillMaxSize()) {
            when (val s = state) {
                ProfileUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is ProfileUiState.Error -> Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(s.message, color = MaterialTheme.colorScheme.error)
                    Button(vm::load) { Text("Retry") }
                }
                is ProfileUiState.Success -> ProfileContent(
                    s.profile, onLogout, uploading, uploadError,
                    onChangePhoto = {
                        pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                )
            }
        }
    }
}

@Composable
private fun ProfileContent(
    p: ProfileInfo,
    onLogout: () -> Unit,
    uploading: Boolean,
    uploadError: String?,
    onChangePhoto: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        //profile picture (or initials if there isn't one) + name and email
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(88.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                if (p.photoUrl != null) {
                    //downloads and shows the picture from its link, cropped to fill the circle
                    AsyncImage(
                        model = p.photoUrl,
                        contentDescription = "Profile picture",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    //default:show the user's initials
                    Text(
                        p.username.initials(),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                if (uploading) CircularProgressIndicator()
            }
            TextButton(onChangePhoto, enabled = !uploading) {
                Text(if (uploading) "Uploading…" else "Change photo")
            }
            uploadError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(4.dp))
            Text(p.username, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(p.email, style = MaterialTheme.typography.bodyMedium)
            p.joinedAtMillis?.let {
                Text("Joined ${it.longDate()}", style = MaterialTheme.typography.bodySmall)
            }
        }

        ProfileSection("Household") {
            if (p.householdName != null) {
                Text(p.householdName, style = MaterialTheme.typography.titleMedium)
                Text("${p.memberCount} member(s)", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text("Couldn't load your household", style = MaterialTheme.typography.bodyMedium)
            }
        }

        ProfileSection("Settings") {
            Text(
                "Notifications, appearance and account options are coming soon.",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        //data usage from app
        ProfileSection("Privacy") {
            Text(
                "Coming soon",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        OutlinedButton(onLogout, Modifier.fillMaxWidth()) { Text("Log out") }
    }
}

//a titled white card for each section
@Composable
private fun ProfileSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

//"Chris Mary" -> "CM", "chris" -> "C"
private fun String.initials(): String =
    trim().split(" ").filter { it.isNotBlank() }.take(2)
        .joinToString("") { it.first().uppercase() }
        .ifEmpty { "?" }

//millis -> "Nov 3, 2026"
private fun Long.longDate(): String =
    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(this))
