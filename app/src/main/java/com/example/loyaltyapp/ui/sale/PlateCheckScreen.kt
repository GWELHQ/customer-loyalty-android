package com.example.loyaltyapp.ui.sale

import android.Manifest
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.loyaltyapp.ui.components.GwCard
import com.example.loyaltyapp.ui.components.PrimaryButton
import com.example.loyaltyapp.ui.components.SecondaryButton
import com.example.loyaltyapp.ui.theme.ColorPrimary
import com.example.loyaltyapp.ui.theme.ColorText
import com.example.loyaltyapp.ui.theme.ColorTextSecondary
import com.example.loyaltyapp.ui.theme.GwTheme
import java.io.File

/**
 * Vehicle-plate photo verification (handover doc §3) — shown after a customer is selected and
 * before amount entry. A captured photo's result (matched/mismatch/undetected) is shown for
 * attendant awareness, with a "Retake" option (testing/QA aid — lets staff re-shoot a photo the
 * OCR clearly misread rather than living with it); neither a mismatch nor a failed detection
 * ever blocks the sale once a photo has been submitted. Capturing a photo itself is currently
 * mandatory — [onSkip] is wired through from [SaleFlowViewModel.skipPlateCheck] but not offered
 * in this screen's UI.
 */
@Composable
fun PlateCheckScreen(
    state: SaleUiState,
    onCapture: (File) -> Unit,
    onSkip: () -> Unit,
    onRetry: () -> Unit,
    onContinue: () -> Unit
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

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    Column(
        modifier = Modifier.fillMaxWidth().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column {
            Text("Vehicle plate photo", style = androidx.compose.material3.MaterialTheme.typography.headlineMedium)
            Text(
                "Step 2 of 4 · Take vehicle plate photo",
                color = ColorTextSecondary,
                fontSize = 13.sp
            )
        }

        val result = state.plateCheck
        when {
            state.isSubmittingPlateCheck -> GwCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Box(modifier = Modifier.padding(start = 10.dp))
                    Text("Checking plate…", fontSize = 14.sp)
                }
            }
            result != null -> {
                GwCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (result.matched) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                            contentDescription = null,
                            tint = if (result.matched) GwTheme.extended.success else GwTheme.extended.warning,
                            modifier = Modifier.size(20.dp)
                        )
                        Box(modifier = Modifier.padding(start = 10.dp))
                        Column {
                            Text(
                                if (result.matched) "Plate matches" else "Plate not confirmed",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                "System read: " + (result.detectedPlateNumber ?: "nothing detected in the photo"),
                                color = ColorTextSecondary,
                                fontSize = 13.sp
                            )
                            // Testing aid: a mismatch is expected, not a bug, when the customer
                            // simply has no plate on file yet — this makes that visible instead
                            // of leaving "not confirmed" looking like a bad OCR read.
                            Text(
                                "On file: " + (state.customer?.licensePlateNumbers
                                    ?.takeIf { it.isNotEmpty() }
                                    ?.joinToString(", ")
                                    ?: "none on file for this customer"),
                                color = ColorTextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton(text = "Retake photo", onClick = onRetry, modifier = Modifier.weight(1f))
                    PrimaryButton(text = "Continue", onClick = onContinue, modifier = Modifier.weight(1f))
                }
            }
            state.plateCheckFailed -> {
                GwCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = GwTheme.extended.warning, modifier = Modifier.size(20.dp))
                        Box(modifier = Modifier.padding(start = 10.dp))
                        Column {
                            Text("Couldn't check this photo", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                "The office couldn't be reached, or rejected the request. This never blocks the sale.",
                                color = ColorTextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton(text = "Retake photo", onClick = onRetry, modifier = Modifier.weight(1f))
                    PrimaryButton(text = "Continue", onClick = onContinue, modifier = Modifier.weight(1f))
                }
            }
            hasCameraPermission -> {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(3f / 4f)
                            .background(ColorText, RoundedCornerShape(16.dp))
                    ) {
                        PlateCameraPreview(
                            modifier = Modifier.fillMaxSize(),
                            onImageCaptureReady = { capture -> imageCapture = capture }
                        )
                    }
                    // Shutter sits below the viewfinder, in easy thumb reach at the bottom of the
                    // screen, rather than floating over the live preview.
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        ShutterButton(
                            onClick = {
                                val capture = imageCapture ?: return@ShutterButton
                                capturePlatePhoto(context, capture, onCapture)
                            }
                        )
                    }
                }
            }
            else -> GwCard {
                Text("Camera permission needed", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    "Grant camera access to take a plate photo.",
                    color = ColorTextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

private fun capturePlatePhoto(context: android.content.Context, capture: ImageCapture, onCapture: (File) -> Unit) {
    val file = File(context.cacheDir, "plate_${System.currentTimeMillis()}.jpg")
    val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()
    capture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                // Bakes the EXIF rotation into the pixel data itself, off the main thread (a
                // 4032x3024 decode/rotate/re-encode is too slow to do inline with the UI). See
                // [normalizeOrientation] for why this matters beyond just cosmetics.
                Thread {
                    normalizeOrientation(file)
                    android.os.Handler(android.os.Looper.getMainLooper()).post { onCapture(file) }
                }.start()
            }
            override fun onError(exception: ImageCaptureException) {
                // Nothing to recover here — the attendant can just tap the shutter again, or Skip.
            }
        }
    )
}

/**
 * CameraX saves a portrait capture with an EXIF orientation *hint* rather than physically
 * rotated pixels — correct for viewers that honor EXIF (Photos, Quick Look, browsers), but the
 * backend's OCR preprocessing does not: a photo that displayed perfectly upright and legible
 * everywhere else still came back with `detectedPlateNumber: null` every time, because the raw
 * pixel grid it actually decodes has the plate text running vertically. Re-encoding here with the
 * rotation baked in and the tag reset to NORMAL makes the file itself unambiguous regardless of
 * whether any given downstream consumer respects EXIF.
 */
private fun normalizeOrientation(file: File) {
    val exif = androidx.exifinterface.media.ExifInterface(file.absolutePath)
    val degrees = when (exif.getAttributeInt(
        androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
        androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
    )) {
        androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
    if (degrees == 0f) return

    val original = android.graphics.BitmapFactory.decodeFile(file.absolutePath) ?: return
    val matrix = android.graphics.Matrix().apply { postRotate(degrees) }
    val rotated = android.graphics.Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
    java.io.FileOutputStream(file).use { out -> rotated.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, out) }
    if (rotated !== original) original.recycle()
    rotated.recycle()
}

@Composable
private fun ShutterButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .background(Color.White, CircleShape)
            .padding(4.dp)
            .background(ColorPrimary, CircleShape)
            .clickable(onClick = onClick)
    )
}

@Composable
private fun PlateCameraPreview(modifier: Modifier = Modifier, onImageCaptureReady: (ImageCapture) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }

    DisposableEffect(Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        cameraProviderFuture.addListener({
            provider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val capture = ImageCapture.Builder().build()
            runCatching {
                provider?.unbindAll()
                provider?.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                onImageCaptureReady(capture)
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose { provider?.unbindAll() }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}
