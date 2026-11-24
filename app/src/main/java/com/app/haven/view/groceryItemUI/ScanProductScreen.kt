package com.app.haven.view.groceryItemUI

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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.app.haven.view.common.BarcodeScanner
import com.app.haven.view.common.PageHeader

@Composable
fun ScanProductScreen(
    onScanned: (String) -> Unit,
    onClose: () -> Unit
) {
    //context is used for checking system services
    //in this case, the camera settings
    val context = LocalContext.current
    //required for showing permissions dialog to user
    val activity = LocalActivity.current
    var hasCamera by remember { mutableStateOf(cameraGranted(context)) }
    var blocked by remember { mutableStateOf(false) }
    var asked by rememberSaveable { mutableStateOf(false) }

    //setting up camera permission request
    //this function registers a request with android and returns a launcher
    val requestCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        //user's results - allow/dont allow
        hasCamera = granted
        blocked = !granted && (activity == null ||
                //android blocks the permission dialog shown to the user
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA))
    }

    LaunchedEffect(Unit) {
        if (!hasCamera && !asked) {
            asked = true
            //ask for permission
            requestCamera.launch(Manifest.permission.CAMERA)
        }
    }

    LifecycleResumeEffect(Unit) {
        hasCamera = cameraGranted(context)
        onPauseOrDispose { }
    }

    Column(Modifier
        .fillMaxSize()
        .padding(16.dp)) {
        PageHeader(title = "Scan a barcode", onBack = onClose)

        if (hasCamera) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
            ) {
                BarcodeScanner(
                    formats = emptyList(),
                    onScanned = onScanned,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Text(
                "Point the camera at a product's barcode",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            )
        } else {
            CameraPermissionMessage(
                blocked = blocked,
                onAllow = { requestCamera.launch(Manifest.permission.CAMERA) },
                onOpenSettings = { openAppSettings(context) },
                onTypeInstead = onClose,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun CameraPermissionMessage(
    blocked: Boolean,
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit,
    onTypeInstead: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Camera needed to scan barcodes", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            if (blocked) "Camera access is off for Haven. You can turn it on in Settings."
            else "Haven uses the camera only to read product barcodes. No pictures are saved.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        if (blocked) {
            Button(onClick = onOpenSettings) { Text("Open settings") }
        } else {
            Button(onClick = onAllow) { Text("Allow camera") }
        }
        OutlinedButton(onClick = onTypeInstead) { Text("Type it instead") }
    }
}

//checking if user has granted permission to use the camera or not
private fun cameraGranted(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private fun openAppSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}
