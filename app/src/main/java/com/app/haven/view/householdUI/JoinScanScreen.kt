package com.app.haven.view.householdUI

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.app.haven.view.common.BarcodeScanner
import com.app.haven.view.common.cameraEnabled
import com.app.haven.view.common.inviteCodeFromScan
import com.app.haven.view.groceryItemUI.CameraPermissionMessage
import com.google.mlkit.vision.barcode.common.Barcode

// Opens the camera straight away to scan a household QR code. "Enter code manually" is always at the bottom.
@Composable
fun JoinScanScreen(
    onCode: (String) -> Unit,
    onEnterManually: () -> Unit,
    joining: Boolean = false,
    error: String? = null
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    var hasCamera by remember { mutableStateOf(cameraEnabled(context)) }
    var blocked by remember { mutableStateOf(false) }
    var asked by rememberSaveable { mutableStateOf(false) }
    // bumped after a bad scan so the scanner starts again (it stops after its first result)
    var attempt by remember { mutableIntStateOf(0) }
    var notInvite by remember { mutableStateOf(false) }

    val requestCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCamera = granted
        blocked = !granted && (activity == null ||
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA))
    }

    LaunchedEffect(Unit) {
        if (!hasCamera && !asked) {
            asked = true
            requestCamera.launch(Manifest.permission.CAMERA)
        }
    }
    LifecycleResumeEffect(Unit) {
        hasCamera = cameraEnabled(context)
        onPauseOrDispose { }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Join a household", style = MaterialTheme.typography.headlineSmall)

        if (hasCamera) {
            Box(
                Modifier.fillMaxWidth().weight(1f).padding(top = 16.dp).clip(RoundedCornerShape(16.dp))
            ) {
                key(attempt) {
                    BarcodeScanner(
                        formats = listOf(Barcode.FORMAT_QR_CODE),
                        onScanned = { raw ->
                            val code = inviteCodeFromScan(raw)
                            if (code == null) {
                                notInvite = true
                                attempt++
                            } else {
                                notInvite = false
                                onCode(code)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Text(
                when {
                    joining -> "Joining..."
                    error != null -> error
                    notInvite -> "That isn't a Haven invite QR code. Try again."
                    else -> "Point the camera at your household's QR code"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = if (error != null || notInvite) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
            )
            OutlinedButton(onClick = onEnterManually, Modifier.fillMaxWidth()) { Text("Enter code manually") }
        } else {
            CameraPermissionMessage(
                blocked = blocked,
                onAllow = { requestCamera.launch(Manifest.permission.CAMERA) },
                onOpenSettings = { openSettings(context) },
                onTypeInstead = onEnterManually,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private fun openSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}