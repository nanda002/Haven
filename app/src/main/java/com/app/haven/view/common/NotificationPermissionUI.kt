package com.app.haven.view.common

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.app.haven.HavenApplication
import kotlinx.coroutines.launch
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
    val pushTokens = remember { (context.applicationContext as HavenApplication).container.pushTokenRepository }
    val scope = rememberCoroutineScope()
    var permissionOn by remember { mutableStateOf(notificationsEnabled(context)) }
    var pushOn by remember { mutableStateOf(NotificationPrefs.pushOn(context)) }
    val enabled = permissionOn && pushOn

    fun turnOn() {
        NotificationPrefs.setPushOn(context, true)
        pushOn = true
        scope.launch { pushTokens.saveToken() }
    }

    fun turnOff() {
        NotificationPrefs.setPushOn(context, false)
        pushOn = false
        scope.launch { pushTokens.removeToken() }
    }

    LifecycleResumeEffect(Unit) {
        permissionOn = notificationsEnabled(context)
        pushOn = NotificationPrefs.pushOn(context)
        onPauseOrDispose { }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionOn = notificationsEnabled(context)
        if (granted) turnOn()
    }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Notifications", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            Column(Modifier.weight(1f)) {
                Text(if (enabled) "On" else "Off", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (enabled) "You'll hear when a roommate adds an expense you're part of!"
                    else "Turn them on to hear when a roommate adds an expense you're part of.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = { wantOn ->
                    when {
                        !wantOn -> turnOff()
                        permissionOn -> turnOn()
                        //Android only allows popups for permissions twice on devices
                        canShowPermissionDialog(context, activity) -> {
                            NotificationPrefs.markRequested(context)
                            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        else -> openNotificationSettings(context)
                    }
                },
                modifier = Modifier.semantics { contentDescription = "Notifications" }
            )
            }
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
