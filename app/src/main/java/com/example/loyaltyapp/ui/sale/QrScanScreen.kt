package com.example.loyaltyapp.ui.sale

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.loyaltyapp.core.scan.QrCodeAnalyzer
import com.example.loyaltyapp.ui.components.GwCard
import com.example.loyaltyapp.ui.components.ScanResultOverlay
import com.example.loyaltyapp.ui.components.ScanResultUi
import com.example.loyaltyapp.ui.components.SecondaryButton
import com.example.loyaltyapp.ui.theme.ColorText
import com.example.loyaltyapp.ui.theme.ColorTextSecondary

/**
 * Scans a customer's QR code (per the handover doc, the code encodes the customer's own id as
 * plain text) as an alternate to phone-number search. Requests CAMERA at runtime; declines
 * gracefully to a message + "Use phone number instead" rather than blocking the sale flow.
 */
@Composable
fun QrScanScreen(
    scanResult: ScanResultUi?,
    onCodeScanned: (String) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column {
            Text("Scan customer QR code", style = androidx.compose.material3.MaterialTheme.typography.headlineMedium)
            Text(
                "Point the camera at the QR code on the customer's profile",
                color = ColorTextSecondary,
                fontSize = 13.sp
            )
        }

        if (scanResult != null) {
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                ScanResultOverlay(scanResult)
            }
        } else if (hasCameraPermission) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .background(ColorText, RoundedCornerShape(16.dp))
            ) {
                QrCameraPreview(onCodeScanned = onCodeScanned)
            }
        } else {
            GwCard {
                Text("Camera permission needed", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    "Grant camera access to scan a QR code, or use the phone number instead.",
                    color = ColorTextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        SecondaryButton(text = "Use phone number instead", onClick = onCancel)
    }
}

@Composable
private fun QrCameraPreview(onCodeScanned: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val onCodeScannedState = rememberUpdatedState(onCodeScanned)

    DisposableEffect(Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        cameraProviderFuture.addListener({
            provider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(1280, 960))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(ContextCompat.getMainExecutor(context), QrCodeAnalyzer { code -> onCodeScannedState.value(code) }) }
            runCatching {
                provider?.unbindAll()
                provider?.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose { provider?.unbindAll() }
    }

    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}
