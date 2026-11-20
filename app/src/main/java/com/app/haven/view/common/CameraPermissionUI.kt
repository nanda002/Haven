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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

//shows whether camera permissions are granted with a button to turn them on
@Composable
fun CameraCard() {
    val context = LocalContext.current
    val activity = LocalActivity.current
    var enabled by remember { mutableStateOf(cameraEnabled(context)) }
    var blocked by remember { mutableStateOf(false) }

    //check again whenever the user navigates to the household screen
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
            if (enabled) {
                Text("Camera is on", style = MaterialTheme.typography.titleMedium)
                Text(
                    "You can scan barcodes to add items to the grocery list.",
                    style = MaterialTheme.typography.bodyMedium
                )
                //only the phone's Settings can turn them off again
                OutlinedButton(onClick = { openAppSettings(context) }) { Text("Camera settings") }
                return@Column
            }
            Text("Camera is off", style = MaterialTheme.typography.titleMedium)
            Text(
                if (blocked) "Camera access is blocked. Turn it on in Settings to scan barcodes."
                else "Turn it on to scan barcodes and add items to the grocery list.",
                style = MaterialTheme.typography.bodyMedium
            )
            Button(onClick = {
                if (blocked) openAppSettings(context)
                else launcher.launch(Manifest.permission.CAMERA)
            }) { Text(if (blocked) "Open settings" else "Turn on camera") }
        }
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
