package com.app.haven.view.common

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.app.haven.notifications.NotificationPrefs
import com.app.haven.notifications.openNotificationSettings
import kotlinx.coroutines.launch

//shows whether camera permissions are granted with a button to turn them on
@Composable
fun CameraCard() {
    val context = LocalContext.current
    val activity = LocalActivity.current
    var enabled by remember { mutableStateOf(cameraEnabled(context)) }
    //usually Android shows the permission dialog twice before blocking the permission
    //In this case, the user has to go to the settings to switch it back on
    var blocked by remember { mutableStateOf(false) }

    //check again whenever the user navigates to the household details screen
    LifecycleResumeEffect(Unit) {
        enabled = cameraEnabled(context)
        onPauseOrDispose { }
    }

    //whenever the user clicks on the settings, launcher is activated to show android's dialog (when permissions are currently not granted)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        enabled = granted
        blocked = !granted && activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (enabled) "Camera is on" else "Camera is off",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                when {
                    enabled -> "You can scan barcodes to add items to the grocery list"
                    blocked -> "Camera access is blocked. Turn it on in settings to scan barcodes"
                    else -> "Turn it on to scan barcodes and add items to the grocery list."
                },
                style = MaterialTheme.typography.bodyMedium
            )
        }
        //adding a switch instead of a button, similar to notifications
        Switch(
            checked = enabled,
            onCheckedChange = { wantOn ->
                when {
                    //turning it off
                    !wantOn -> openAppSettings(context)
                    //turning it on but Android has blocked the dialog
                    blocked -> openAppSettings(context)
                    //turning it on and shows the dialog
                    else -> launcher.launch(Manifest.permission.CAMERA)
                }
                },
                modifier = Modifier.semantics { contentDescription = "Camera" }
            )
        }
}

fun cameraEnabled(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

//directs the user to android's settings if the permissions are currently on
private fun openAppSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}
