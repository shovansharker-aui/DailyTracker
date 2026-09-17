package com.dailytracker.app.miniapps.businesscard

import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File

@Composable
fun BusinessCardCaptureScreen(
    viewModel: BusinessCardViewModel,
    onExtracted: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isExtracting by viewModel.isExtracting.collectAsStateWithLifecycle()
    val extractionError by viewModel.extractionError.collectAsStateWithLifecycle()

    var pendingPhotoFile by remember { mutableStateOf<File?>(null) }
    var pendingPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var hasLaunchedOnce by remember { mutableStateOf(false) }
    var photoCaptured by remember { mutableStateOf(false) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            photoCaptured = true
            pendingPhotoFile?.let { viewModel.onImageCaptured(it.absolutePath) }
        } else if (!isExtracting) {
            onCancel()
        }
    }

    fun launchCamera() {
        val file = File(context.cacheDir, "card_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        pendingPhotoFile = file
        pendingPhotoUri = uri
        cameraLauncher.launch(uri)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) launchCamera() else onCancel() }

    LaunchedEffect(Unit) {
        if (!hasLaunchedOnce) {
            hasLaunchedOnce = true
            val hasPermission = ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
            if (hasPermission) launchCamera() else cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(isExtracting, extractionError, photoCaptured) {
        if (photoCaptured && !isExtracting && extractionError == null) {
            onExtracted()
        }
    }

    Scaffold(modifier = modifier.fillMaxSize()) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when {
                isExtracting -> {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Reading the card with Gemini...")
                }
                extractionError != null -> {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(extractionError ?: "", color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { pendingPhotoUri?.let { cameraLauncher.launch(it) } ?: launchCamera() }) {
                        Text("Retake photo")
                    }
                    TextButton(onClick = onCancel) { Text("Cancel") }
                }
                else -> {
                    CircularProgressIndicator()
                }
            }
        }
    }
}
