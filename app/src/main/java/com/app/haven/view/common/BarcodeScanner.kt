//* AI-assisted code
package com.app.haven.view.common

import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import kotlinx.coroutines.awaitCancellation

@Composable
fun BarcodeScanner (
    formats: List<Int>,
    onScanned: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    //using lifecycle here because we want the camera's lifecycle
    //tied to the screen and not be used in the background
    //if the user goes to another screen
    val lifecycleOwner = LocalLifecycleOwner.current
    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }
    val latestOnScanned by rememberUpdatedState(onScanned)

    LaunchedEffect(lifecycleOwner, formats) {
        //ProcessCameraProvider gets the camera
        val provider = ProcessCameraProvider.awaitInstance(context)

        //building the live picture with surface + cameraXViewfinder
        val preview = Preview.Builder().build().apply {
            setSurfaceProvider { request -> surfaceRequest = request }
        }

        //there are different types of formats for barcodes, the formats are pre fed
        //from ml kit's barcode package
        val wanted = formats.ifEmpty { listOf(Barcode.FORMAT_ALL_FORMATS) }
        //configuring ML Kit's barcode scanner
        val scanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                //wanted.first() is only used to fit the function config
                .setBarcodeFormats(wanted.first(), *wanted.drop(1).toIntArray())
                .build()
        )

        //executer is required to decide which thread to run the ML Kit analyzing work on
        val executor = ContextCompat.getMainExecutor(context)
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        var found = false
        //frames from the camera are passed over to the analyzer
        analysis.setAnalyzer(
            executor,
            MlKitAnalyzer(listOf(scanner), ImageAnalysis.COORDINATE_SYSTEM_ORIGINAL, executor) { result ->
                //retreiving the barcode value
                val code = result.getValue(scanner)?.firstOrNull()?.rawValue
                if (code != null && !found) {
                    found = true
                    latestOnScanned(code)
                }
            }
        )

        try {
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            awaitCancellation()
        } finally {
            //cleanup when launched effect is canceled
            analysis.clearAnalyzer()
            //release the camera
            provider.unbind(preview, analysis)
            //close the ml kit
            scanner.close()
        }
    }
    surfaceRequest?.let { request ->
        CameraXViewfinder(surfaceRequest = request, modifier = modifier)
    }
}
