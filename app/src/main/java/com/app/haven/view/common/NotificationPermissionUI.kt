package com.app.haven.view.common

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.app.haven.notifications.NotificationPrefs
import com.app.haven.notifications.notificationsEnabled
import com.app.haven.notifications.openNotificationSettings

//shows the system "Allow notifications?" popup the first time dashboard opens
@Composable
fun AskForNotificationsOnce() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(Unit) {
        if (!notificationsEnabled(context) && !NotificationPrefs.askedOnHome(context)) {
            NotificationPrefs.markAskedOnHome(context)
            NotificationPrefs.markRequested(context)
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
fun NotificationsCard() {
    val context = LocalContext.current
    val activity = LocalActivity.current
    var enabled by remember { mutableStateOf(notificationsEnabled(context)) }

    LifecycleResumeEffect(Unit) {
        enabled = notificationsEnabled(context)
        onPauseOrDispose { }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        enabled = notificationsEnabled(context)
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (enabled) {
                Text("Notifications are on", style = MaterialTheme.typography.titleMedium)
                Text(
                    "You'll hear when a roommate adds an expense you're part of!",
                    style = MaterialTheme.typography.bodyMedium
                )
                //only the phone's settings can turn them off again
                OutlinedButton(onClick = { openNotificationSettings(context) }) { Text("Notification settings") }
                return@Column
            }
            Text("Notifications are off", style = MaterialTheme.typography.titleMedium)
            Text(
                "Turn them on to hear when a roommate adds an expense you're part of.",
                style = MaterialTheme.typography.bodyMedium
            )
            Button(onClick = {
                //Android only allows popups for permissions twice on devices
                if (canShowPermissionDialog(context, activity)) {
                    NotificationPrefs.markRequested(context)
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    openNotificationSettings(context)
                }
            }) { Text("Turn on notifications") }
        }
    }
}

private fun canShowPermissionDialog(context: android.content.Context, activity: android.app.Activity?): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false   //no dialog before Android 13
    val permission = Manifest.permission.POST_NOTIFICATIONS
    if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) return false
    return !NotificationPrefs.requestedBefore(context) ||
        (activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, permission))
}
